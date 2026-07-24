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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val maxOriginMovementMeters: Float = MAX_ORIGIN_MOVEMENT_METERS,
    private val maxLocationAccuracyMeters: Float = MAX_LOCATION_ACCURACY_METERS,
    private val distanceBetween: (Double, Double, Double, Double) -> Float? =
        WalkingRouteUtils::straightLineDistanceMeters
) {
    private data class CacheEntry(
        val originLatitude: Double,
        val originLongitude: Double,
        val destinationLatitude: Double,
        val destinationLongitude: Double,
        val route: WalkingRouteInfo,
        val expiresAtMillis: Long
    )

    private val requestMutex = Mutex()
    private val cache = mutableMapOf<String, CacheEntry>()

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
                Candidate(alert.uniqueId, latitude, longitude, directDistance)
            }
            .sortedBy(Candidate::directDistanceMeters)
            .take(MAX_DESTINATIONS)

        if (candidates.isEmpty()) return emptyMap()
        return withTimeoutOrNull(timeoutMillis) {
            requestMutex.withLock {
                val current = nowMillis()
                val routes = mutableMapOf<String, WalkingRouteInfo>()
                val missing = mutableListOf<Candidate>()
                candidates.forEach { candidate ->
                    val cached = cache[candidate.id]
                    if (cached != null && cached.isReusable(origin, candidate, current)) {
                        routes[candidate.id] = cached.route
                    } else {
                        cache.remove(candidate.id)
                        missing += candidate
                    }
                }

                if (missing.isNotEmpty()) {
                    val response = try {
                        service.getWalkingRoutes(
                            WalkingRouteRequest(
                                origin = WalkingRouteCoordinates(origin.latitude, origin.longitude),
                                destinations = missing.map { candidate ->
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

                    response?.routes.orEmpty().forEach { result ->
                        val candidate = missing.firstOrNull { it.id == result.id } ?: return@forEach
                        val distance = result.distanceMeters
                        val duration = result.durationSeconds
                        if (
                            result.status == STATUS_OK &&
                            distance != null && distance >= 0 &&
                            duration != null && duration >= 0
                        ) {
                            val route = WalkingRouteInfo(distance, duration)
                            routes[candidate.id] = route
                            cache[candidate.id] = CacheEntry(
                                originLatitude = origin.latitude,
                                originLongitude = origin.longitude,
                                destinationLatitude = candidate.latitude,
                                destinationLongitude = candidate.longitude,
                                route = route,
                                expiresAtMillis = current + cacheTtlMillis
                            )
                        }
                    }
                }
                routes
            }
        } ?: emptyMap()
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
        private const val MAX_ORIGIN_MOVEMENT_METERS = 75f
        private const val MAX_LOCATION_ACCURACY_METERS = 100f
        private const val MAX_DESTINATIONS = 50
        private const val STATUS_OK = "OK"

        @Volatile
        private var instance: WalkingRouteRepository? = null

        fun getInstance(): WalkingRouteRepository =
            instance ?: synchronized(this) {
                instance ?: WalkingRouteRepository(PokemonAlertsApi.service).also { instance = it }
            }
    }
}
