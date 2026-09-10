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
    nowMillis: Long = System.currentTimeMillis(),
    costs: HuntLegCosts = HuntLegCosts.None
): List<PokemonAlert> {
    val matches = alerts.filter { alert ->
        alert.uniqueId !in dismissedAlertIds &&
            alert.isEligibleArrivalDestination(nowMillis) &&
            AlertFilterMatcher.matches(alert, definition)
    }
    return huntWalkOrder(matches, originLatitude, originLongitude, nowMillis, costs)
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
 *
 * "Nearest" is a real walked leg wherever [costs] has one, and a straight line
 * everywhere else -- so a target across a river no longer sorts ahead of one on
 * this side of it.
 */
internal fun huntWalkOrder(
    alerts: List<PokemonAlert>,
    originLatitude: Double,
    originLongitude: Double,
    nowMillis: Long = System.currentTimeMillis(),
    costs: HuntLegCosts = HuntLegCosts.None
): List<PokemonAlert> {
    val (reachable, tooLate) = mapPipBrowseOrder(alerts, originLatitude, originLongitude)
        .partition { canArriveBeforeItEnds(it, originLatitude, originLongitude, nowMillis, costs) }
    return chainNearest(reachable, originLatitude, originLongitude, costs) + tooLate
}

/**
 * Whether the walk gets you there before the alert ends.
 *
 * Routed where [costs] has an answer, estimated everywhere else -- straight line,
 * detour factor, average walking speed, the same estimate the cards fall back to.
 * This runs on every location fix over every match, so it never routes anything
 * itself; only the handful of targets the matrix already covers get the real number.
 *
 * A routed answer may only *demote* a target it is clearly sure about. Reachability
 * decides list membership, and a target that flips in and out as the trainer walks
 * is worse than one that is merely optimistic -- hence the margin. No end time means
 * nothing to miss, so the answer is yes.
 */
internal fun canArriveBeforeItEnds(
    alert: PokemonAlert,
    originLatitude: Double,
    originLongitude: Double,
    nowMillis: Long = System.currentTimeMillis(),
    costs: HuntLegCosts = HuntLegCosts.None
): Boolean {
    val coordinates = alert.mapCoordinatesOrNull() ?: return true
    val endMillis = TimeUtils.parseEndTimeToMillis(alert.endTime) ?: return true
    val routedSeconds = costs.walkSecondsFromOriginOrNull(alert.uniqueId)
    val seconds = if (routedSeconds != null) {
        (routedSeconds * HUNT_ROUTED_REACHABILITY_MARGIN).toLong()
    } else {
        val distance = mapPipDistanceMeters(
            originLatitude,
            originLongitude,
            coordinates.latitude,
            coordinates.longitude
        ).toFloat()
        WalkingRouteUtils.estimateWalkingRouteInfo(distance)?.durationSeconds ?: return true
    }
    return !TravelTime.expiresBeforeArrival(
        walkingDurationSeconds = seconds,
        remainingMillis = endMillis - nowMillis
    )
}

/**
 * How far a routed walk has to overrun before it drops a target to the back.
 *
 * A routed walk is longer than the straight-line estimate almost every time, so
 * without a margin the first matrix of a hunt would demote a row of targets at once.
 * At 0.9 routing only demotes what it overruns by more than about a ninth.
 */
private const val HUNT_ROUTED_REACHABILITY_MARGIN = 0.9

/**
 * Nearest-neighbour from the trainer, then from each target in turn.
 *
 * Only the nearest [HUNT_CHAIN_POOL] are chained. Beyond that the order is a
 * plan for a walk nobody is taking yet, and the cost is quadratic in a list that
 * a broad hunt can push past eight hundred.
 *
 * Legs come from [costs] where it has them. The straight-line fallback is scaled by
 * [WalkingRouteUtils.DETOUR_FACTOR] so both are in walked metres: a routed leg runs
 * 1.1 to 1.26 times its straight line, so comparing a raw one against a routed one
 * would quietly favour whichever target happened not to be routed. Being one
 * positive factor applied to every fallback, it leaves the order of a wholly
 * unrouted list exactly as it was.
 */
private fun chainNearest(
    ordered: List<PokemonAlert>,
    originLatitude: Double,
    originLongitude: Double,
    costs: HuntLegCosts
): List<PokemonAlert> {
    // Two targets used to be worth short-circuiting: sorted by straight line from
    // the trainer and chained greedily from the trainer are the same two orders.
    // With routed legs they are not -- the nearer one as the crow flies can be the
    // longer walk -- so only a single target skips the chain now.
    if (ordered.size < 2) return ordered
    val pool = ordered.take(HUNT_CHAIN_POOL).toMutableList()
    val tail = ordered.drop(HUNT_CHAIN_POOL)
    val chain = ArrayList<PokemonAlert>(pool.size)
    var fromId: String? = null
    var fromLatitude = originLatitude
    var fromLongitude = originLongitude
    while (pool.isNotEmpty()) {
        val nextIndex = pool.indices.minBy { index ->
            val alert = pool[index]
            val coordinates = alert.mapCoordinatesOrNull()
                ?: return@minBy Double.MAX_VALUE
            costs.walkedMetersOrNull(fromId, alert.uniqueId)
                ?: (mapPipDistanceMeters(
                    fromLatitude,
                    fromLongitude,
                    coordinates.latitude,
                    coordinates.longitude
                ) * WalkingRouteUtils.DETOUR_FACTOR)
        }
        val next = pool.removeAt(nextIndex)
        chain += next
        next.mapCoordinatesOrNull()?.let { coordinates ->
            fromId = next.uniqueId
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
