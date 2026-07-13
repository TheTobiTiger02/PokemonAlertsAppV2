package com.example.pokemonalertsv2.util

import com.example.pokemonalertsv2.data.PokemonAlert
import kotlin.math.abs

internal enum class AlertImageSource {
    REMOTE_IMAGE,
    MAP_FALLBACK,
    THUMBNAIL,
    PLACEHOLDER
}

internal data class AlertCoordinates(
    val latitude: Double,
    val longitude: Double
)

internal fun validAlertCoordinates(alert: PokemonAlert): AlertCoordinates? {
    val latitude = alert.latitude ?: return null
    val longitude = alert.longitude ?: return null
    if (!latitude.isFinite() || !longitude.isFinite()) return null
    if (latitude !in -85.0511..85.0511 || longitude !in -180.0..180.0) return null
    if (abs(latitude) < 0.0001 && abs(longitude) < 0.0001) return null
    return AlertCoordinates(latitude, longitude)
}

internal fun orderedAlertImageSources(alert: PokemonAlert): List<AlertImageSource> = buildList {
    if (!alert.imageUrl.isNullOrBlank()) add(AlertImageSource.REMOTE_IMAGE)
    if (validAlertCoordinates(alert) != null) add(AlertImageSource.MAP_FALLBACK)
    if (!alert.thumbnailUrl.isNullOrBlank()) add(AlertImageSource.THUMBNAIL)
    add(AlertImageSource.PLACEHOLDER)
}
