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

/**
 * Whether one alert is something [definition] is hunting, right now.
 *
 * Split out from [huntTargets] because it is also the question asked of a
 * destination restored from storage, where there is no list to rank: the
 * journey store outlives any one hunt, so without this a hunt would happily
 * adopt whatever the last one left behind.
 */
internal fun isHuntTarget(
    alert: PokemonAlert,
    definition: FilterDefinition,
    nowMillis: Long = System.currentTimeMillis()
): Boolean =
    alert.isEligibleArrivalDestination(nowMillis) &&
        AlertFilterMatcher.matches(alert, definition)
