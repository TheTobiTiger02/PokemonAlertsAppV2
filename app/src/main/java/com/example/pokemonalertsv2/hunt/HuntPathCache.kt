package com.example.pokemonalertsv2.hunt

import androidx.annotation.VisibleForTesting
import com.example.pokemonalertsv2.catchroutes.CatchApiException
import com.example.pokemonalertsv2.catchroutes.CatchPoint
import com.example.pokemonalertsv2.catchroutes.CatchRoutesService
import com.example.pokemonalertsv2.catchroutes.catchBody
import com.example.pokemonalertsv2.data.PokemonAlertsApi
import com.example.pokemonalertsv2.data.RouteMatrixPoint
import com.example.pokemonalertsv2.data.RouteMatrixRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Street geometry for the hunt's numbered route, fetched in the background and published as a snapshot.
 *
 * The lock discipline is [HuntRouteMatrixCache]'s: the mutex guards the caches and is never held across a
 * network call, one request is in flight at a time, and every failure leaves the last snapshot alone so the
 * map simply keeps the line it has (or none).
 *
 * What it adds is frugality, because geometry is the more expensive of the two calls and shares one
 * per-client allowance with the matrix at the edge (see [HuntRoutingGate]): legs are cached by position
 * pair, so a plan whose stops are unchanged costs nothing, and when only the trainer has moved the request
 * is the two points from them to the next stop rather than the whole chain.
 */
internal class HuntPathCache @VisibleForTesting internal constructor(
    private val service: CatchRoutesService,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val legTtlMillis: Long = HUNT_LEG_TTL_MILLIS,
    private val minIntervalMillis: Long = MIN_INTERVAL_MILLIS,
    private val requestScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val cacheMutex = Mutex()
    private val legs = mutableMapOf<HuntLegKey, List<CatchPoint>>()
    private val legExpiry = mutableMapOf<HuntLegKey, Long>()
    private var inFlight: CompletableDeferred<Unit>? = null
    private var lastRequestAtMillis: Long? = null
    private var consecutiveFailures = 0
    @Volatile private var anchor: HuntRoutePoint? = null

    private val published = MutableStateFlow<HuntPath>(HuntPath.None)

    /** The path to draw, for readers that can collect. */
    val path: StateFlow<HuntPath> = published.asStateFlow()

    /** The path to draw, for readers that cannot (the floating overlay). */
    fun snapshot(): HuntPath = published.value

    /**
     * Publishes the street path from the trainer through [stops], fetching the legs it is missing.
     *
     * Returns true when the published path covers the whole chain. [force] is the Recalculate button: it
     * drops the legs from the trainer's position and asks again even while backing off.
     */
    suspend fun request(
        originLatitude: Double,
        originLongitude: Double,
        stops: List<HuntRoutePoint>,
        force: Boolean = false
    ): Boolean {
        // The trainer's leg is re-asked only once they have walked past the drift the matrix tolerates too;
        // until then the line keeps starting where they stood, instead of vanishing on every fix.
        val kept = anchor?.takeIf {
            !force && !huntOriginMoved(it.latitude, it.longitude, originLatitude, originLongitude)
        }
        val origin = kept ?: HuntRoutePoint(HuntRouteMatrixCache.HUNT_ORIGIN_ID, originLatitude, originLongitude)
        anchor = origin
        val chain = huntPathChain(origin, stops)
        if (chain.size < 2) {
            cacheMutex.withLock { published.value = HuntPath.None }
            return false
        }

        val span: IntRange
        val own: CompletableDeferred<Unit>
        cacheMutex.withLock {
            val current = nowMillis()
            prune(current)
            if (force) evictOriginLegsLocked(chain.first())
            val missing = huntPathRequestSpan(chain, legs.keys)
            if (missing == null) {
                publishLocked(chain, current)
                return true
            }
            if (!force && (current < HuntRoutingGate.blockedUntilMillis || lastRequestAtMillis.let { it != null && current - it < minIntervalMillis })) {
                publishLocked(chain, current)
                return false
            }
            if (inFlight != null) {
                publishLocked(chain, current)
                return false
            }
            val deferred = CompletableDeferred<Unit>()
            inFlight = deferred
            own = deferred
            span = missing
            lastRequestAtMillis = current
            requestScope.launch { fetchAndPublish(chain, span, deferred) }
        }

        own.await()
        return cacheMutex.withLock { huntPathRequestSpan(chain, legs.keys) == null }
    }

    private suspend fun fetchAndPublish(chain: List<HuntRoutePoint>, span: IntRange, deferred: CompletableDeferred<Unit>) {
        val points = chain.slice(span).mapIndexed { index, point -> RouteMatrixPoint("h$index", point.latitude, point.longitude) }
        var parsed: Map<HuntLegKey, List<CatchPoint>>? = null
        var transportFailure = false
        var retryAtMillis = 0L
        try {
            val response = service.path(RouteMatrixRequest.pedestrian(points)).catchBody()
            parsed = parseHuntPath(response, points)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (busy: CatchApiException) {
            // 429 and friends: the edge told us when to come back, and that answer counts for the matrix too.
            retryAtMillis = busy.retryAtMillis
            transportFailure = retryAtMillis <= 0
        } catch (error: Throwable) {
            transportFailure = true
        }

        try {
            cacheMutex.withLock {
                val current = nowMillis()
                when {
                    parsed != null -> {
                        parsed.forEach { (key, line) ->
                            legs[key] = line
                            legExpiry[key] = current + legTtlMillis
                        }
                        consecutiveFailures = 0
                        HuntRoutingGate.clear()
                        publishLocked(chain, current)
                    }
                    retryAtMillis > 0 -> HuntRoutingGate.blockUntil(retryAtMillis)
                    transportFailure -> {
                        consecutiveFailures++
                        val delay = BASE_BACKOFF_MILLIS shl (consecutiveFailures - 1).coerceAtMost(BACKOFF_SHIFT_CAP)
                        HuntRoutingGate.blockUntil(current + delay.coerceAtMost(MAX_BACKOFF_MILLIS))
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                cacheMutex.withLock { if (inFlight === deferred) inFlight = null }
            }
            deferred.complete(Unit)
        }
    }

    private fun publishLocked(chain: List<HuntRoutePoint>, current: Long) {
        published.value = huntPathFrom(chain, legs, current)
    }

    private fun evictOriginLegsLocked(origin: HuntRoutePoint) {
        val node = huntLegNode(origin.latitude, origin.longitude)
        val touchesOrigin = { key: HuntLegKey -> key.from == node || key.to == node }
        legs.keys.removeAll(touchesOrigin)
        legExpiry.keys.removeAll(touchesOrigin)
    }

    private fun prune(current: Long) {
        legExpiry.entries.removeAll { (key, expiry) ->
            (expiry <= current).also { if (it) legs.remove(key) }
        }
    }

    /** Forgets everything; the hunt ended. */
    internal suspend fun clear() {
        cacheMutex.withLock {
            legs.clear()
            legExpiry.clear()
            inFlight = null
            lastRequestAtMillis = null
            consecutiveFailures = 0
            anchor = null
            published.value = HuntPath.None
        }
        HuntRoutingGate.clear()
    }

    companion object {
        /** One request per this long at most, whatever changed. */
        @VisibleForTesting internal const val MIN_INTERVAL_MILLIS = 15_000L
        @VisibleForTesting internal const val BASE_BACKOFF_MILLIS = 2_000L
        @VisibleForTesting internal const val MAX_BACKOFF_MILLIS = 60_000L
        private const val BACKOFF_SHIFT_CAP = 5

        @Volatile
        private var instance: HuntPathCache? = null

        fun getInstance(): HuntPathCache =
            instance ?: synchronized(this) {
                instance ?: HuntPathCache(PokemonAlertsApi.catchRoutesService).also { instance = it }
            }
    }
}
