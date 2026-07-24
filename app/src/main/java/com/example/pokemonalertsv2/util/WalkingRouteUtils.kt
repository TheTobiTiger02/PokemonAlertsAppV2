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
        routeInfo: WalkingRouteInfo?
    ): RouteDisplayInfo {
        val directDistance = straightLineDistanceMeters
            ?.takeIf { it >= 0f && !it.isNaN() && !it.isInfinite() }
        val validRoute = routeInfo?.takeIf {
            it.distanceMeters >= 0 && it.durationSeconds >= 0
        }
        val source = when {
            validRoute != null -> DistanceSource.ROUTED
            directDistance != null -> DistanceSource.DIRECT
            else -> DistanceSource.UNAVAILABLE
        }
        val routedDistance = validRoute?.distanceMeters?.toFloat()
        val effectiveDistance = routedDistance ?: directDistance
        return RouteDisplayInfo(
            straightLineDistanceMeters = directDistance,
            routedDistanceMeters = routedDistance,
            effectiveDistanceMeters = effectiveDistance,
            walkingDurationSeconds = validRoute?.durationSeconds,
            source = source,
            distanceText = when (source) {
                DistanceSource.ROUTED -> routedDistance?.let(::formatDistanceMeters)
                DistanceSource.DIRECT -> directDistance?.let { "${formatDistanceMeters(it)} direct" }
                DistanceSource.UNAVAILABLE -> null
            },
            walkingText = validRoute?.let { formatWalkingDurationSeconds(it.durationSeconds) }
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
