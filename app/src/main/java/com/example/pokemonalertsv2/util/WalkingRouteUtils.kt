package com.example.pokemonalertsv2.util

import android.location.Location
import com.example.pokemonalertsv2.data.PokemonAlert
import java.util.Locale
import kotlin.math.ceil

data class WalkingRouteInfo(
    val distanceMeters: Int,
    val durationSeconds: Long
)

data class RouteDisplayInfo(
    val straightLineDistanceMeters: Float?,
    val distanceText: String?,
    val walkingText: String?
)

object WalkingRouteUtils {
    private const val WALKING_ROUTE_DISTANCE_FACTOR = 1.25f
    private const val WALKING_SPEED_METERS_PER_SECOND = 1.35f

    suspend fun getWalkingRoutes(
        origin: Location,
        alerts: List<PokemonAlert>
    ): Map<String, WalkingRouteInfo> {
        return buildEstimatedWalkingRoutes(alerts) { latitude, longitude ->
            straightLineDistanceMeters(
                originLatitude = origin.latitude,
                originLongitude = origin.longitude,
                destinationLatitude = latitude,
                destinationLongitude = longitude
            )
        }
    }

    internal fun buildEstimatedWalkingRoutes(
        alerts: List<PokemonAlert>,
        straightLineDistanceMeters: (latitude: Double, longitude: Double) -> Float?
    ): Map<String, WalkingRouteInfo> {
        return alerts.mapNotNull { alert ->
            val latitude = alert.latitude ?: return@mapNotNull null
            val longitude = alert.longitude ?: return@mapNotNull null
            val routeInfo = estimateWalkingRouteInfo(straightLineDistanceMeters(latitude, longitude))
                ?: return@mapNotNull null
            alert.uniqueId to routeInfo
        }.toMap()
    }

    internal fun estimateWalkingRouteInfo(straightLineDistanceMeters: Float?): WalkingRouteInfo? {
        val meters = straightLineDistanceMeters?.takeIf { it >= 0f && !it.isNaN() && !it.isInfinite() }
            ?: return null
        val estimatedDistanceMeters = ceil(meters * WALKING_ROUTE_DISTANCE_FACTOR).toInt()
        val estimatedDurationSeconds = ceil(estimatedDistanceMeters / WALKING_SPEED_METERS_PER_SECOND).toLong()
        return WalkingRouteInfo(
            distanceMeters = estimatedDistanceMeters,
            durationSeconds = estimatedDurationSeconds
        )
    }

    private fun straightLineDistanceMeters(
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
        return RouteDisplayInfo(
            straightLineDistanceMeters = straightLineDistanceMeters,
            distanceText = routeInfo?.let { formatDistanceMeters(it.distanceMeters.toFloat()) }
                ?: straightLineDistanceMeters?.let { formatDistanceMeters(it) },
            walkingText = routeInfo?.let { formatWalkingDurationSeconds(it.durationSeconds) }
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
