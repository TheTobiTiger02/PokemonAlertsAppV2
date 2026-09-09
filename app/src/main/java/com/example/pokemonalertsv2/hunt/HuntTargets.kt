package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.AlertFilterMatcher
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.tracking.isEligibleArrivalDestination
import com.example.pokemonalertsv2.ui.alerts.mapPipBrowseOrder

/**
 * The alerts a hunt is currently offering, nearest first.
 *
 * Dismissed alerts are excluded because "Got it" is the whole point of the
 * loop: the thing you just caught must not be the next thing the cursor lands
 * on. Expiry and coordinate validity are delegated to
 * [isEligibleArrivalDestination] so a hunt can never target something the
 * arrival service would refuse to accept.
 */
internal fun huntTargets(
    alerts: List<PokemonAlert>,
    definition: FilterDefinition,
    dismissedAlertIds: Set<String>,
    originLatitude: Double,
    originLongitude: Double,
    nowMillis: Long = System.currentTimeMillis()
): List<PokemonAlert> {
    val matches = alerts.filter { alert ->
        alert.uniqueId !in dismissedAlertIds &&
            alert.isEligibleArrivalDestination(nowMillis) &&
            AlertFilterMatcher.matches(alert, definition)
    }
    return mapPipBrowseOrder(matches, originLatitude, originLongitude)
}
