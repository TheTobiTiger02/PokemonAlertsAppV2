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
 *
 * 3. **Then the plan is checked against itself.** [improveChain] takes the crossings
 *    out of the greedy chain, and [chainReachability] asks the question the first
 *    rule cannot: not "can I reach this from here" but "can I still reach this after
 *    walking to everything ahead of it". Both need real legs and are skipped without
 *    them.
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
    val chained = chainNearest(reachable, originLatitude, originLongitude, costs)
    // 2-opt first: reachability judges the order you will actually walk, and this is
    // what changes it.
    val improved = improveChain(chained, originLatitude, originLongitude, costs)
    val (keeping, missing) = chainReachability(improved, originLatitude, originLongitude, nowMillis, costs)
    return keeping + missing + tooLate
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
 * Takes the crossings out of a greedy chain.
 *
 * Nearest-neighbour is quick and usually leaves one obvious blemish: a target
 * skipped early because something nearer was in the other direction, then walked
 * back for at the end. 2-opt reverses a stretch of the order whenever doing so makes
 * the whole walk shorter, which is exactly the move that removes a crossing.
 *
 * **Scored on the whole tour, not the two cut edges.** The usual 2-opt shortcut --
 * compare the two edges you cut against the two you add -- assumes the cost of
 * walking a stretch backwards equals the cost of walking it forwards. Routed legs are
 * directional (one-way pavements, stairs, crossings on one side only), so it does not
 * hold here, and the shortcut would happily accept a move that is worse. At a pool of
 * [HUNT_CHAIN_POOL] the honest version is a few thousand map lookups.
 *
 * Skipped entirely when nothing in the chain is routed: a straight-line estimate is
 * not accurate enough to justify rearranging a walk, and skipping keeps an unrouted
 * hunt ordered exactly as it was before any of this existed.
 */
private fun improveChain(
    ordered: List<PokemonAlert>,
    originLatitude: Double,
    originLongitude: Double,
    costs: HuntLegCosts
): List<PokemonAlert> {
    if (ordered.size < 4) return ordered
    val pool = ordered.take(HUNT_CHAIN_POOL)
    val tail = ordered.drop(HUNT_CHAIN_POOL)
    if (!anyRoutedLeg(pool, costs)) return ordered

    var best = pool
    var bestCost = tourCost(best, originLatitude, originLongitude, costs)
    repeat(HUNT_TWO_OPT_MAX_SWEEPS) {
        var improvedThisSweep = false
        for (i in 0 until best.size - 1) {
            for (j in i + 1 until best.size) {
                val candidate = best.toMutableList().apply {
                    subList(i, j + 1).reverse()
                }
                val candidateCost = tourCost(candidate, originLatitude, originLongitude, costs)
                if (candidateCost < bestCost) {
                    best = candidate
                    bestCost = candidateCost
                    improvedThisSweep = true
                }
            }
        }
        if (!improvedThisSweep) return best + tail
    }
    return best + tail
}

/** Walked metres for the whole order, trainer to first target and on down the list. */
private fun tourCost(
    order: List<PokemonAlert>,
    originLatitude: Double,
    originLongitude: Double,
    costs: HuntLegCosts
): Double {
    var total = 0.0
    var fromId: String? = null
    var fromLatitude = originLatitude
    var fromLongitude = originLongitude
    for (alert in order) {
        val coordinates = alert.mapCoordinatesOrNull() ?: continue
        total += legMeters(fromId, fromLatitude, fromLongitude, alert, costs)
        fromId = alert.uniqueId
        fromLatitude = coordinates.latitude
        fromLongitude = coordinates.longitude
    }
    return total
}

/** One leg in walked metres: routed where known, scaled straight line where not. */
private fun legMeters(
    fromId: String?,
    fromLatitude: Double,
    fromLongitude: Double,
    to: PokemonAlert,
    costs: HuntLegCosts
): Double {
    val coordinates = to.mapCoordinatesOrNull() ?: return 0.0
    return costs.walkedMetersOrNull(fromId, to.uniqueId)
        ?: (mapPipDistanceMeters(
            fromLatitude,
            fromLongitude,
            coordinates.latitude,
            coordinates.longitude
        ) * WalkingRouteUtils.DETOUR_FACTOR)
}

private fun anyRoutedLeg(order: List<PokemonAlert>, costs: HuntLegCosts): Boolean {
    if (order.any { costs.walkedMetersOrNull(null, it.uniqueId) != null }) return true
    for (i in order.indices) for (j in order.indices) {
        if (i != j && costs.walkedMetersOrNull(order[i].uniqueId, order[j].uniqueId) != null) return true
    }
    return false
}

/**
 * Which targets are still reachable once you account for the ones ahead of them.
 *
 * [canArriveBeforeItEnds] asks whether each target can be reached *from where the
 * trainer stands*, one at a time. That is the right question for the first target and
 * the wrong one for the fifth: by then you have walked the four before it. A hunt was
 * therefore able to offer eight spawns you could physically make three of.
 *
 * Walks the order accumulating leg times and judges each arrival against that
 * target's own end time. Returns the ones that survive and, second, the ones that do
 * not -- demoted to the back like every other unreachable target, never hidden, since
 * an estimate is not a fact and you may be cycling.
 *
 * A demoted target does not advance the clock. You are not going, so you do not walk
 * there: the running total and the leg both carry on from the last target actually
 * kept. Without that one unreachable target early on would cascade and condemn
 * everything behind it.
 *
 * Skipped without routed legs, for the same reason [improveChain] is.
 */
private fun chainReachability(
    order: List<PokemonAlert>,
    originLatitude: Double,
    originLongitude: Double,
    nowMillis: Long,
    costs: HuntLegCosts
): Pair<List<PokemonAlert>, List<PokemonAlert>> {
    if (order.size < 2) return order to emptyList()
    if (!anyRoutedLeg(order, costs)) return order to emptyList()

    val keeping = ArrayList<PokemonAlert>(order.size)
    val missing = ArrayList<PokemonAlert>()
    var elapsedSeconds = 0L
    var fromId: String? = null
    var fromLatitude = originLatitude
    var fromLongitude = originLongitude

    for (alert in order) {
        val coordinates = alert.mapCoordinatesOrNull()
        val endMillis = TimeUtils.parseEndTimeToMillis(alert.endTime)
        if (coordinates == null || endMillis == null) {
            // Nothing to miss, and nothing to measure from -- carried as-is.
            keeping += alert
            continue
        }
        val legSeconds = huntWalkSeconds(
            legMeters(fromId, fromLatitude, fromLongitude, alert, costs).toInt()
        )
        val arrivalSeconds = elapsedSeconds + legSeconds
        val expires = TravelTime.expiresBeforeArrival(
            walkingDurationSeconds = (arrivalSeconds * HUNT_ROUTED_REACHABILITY_MARGIN).toLong(),
            remainingMillis = endMillis - nowMillis
        )
        if (expires) {
            missing += alert
            continue
        }
        keeping += alert
        elapsedSeconds = arrivalSeconds
        fromId = alert.uniqueId
        fromLatitude = coordinates.latitude
        fromLongitude = coordinates.longitude
    }
    return keeping to missing
}

/**
 * Sweeps of 2-opt before the order is called good enough.
 *
 * Two is plenty: the first takes out the obvious crossing, the second catches what
 * the first created, and this runs on the tracking service's location path where the
 * budget is a fraction of a frame.
 */
private const val HUNT_TWO_OPT_MAX_SWEEPS = 2

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
