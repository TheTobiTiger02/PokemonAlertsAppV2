package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.alerts.AlertMapCoordinates
import com.example.pokemonalertsv2.ui.alerts.HUNT_ORDINAL_MAX
import com.example.pokemonalertsv2.ui.alerts.mapCoordinatesOrNull

internal enum class HuntMapFocus {
    READY, TARGET, ROUTE;
    fun pressed(): HuntMapFocus = if (this == TARGET) ROUTE else TARGET
}

/** Use the same ordered head that receives numbered marker badges. */
internal fun huntRouteFocusCoordinates(
    targets: List<PokemonAlert>,
    userLatitude: Double?,
    userLongitude: Double?
): List<AlertMapCoordinates> = buildList {
    addAll(targets.take(HUNT_ORDINAL_MAX).mapNotNull { it.mapCoordinatesOrNull() })
    if (userLatitude != null && userLongitude != null &&
        userLatitude.isFinite() && userLongitude.isFinite() &&
        userLatitude in -90.0..90.0 && userLongitude in -180.0..180.0
    ) add(AlertMapCoordinates(userLatitude, userLongitude))
}.distinct()
