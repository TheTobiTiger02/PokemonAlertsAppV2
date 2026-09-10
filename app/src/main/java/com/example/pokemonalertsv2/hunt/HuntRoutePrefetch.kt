package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.AlertFilterMatcher
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.tracking.isEligibleArrivalDestination
import com.example.pokemonalertsv2.ui.alerts.mapCoordinatesOrNull
import com.example.pokemonalertsv2.ui.alerts.mapPipBrowseOrder
import com.example.pokemonalertsv2.ui.alerts.mapPipDistanceMeters

/**
 * The targets worth routing, chosen without ever consulting a routed cost.
 *
 * **Straight-line only, deliberately, and it must stay that way.** If this list were
 * ranked by the published costs, a new snapshot would reorder the targets, which
 * would change the list, which would trigger another prefetch -- a loop with a
 * network call in it. Ranking by straight line is a fixed point: the same alerts in,
 * the same ids out, routed or not.
 *
 * The reachability partition is deliberately not applied either. A target the
 * estimate calls unreachable is exactly the one where a real answer is worth most,
 * and it is still on the list the trainer can see.
 */
internal fun huntRoutingCandidates(
    alerts: List<PokemonAlert>,
    definition: FilterDefinition,
    dismissedAlertIds: Set<String>,
    originLatitude: Double,
    originLongitude: Double,
    nowMillis: Long = System.currentTimeMillis(),
    limit: Int = HUNT_MATRIX_MAX_TARGETS
): List<HuntRoutePoint> {
    val matches = alerts.filter { alert ->
        alert.uniqueId !in dismissedAlertIds &&
            alert.isEligibleArrivalDestination(nowMillis) &&
            AlertFilterMatcher.matches(alert, definition)
    }
    return mapPipBrowseOrder(matches, originLatitude, originLongitude)
        .asSequence()
        .mapNotNull { alert ->
            alert.mapCoordinatesOrNull()?.let { coordinates ->
                HuntRoutePoint(alert.uniqueId, coordinates.latitude, coordinates.longitude)
            }
        }
        // Two alerts on one PokeStop are one point to the router, and the endpoint
        // rejects a duplicate id outright.
        .distinctBy { it.id }
        .take(limit)
        .toList()
}

/** True once the trainer has walked far enough for the origin row to be worth re-asking. */
internal fun huntOriginMoved(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double,
    thresholdMeters: Double = HUNT_ORIGIN_DRIFT_METERS
): Boolean =
    mapPipDistanceMeters(fromLatitude, fromLongitude, toLatitude, toLongitude) > thresholdMeters
