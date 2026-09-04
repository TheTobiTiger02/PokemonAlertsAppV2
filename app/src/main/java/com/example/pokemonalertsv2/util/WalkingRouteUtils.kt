package com.example.pokemonalertsv2.util

import android.location.Location
import java.util.Locale
import kotlin.math.ceil

data class WalkingRouteInfo(
    val distanceMeters: Int,
    val durationSeconds: Long
)

enum class DistanceSource {
    ROUTED,
    ESTIMATED,
    DIRECT,
    UNAVAILABLE
}

data class RouteDisplayInfo(
    val straightLineDistanceMeters: Float?,
    val routedDistanceMeters: Float?,
    val effectiveDistanceMeters: Float?,
    val walkingDurationSeconds: Long?,
    val source: DistanceSource,
    val distanceText: String?,
    val walkingText: String?
)

object WalkingRouteUtils {
    // Measured against 400 routed alerts from the Valhalla backend, taking the
    // median ratio of routed distance to straight-line distance:
    //
    //   500 m - 2 km   1.26
    //   over 8 km      1.09
    //   overall        1.12
    //
    // The estimate now only applies to alerts ranked past MAX_DESTINATIONS,
    // which are the farthest ones, so this is tuned toward the long end. The
    // old 1.25 overstated a 14.3 km walk by 2.4 km and 39 minutes. Note the
    // ratio is much higher under ~500 m -- a 25 m straight line can route 250 m
    // around a building -- but those alerts sort first and are always routed.
    const val DETOUR_FACTOR = 1.10f

    // Median observed speed was 1.36 m/s across every distance band, against
    // the 1.3 assumed here before.
    const val AVERAGE_WALKING_SPEED_MPS = 1.36f

    fun estimateWalkingRouteInfo(straightLineDistanceMeters: Float?): WalkingRouteInfo? {
        val distance = straightLineDistanceMeters
            ?.takeIf { it >= 0f && !it.isNaN() && !it.isInfinite() }
            ?: return null
        val estimatedDistance = Math.round(distance * DETOUR_FACTOR)
        val estimatedDuration = ceil(estimatedDistance / AVERAGE_WALKING_SPEED_MPS.toDouble()).toLong().coerceAtLeast(60L)
        return WalkingRouteInfo(
            distanceMeters = estimatedDistance,
            durationSeconds = estimatedDuration
        )
    }

    fun straightLineDistanceMeters(
        originLatitude: Double,
        originLongitude: Double,
        destinationLatitude: Double,
        destinationLongitude: Double
    ): Float? {
        val results = FloatArray(1)
        runCatching {
            Location.distanceBetween(
                originLatitude,
                originLongitude,
                destinationLatitude,
                destinationLongitude,
                results
            )
        }.getOrNull() ?: return null
        return results.getOrNull(0)?.takeIf { it >= 0f && !it.isNaN() && !it.isInfinite() }
    }

    fun buildRouteDisplayInfo(
        straightLineDistanceMeters: Float?,
        routeInfo: WalkingRouteInfo?,
        fallbackToEstimate: Boolean = true
    ): RouteDisplayInfo {
        val directDistance = straightLineDistanceMeters
            ?.takeIf { it >= 0f && !it.isNaN() && !it.isInfinite() }
        val validRoute = routeInfo?.takeIf {
            it.distanceMeters >= 0 && it.durationSeconds >= 0
        }
        val estimatedRoute = if (validRoute == null && fallbackToEstimate && directDistance != null) {
            estimateWalkingRouteInfo(directDistance)
        } else null

        val source = when {
            validRoute != null -> DistanceSource.ROUTED
            estimatedRoute != null -> DistanceSource.ESTIMATED
            directDistance != null -> DistanceSource.DIRECT
            else -> DistanceSource.UNAVAILABLE
        }
        val routedDistance = validRoute?.distanceMeters?.toFloat()
        val effectiveDistance = routedDistance
            ?: estimatedRoute?.distanceMeters?.toFloat()
            ?: directDistance
        val walkingDuration = validRoute?.durationSeconds ?: estimatedRoute?.durationSeconds

        return RouteDisplayInfo(
            straightLineDistanceMeters = directDistance,
            routedDistanceMeters = routedDistance,
            effectiveDistanceMeters = effectiveDistance,
            walkingDurationSeconds = walkingDuration,
            source = source,
            distanceText = when (source) {
                DistanceSource.ROUTED -> routedDistance?.let(::formatDistanceMeters)
                DistanceSource.ESTIMATED -> effectiveDistance?.let { "~${formatDistanceMeters(it)}" }
                DistanceSource.DIRECT -> directDistance?.let { "${formatDistanceMeters(it)} direct" }
                DistanceSource.UNAVAILABLE -> null
            },
            walkingText = when (source) {
                DistanceSource.ROUTED -> validRoute?.let { formatWalkingDurationSeconds(it.durationSeconds) }
                DistanceSource.ESTIMATED -> estimatedRoute?.let { "~${formatWalkingDurationSeconds(it.durationSeconds)}" }
                else -> null
            }
        )
    }

    fun formatDistanceMeters(meters: Float): String {
        return if (meters >= 1000f) {
            String.format(Locale.getDefault(), "%.1f km", meters / 1000f)
        } else {
            String.format(Locale.getDefault(), "%.0f m", meters)
        }
    }

    fun formatWalkingDurationSeconds(seconds: Long): String {
        val minutes = ceil(seconds / 60.0).toInt().coerceAtLeast(1)
        return String.format(Locale.getDefault(), "%d min walk", minutes)
    }
}
