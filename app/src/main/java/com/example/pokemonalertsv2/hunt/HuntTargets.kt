package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.AlertFilterMatcher
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.tracking.isEligibleArrivalDestination
import com.example.pokemonalertsv2.ui.alerts.mapCoordinatesOrNull
import com.example.pokemonalertsv2.ui.alerts.mapPipBrowseOrder
import com.example.pokemonalertsv2.ui.alerts.mapPipDistanceMeters
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.TravelTime
import com.example.pokemonalertsv2.util.WalkingRouteUtils

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
    return huntWalkOrder(matches, originLatitude, originLongitude, nowMillis)
}

/**
 * The order to walk a hunt's matches in.
 *
 * Two rules, in this order:
 *
 * 1. **Reachable first.** Nearest-first alone will happily send you to the alert
 *    that ends before you get there -- the same thing the alert card warns about
 *    with "Ends before you arrive" -- while a target you could actually reach
 *    waits behind it. Anything that cannot be reached in time drops to the back
 *    rather than being hidden: an estimate is not a fact, and you may be cycling.
 * 2. **Then a chain, not a fan.** Repeatedly taking the nearest remaining target
 *    from where you are *standing* makes you cross the same square three times
 *    when a cluster is spread around you. Taking the nearest from where you will
 *    *be* -- the previous target -- walks the cluster instead.
 */
internal fun huntWalkOrder(
    alerts: List<PokemonAlert>,
    originLatitude: Double,
    originLongitude: Double,
    nowMillis: Long = System.currentTimeMillis()
): List<PokemonAlert> {
    val (reachable, tooLate) = mapPipBrowseOrder(alerts, originLatitude, originLongitude)
        .partition { canArriveBeforeItEnds(it, originLatitude, originLongitude, nowMillis) }
    return chainNearest(reachable, originLatitude, originLongitude) + tooLate
}

/**
 * Whether an estimated walk gets you there before the alert ends.
 *
 * Estimated, never routed: this runs on every location fix over every match, and
 * routing hundreds of alerts would be both slow and rate-limited. The estimate is
 * the same one the cards fall back to -- straight line, detour factor, average
 * walking speed. No end time means nothing to miss, so the answer is yes.
 */
internal fun canArriveBeforeItEnds(
    alert: PokemonAlert,
    originLatitude: Double,
    originLongitude: Double,
    nowMillis: Long = System.currentTimeMillis()
): Boolean {
    val coordinates = alert.mapCoordinatesOrNull() ?: return true
    val endMillis = TimeUtils.parseEndTimeToMillis(alert.endTime) ?: return true
    val distance = mapPipDistanceMeters(
        originLatitude,
        originLongitude,
        coordinates.latitude,
        coordinates.longitude
    ).toFloat()
    val estimate = WalkingRouteUtils.estimateWalkingRouteInfo(distance) ?: return true
    return !TravelTime.expiresBeforeArrival(
        walkingDurationSeconds = estimate.durationSeconds,
        remainingMillis = endMillis - nowMillis
    )
}

/**
 * Nearest-neighbour from the trainer, then from each target in turn.
 *
 * Only the nearest [HUNT_CHAIN_POOL] are chained. Beyond that the order is a
 * plan for a walk nobody is taking yet, and the cost is quadratic in a list that
 * a broad hunt can push past eight hundred.
 */
private fun chainNearest(
    ordered: List<PokemonAlert>,
    originLatitude: Double,
    originLongitude: Double
): List<PokemonAlert> {
    if (ordered.size < 3) return ordered
    val pool = ordered.take(HUNT_CHAIN_POOL).toMutableList()
    val tail = ordered.drop(HUNT_CHAIN_POOL)
    val chain = ArrayList<PokemonAlert>(pool.size)
    var fromLatitude = originLatitude
    var fromLongitude = originLongitude
    while (pool.isNotEmpty()) {
        val nextIndex = pool.indices.minBy { index ->
            val coordinates = pool[index].mapCoordinatesOrNull()
                ?: return@minBy Double.MAX_VALUE
            mapPipDistanceMeters(
                fromLatitude,
                fromLongitude,
                coordinates.latitude,
                coordinates.longitude
            )
        }
        val next = pool.removeAt(nextIndex)
        chain += next
        next.mapCoordinatesOrNull()?.let { coordinates ->
            fromLatitude = coordinates.latitude
            fromLongitude = coordinates.longitude
        }
    }
    return chain + tail
}

/** Enough to cover the cluster you are standing in, cheap enough to redo often. */
private const val HUNT_CHAIN_POOL = 30

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
