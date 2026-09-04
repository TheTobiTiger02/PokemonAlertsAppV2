package com.example.pokemonalertsv2.util

import android.location.Location
import androidx.annotation.VisibleForTesting
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonAlertsApi
import com.example.pokemonalertsv2.data.PokemonAlertsService
import com.example.pokemonalertsv2.data.WalkingRouteCoordinates
import com.example.pokemonalertsv2.data.WalkingRouteDestination
import com.example.pokemonalertsv2.data.WalkingRouteRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@VisibleForTesting
internal data class WalkingRouteOrigin(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float
)

class WalkingRouteRepository @VisibleForTesting internal constructor(
    private val service: PokemonAlertsService,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val cacheTtlMillis: Long = CACHE_TTL_MILLIS,
    private val negativeCacheTtlMillis: Long = NEGATIVE_TTL_MILLIS,
    private val maxOriginMovementMeters: Float = MAX_ORIGIN_MOVEMENT_METERS,
    private val maxRoutedStraightLineMeters: Float = MAX_ROUTED_STRAIGHT_LINE_METERS,
    private val maxLocationAccuracyMeters: Float = MAX_LOCATION_ACCURACY_METERS,
    private val distanceBetween: (Double, Double, Double, Double) -> Float? =
        WalkingRouteUtils::straightLineDistanceMeters,
    // Requests run here rather than in the caller's scope so one caller's timeout
    // cannot cancel a batch other callers are already waiting on.
    private val requestScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private data class CacheEntry(
        val originLatitude: Double,
        val originLongitude: Double,
        val destinationLatitude: Double,
        val destinationLongitude: Double,
        val route: WalkingRouteInfo,
        val expiresAtMillis: Long
    )

    // Guards `cache`, `negativeCache` and `inFlight` only -- never held across a network
    // call, so a one-marker lookup no longer queues behind a 500-destination prefetch.
    private val cacheMutex = Mutex()
    private val cache = mutableMapOf<String, CacheEntry>()

    // Destination id -> expiry. The server can answer OK while saying a specific
    // destination has no pedestrian route; nothing lands in `cache` then, and without
    // this the arrival tracker re-asks on every GPS fix for the whole walk.
    private val negativeCache = mutableMapOf<String, Long>()

    private val inFlight = mutableMapOf<String, CompletableDeferred<WalkingRouteInfo?>>()

    // A failed request caches nothing, so without this the next refresh finds an empty
    // cache and asks again immediately -- one 429 turns into a request storm. Both
    // fields are only read and written under `cacheMutex`.
    private var backoffUntilMillis = 0L
    private var consecutiveFailures = 0

    suspend fun getWalkingRoutes(
        origin: Location,
        alerts: List<PokemonAlert>,
        timeoutMillis: Long = FOREGROUND_TIMEOUT_MILLIS
    ): Map<String, WalkingRouteInfo> {
        if (!origin.hasAccuracy()) return emptyMap()
        return getWalkingRoutes(
            origin = WalkingRouteOrigin(origin.latitude, origin.longitude, origin.accuracy),
            alerts = alerts,
            timeoutMillis = timeoutMillis
        )
    }

    @VisibleForTesting
    internal suspend fun getWalkingRoutes(
        origin: WalkingRouteOrigin,
        alerts: List<PokemonAlert>,
        timeoutMillis: Long = FOREGROUND_TIMEOUT_MILLIS
    ): Map<String, WalkingRouteInfo> {
        if (origin.accuracyMeters > maxLocationAccuracyMeters) return emptyMap()

        val candidates = alerts
            .mapNotNull { alert ->
                val latitude = alert.latitude?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return@mapNotNull null
                val longitude = alert.longitude?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return@mapNotNull null
                if (latitude == 0.0 && longitude == 0.0) return@mapNotNull null
                val directDistance = distanceBetween(
                    origin.latitude,
                    origin.longitude,
                    latitude,
                    longitude
                ) ?: return@mapNotNull null
                if (directDistance > maxRoutedStraightLineMeters) return@mapNotNull null
                Candidate(alert.uniqueId, latitude, longitude, directDistance)
            }
            .sortedBy(Candidate::directDistanceMeters)
            .take(MAX_DESTINATIONS)

        if (candidates.isEmpty()) return emptyMap()

        val routes = mutableMapOf<String, WalkingRouteInfo>()
        val owned = mutableListOf<Candidate>()
        val ownedDeferreds = mutableMapOf<String, CompletableDeferred<WalkingRouteInfo?>>()
        val pending = mutableMapOf<String, Deferred<WalkingRouteInfo?>>()

        cacheMutex.withLock {
            val current = nowMillis()
            // While backing off, serve whatever is cached and start nothing new. Callers
            // fall back to the straight-line estimate exactly as they do on any miss.
            val backingOff = current < backoffUntilMillis
            candidates.forEach { candidate ->
                val cached = cache[candidate.id]
                if (cached != null && cached.isReusable(origin, candidate, current)) {
                    routes[candidate.id] = cached.route
                    return@forEach
                }
                cache.remove(candidate.id)
                val negativeUntil = negativeCache[candidate.id]
                if (negativeUntil != null) {
                    if (negativeUntil > current) return@forEach
                    negativeCache.remove(candidate.id)
                }
                val alreadyRequested = inFlight[candidate.id]
                if (alreadyRequested != null) {
                    // Another caller is already asking for this destination; wait on
                    // its answer instead of issuing a duplicate request.
                    pending[candidate.id] = alreadyRequested
                } else if (!backingOff) {
                    val deferred = CompletableDeferred<WalkingRouteInfo?>()
                    inFlight[candidate.id] = deferred
                    ownedDeferreds[candidate.id] = deferred
                    owned += candidate
                }
            }
        }

        if (owned.isNotEmpty()) {
            requestScope.launch { fetchAndPublish(origin, owned, ownedDeferreds) }
        }

        pending += ownedDeferreds
        if (pending.isEmpty()) return routes

        // The timeout now bounds only the wait for an answer, not lock contention.
        val fetched = withTimeoutOrNull(timeoutMillis) {
            pending.mapValues { (_, deferred) -> deferred.await() }
        }.orEmpty()
        fetched.forEach { (id, route) -> if (route != null) routes[id] = route }
        return routes
    }

    private suspend fun fetchAndPublish(
        origin: WalkingRouteOrigin,
        owned: List<Candidate>,
        deferreds: Map<String, CompletableDeferred<WalkingRouteInfo?>>
    ) {
        val resolved = mutableMapOf<String, WalkingRouteInfo>()
        try {
            val response = try {
                service.getWalkingRoutes(
                    WalkingRouteRequest(
                        origin = WalkingRouteCoordinates(origin.latitude, origin.longitude),
                        destinations = owned.map { candidate ->
                            WalkingRouteDestination(
                                id = candidate.id,
                                latitude = candidate.latitude,
                                longitude = candidate.longitude
                            )
                        }
                    )
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Throwable) {
                null
            }

            val current = nowMillis()
            // Indexed once: a linear scan per result is 125k string compares at 500.
            val ownedById = owned.associateBy(Candidate::id)
            response?.routes.orEmpty().forEach { result ->
                val candidate = ownedById[result.id] ?: return@forEach
                val distance = result.distanceMeters
                val duration = result.durationSeconds
                if (
                    result.status == STATUS_OK &&
                    distance != null && distance >= 0 &&
                    duration != null && duration >= 0
                ) {
                    resolved[candidate.id] = WalkingRouteInfo(distance, duration)
                }
            }

            // One lock acquisition for the whole batch rather than one per result.
            cacheMutex.withLock {
                resolved.forEach { (id, route) ->
                    val candidate = ownedById.getValue(id)
                    cache[id] = CacheEntry(
                        originLatitude = origin.latitude,
                        originLongitude = origin.longitude,
                        destinationLatitude = candidate.latitude,
                        destinationLongitude = candidate.longitude,
                        route = route,
                        expiresAtMillis = current + cacheTtlMillis
                    )
                    negativeCache.remove(id)
                }
                // A successful response that still lacks a destination means unroutable,
                // not offline -- remember it briefly so the next fix does not re-ask.
                if (response != null) {
                    val negativeUntil = current + negativeCacheTtlMillis
                    ownedById.keys.forEach { id ->
                        if (id !in resolved) negativeCache[id] = negativeUntil
                    }
                }
                if (response == null) {
                    consecutiveFailures++
                    val delay = BASE_BACKOFF_MILLIS shl (consecutiveFailures - 1).coerceAtMost(BACKOFF_SHIFT_CAP)
                    backoffUntilMillis = current + delay.coerceAtMost(MAX_BACKOFF_MILLIS)
                } else {
                    consecutiveFailures = 0
                    backoffUntilMillis = 0L
                }
            }
        } finally {
            // Every owned destination must be answered -- with null if the request
            // failed or was cancelled -- or its waiters hang until their timeout.
            withContext(NonCancellable) {
                cacheMutex.withLock {
                    deferreds.forEach { (id, deferred) ->
                        if (inFlight[id] === deferred) inFlight.remove(id)
                    }
                }
            }
            deferreds.forEach { (id, deferred) -> deferred.complete(resolved[id]) }
        }
    }

    private fun CacheEntry.isReusable(
        origin: WalkingRouteOrigin,
        candidate: Candidate,
        currentMillis: Long
    ): Boolean {
        if (expiresAtMillis <= currentMillis) return false
        if (destinationLatitude != candidate.latitude || destinationLongitude != candidate.longitude) return false
        val movement = distanceBetween(
            originLatitude,
            originLongitude,
            origin.latitude,
            origin.longitude
        ) ?: return false
        return movement <= maxOriginMovementMeters
    }

    @VisibleForTesting
    internal fun clearCache() {
        cache.clear()
        negativeCache.clear()
        inFlight.clear()
        backoffUntilMillis = 0L
        consecutiveFailures = 0
    }

    private data class Candidate(
        val id: String,
        val latitude: Double,
        val longitude: Double,
        val directDistanceMeters: Float
    )

    companion object {
        const val FOREGROUND_TIMEOUT_MILLIS = 6_000L
        const val BACKGROUND_TIMEOUT_MILLIS = 2_500L
        private const val CACHE_TTL_MILLIS = 10 * 60 * 1000L

        // Short by design: unroutability is often positional ("across the river"), so a
        // walker who keeps approaching deserves periodic re-answers, just slower than
        // one per GPS fix.
        private const val NEGATIVE_TTL_MILLIS = 90 * 1000L
        private const val MAX_ORIGIN_MOVEMENT_METERS = 75f

        // Farthest straight-line distance still worth a real route: the largest travel-time
        // preset is a 60-minute walk -- 60*60*AVERAGE_WALKING_SPEED_MPS of routed distance,
        // over DETOUR_FACTOR as the crow flies (~4.5 km). Farther alerts can never produce
        // a walking time worth acting on, so they keep the "~" straight-line estimate
        // instead of riding along in every batch and re-downloading every 75 m of movement.
        private val MAX_ROUTED_STRAIGHT_LINE_METERS: Float =
            60f * 60f * WalkingRouteUtils.AVERAGE_WALKING_SPEED_MPS / WalkingRouteUtils.DETOUR_FACTOR

        private const val MAX_LOCATION_ACCURACY_METERS = 100f

        // Alerts past this rank never get a real route and fall back to the
        // straight-line estimate, which the UI marks with a leading "~". At 50
        // that was almost every Darmstadt alert, since they sort last.
        //
        // 500 is the server's ceiling in both senses: routingMaxDestinations
        // caps a request at 500, and the endpoint parses at most a 64 KB body,
        // which 500 destinations fill to roughly 40 KB. Measured server-side,
        // 500 costs ~305 ms cold and ~10 ms once cached, against ~154 ms for
        // 50 -- ten times the destinations for twice the time.
        private const val MAX_DESTINATIONS = 500
        private const val STATUS_OK = "OK"

        // Doubling from 2 s to a 60 s ceiling. Cloudflare sits in front of the routing
        // endpoint and answers 429 on a burst, so a failure has to suppress the next
        // attempt rather than let every refresh retry into the limit.
        @VisibleForTesting
        internal const val BASE_BACKOFF_MILLIS = 2_000L
        @VisibleForTesting
        internal const val MAX_BACKOFF_MILLIS = 60_000L
        private const val BACKOFF_SHIFT_CAP = 5

        @Volatile
        private var instance: WalkingRouteRepository? = null

        fun getInstance(): WalkingRouteRepository =
            instance ?: synchronized(this) {
                instance ?: WalkingRouteRepository(PokemonAlertsApi.service).also { instance = it }
            }
    }
}
