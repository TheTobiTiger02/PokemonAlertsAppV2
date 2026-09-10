package com.example.pokemonalertsv2.hunt

import androidx.annotation.VisibleForTesting
import com.example.pokemonalertsv2.data.PokemonAlertsApi
import com.example.pokemonalertsv2.data.PokemonAlertsService
import com.example.pokemonalertsv2.data.RouteMatrixPoint
import com.example.pokemonalertsv2.data.RouteMatrixRequest
import com.example.pokemonalertsv2.data.RouteMatrixResponse
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
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Walked legs for the hunt, fetched in the background and published as a snapshot.
 *
 * Modelled on [com.example.pokemonalertsv2.util.WalkingRouteRepository], down to the
 * lock discipline and the backoff curve, because the two solve the same problem
 * against the same host: the mutex guards the caches and is never held across a
 * network call, and the request runs on [requestScope] so one caller's timeout
 * cannot cancel a batch another caller is waiting on.
 *
 * What it does *not* share is the key. Walking routes are keyed by destination id
 * from one moving origin; legs here are keyed by an ordered pair of positions,
 * because most of a matrix is target-to-target and those legs are the same walk
 * however far the trainer has moved since.
 */
internal class HuntRouteMatrixCache @VisibleForTesting internal constructor(
    private val service: PokemonAlertsService,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val legTtlMillis: Long = HUNT_LEG_TTL_MILLIS,
    private val negativeTtlMillis: Long = NEGATIVE_TTL_MILLIS,
    private val maxTargets: Int = HUNT_MATRIX_MAX_TARGETS,
    private val requestScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val cacheMutex = Mutex()
    private val legs = mutableMapOf<HuntLegKey, HuntLeg>()
    private val legExpiry = mutableMapOf<HuntLegKey, Long>()

    /** Pairs the server said have no walking route. Re-asked, but slowly. */
    private val negativeCache = mutableMapOf<HuntLegKey, Long>()

    private var inFlight: CompletableDeferred<Unit>? = null

    private var backoffUntilMillis = 0L
    private var consecutiveFailures = 0

    private val published = MutableStateFlow<HuntLegCosts>(HuntLegCosts.None)

    /** The latest snapshot, for readers that can collect. */
    val costs: StateFlow<HuntLegCosts> = published.asStateFlow()

    /** The latest snapshot, for main-thread readers that cannot. */
    fun snapshot(): HuntLegCosts = published.value

    /**
     * Route the trainer's row and the block between [targets], then publish.
     *
     * Returns true when a snapshot was published. Never throws: every failure leaves
     * the last snapshot in place, which is what makes the hunt degrade to straight
     * lines instead of breaking.
     */
    suspend fun prefetch(
        originLatitude: Double,
        originLongitude: Double,
        targets: List<HuntRoutePoint>,
        timeoutMillis: Long = PREFETCH_TIMEOUT_MILLIS
    ): Boolean {
        val origin = HuntRoutePoint(HUNT_ORIGIN_ID, originLatitude, originLongitude)
        val points = buildPoints(origin, targets)
        if (points.size < 2) return false

        val wait: CompletableDeferred<Unit>?
        val own: CompletableDeferred<Unit>?

        cacheMutex.withLock {
            val current = nowMillis()
            prune(current)
            if (isFullyResolved(points, current)) {
                publishLocked(points, current)
                return true
            }
            if (current < backoffUntilMillis) {
                publishLocked(points, current)
                return false
            }
            val running = inFlight
            if (running != null) {
                // A different point set has to wait its turn rather than open a second
                // request: both endpoints share one per-client allowance at the edge.
                wait = running
                own = null
            } else {
                val deferred = CompletableDeferred<Unit>()
                inFlight = deferred
                wait = null
                own = deferred
                requestScope.launch { fetchAndPublish(points, deferred) }
            }
        }

        if (wait != null) {
            withTimeoutOrNull(timeoutMillis) { wait.await() }
            // The batch that just finished may already cover this one.
            cacheMutex.withLock {
                val current = nowMillis()
                if (isFullyResolved(points, current)) {
                    publishLocked(points, current)
                    return true
                }
            }
            return false
        }

        withTimeoutOrNull(timeoutMillis) { own?.await() }
        // Answers "are these points routed now", not "has anything ever been routed".
        // A timeout leaves the request running on requestScope; it publishes when it
        // lands, and the caller simply orders on straight lines until then.
        return cacheMutex.withLock { isFullyResolved(points, nowMillis()) }
    }

    private fun buildPoints(origin: HuntRoutePoint, targets: List<HuntRoutePoint>): List<HuntRoutePoint> {
        val seen = mutableSetOf(origin.id)
        val trimmed = targets.asSequence()
            .filter { it.id != HUNT_ORIGIN_ID && seen.add(it.id) }
            .filter { it.latitude.isFinite() && it.longitude.isFinite() }
            .filter { it.latitude != 0.0 || it.longitude != 0.0 }
            .take(maxTargets)
            .toList()
        return listOf(origin) + trimmed
    }

    private fun isFullyResolved(points: List<HuntRoutePoint>, current: Long): Boolean =
        pairsOf(points).all { key ->
            legExpiry[key]?.let { it > current } == true || negativeCache[key]?.let { it > current } == true
        }

    private fun pairsOf(points: List<HuntRoutePoint>): List<HuntLegKey> {
        val nodes = points.map { huntLegNode(it.latitude, it.longitude) }
        val keys = ArrayList<HuntLegKey>(nodes.size * (nodes.size - 1))
        for (i in nodes.indices) for (j in nodes.indices) {
            if (nodes[i] != nodes[j]) keys += HuntLegKey(nodes[i], nodes[j])
        }
        return keys
    }

    private fun prune(current: Long) {
        legExpiry.entries.removeAll { (key, expiry) ->
            (expiry <= current).also { if (it) legs.remove(key) }
        }
        negativeCache.entries.removeAll { it.value <= current }
    }

    private fun publishLocked(points: List<HuntRoutePoint>, current: Long) {
        val origin = points.first()
        val nodes = points.drop(1).associate { it.id to huntLegNode(it.latitude, it.longitude) }
        val wanted = pairsOf(points).toSet()
        val frozen = HashMap<HuntLegKey, HuntLeg>(wanted.size)
        var earliestExpiry = Long.MAX_VALUE
        for (key in wanted) {
            val leg = legs[key] ?: continue
            val expiry = legExpiry[key] ?: continue
            if (expiry <= current) continue
            frozen[key] = leg
            if (expiry < earliestExpiry) earliestExpiry = expiry
        }
        if (frozen.isEmpty()) return
        published.value = ResolvedHuntLegCosts(
            legs = frozen,
            nodes = nodes,
            originNode = huntLegNode(origin.latitude, origin.longitude),
            originLatitude = origin.latitude,
            originLongitude = origin.longitude,
            calculatedAtMillis = current,
            expiresAtMillis = if (earliestExpiry == Long.MAX_VALUE) current else earliestExpiry
        )
    }

    private suspend fun fetchAndPublish(points: List<HuntRoutePoint>, deferred: CompletableDeferred<Unit>) {
        var response: RouteMatrixResponse? = null
        var transportFailure = false
        try {
            response = service.getRouteMatrix(
                RouteMatrixRequest.pedestrian(
                    points.map { RouteMatrixPoint(it.id, it.latitude, it.longitude) }
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            // Not logged, matching WalkingRouteRepository: a hunt out of signal would
            // otherwise write a line every refresh, and the outcome is visible anyway
            // as the ordering falling back to straight lines.
            transportFailure = true
        }

        try {
            val parsed = response?.let { parseMatrix(it, points) }
            cacheMutex.withLock {
                val current = nowMillis()
                when {
                    parsed != null -> {
                        parsed.legs.forEach { (key, leg) ->
                            legs[key] = leg
                            legExpiry[key] = current + legTtlMillis
                            negativeCache.remove(key)
                        }
                        parsed.unreachable.forEach { key ->
                            legs.remove(key)
                            legExpiry.remove(key)
                            negativeCache[key] = current + negativeTtlMillis
                        }
                        consecutiveFailures = 0
                        backoffUntilMillis = 0L
                        publishLocked(points, current)
                    }
                    // A malformed answer is a contract bug, not an outage: retrying it
                    // sooner will not help, and backing off would hide the next good one.
                    transportFailure -> {
                        consecutiveFailures++
                        val delay = BASE_BACKOFF_MILLIS shl (consecutiveFailures - 1).coerceAtMost(BACKOFF_SHIFT_CAP)
                        backoffUntilMillis = current + delay.coerceAtMost(MAX_BACKOFF_MILLIS)
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                cacheMutex.withLock {
                    if (inFlight === deferred) inFlight = null
                }
            }
            deferred.complete(Unit)
        }
    }

    private class ParsedMatrix(val legs: Map<HuntLegKey, HuntLeg>, val unreachable: Set<HuntLegKey>)

    /**
     * Reads the response, mapping ids **by name**.
     *
     * Never by position: the server is free to answer in any order, and a positional
     * read that happened to be wrong would send the trainer to a different Pokemon
     * than the one the list is pointing at. A response that does not describe the
     * points that were asked about is discarded whole.
     */
    private fun parseMatrix(response: RouteMatrixResponse, points: List<HuntRoutePoint>): ParsedMatrix? {
        val size = points.size
        if (response.ids.size != size) return null
        if (response.distanceMeters.size != size || response.durationSeconds.size != size) return null
        if (response.distanceMeters.any { it.size != size } || response.durationSeconds.any { it.size != size }) return null

        val byId = points.associateBy { it.id }
        val nodes = response.ids.map { id -> byId[id]?.let { huntLegNode(it.latitude, it.longitude) } ?: return null }
        if (nodes.toSet().size != response.ids.toSet().size) return null

        val resolved = HashMap<HuntLegKey, HuntLeg>(size * size)
        val unreachable = HashSet<HuntLegKey>()
        for (i in 0 until size) for (j in 0 until size) {
            if (nodes[i] == nodes[j]) continue
            val key = HuntLegKey(nodes[i], nodes[j])
            val distance = response.distanceMeters[i][j]
            val duration = response.durationSeconds[i][j]
            if (distance == null || duration == null || distance < 0 || duration < 0) {
                unreachable += key
            } else {
                resolved[key] = HuntLeg(distance, duration)
            }
        }
        return ParsedMatrix(resolved, unreachable)
    }

    @VisibleForTesting
    internal suspend fun clear() {
        cacheMutex.withLock {
            legs.clear()
            legExpiry.clear()
            negativeCache.clear()
            inFlight = null
            backoffUntilMillis = 0L
            consecutiveFailures = 0
            published.value = HuntLegCosts.None
        }
    }

    companion object {
        /** The id the trainer's own position travels under. Alerts are "server-<n>". */
        internal const val HUNT_ORIGIN_ID = "__origin"

        /** Longer than the map's 2.5 s background budget: this never blocks a frame. */
        const val PREFETCH_TIMEOUT_MILLIS = 4_000L

        private const val NEGATIVE_TTL_MILLIS = 90 * 1000L

        @VisibleForTesting internal const val BASE_BACKOFF_MILLIS = 2_000L
        @VisibleForTesting internal const val MAX_BACKOFF_MILLIS = 60_000L
        private const val BACKOFF_SHIFT_CAP = 5

        @Volatile
        private var instance: HuntRouteMatrixCache? = null

        fun getInstance(): HuntRouteMatrixCache =
            instance ?: synchronized(this) {
                instance ?: HuntRouteMatrixCache(PokemonAlertsApi.service).also { instance = it }
            }
    }
}
