package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.catchroutes.CatchPoint
import com.example.pokemonalertsv2.catchroutes.isArea
import com.example.pokemonalertsv2.catchroutes.pointInArea
import com.example.pokemonalertsv2.data.AlertFilterMatcher
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.tracking.isEligibleArrivalDestination
import com.example.pokemonalertsv2.ui.alerts.mapCoordinatesOrNull
import com.example.pokemonalertsv2.ui.alerts.mapPipDistanceMeters
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.WalkingRouteUtils

/**
 * What a hunt suggests right now.
 *
 * [route] is the walk: every stop on it is one the plan expects to reach before it ends,
 * in the order to walk them, and only these wear numbers on the map. [others] is every
 * other match, nearest first -- still drawn, still tappable, but not something the plan
 * is sending you to. Keeping the two apart is the fix for numbered pins that zigzag: a
 * stop the plan had already given up on used to be appended to the numbered list anyway.
 */
internal data class HuntPlan(
    val route: List<PokemonAlert>,
    val others: List<PokemonAlert>
) {
    /** The route, then the rest: the one list the step buttons and the map walk through. */
    val ordered: List<PokemonAlert> get() = route + others

    companion object {
        val Empty = HuntPlan(emptyList(), emptyList())
    }
}

/**
 * The plan for a hunt's current matches.
 *
 * Dismissed alerts are excluded because "Got it" is the whole point of the loop: the thing
 * you just caught must not be the next thing the cursor lands on. Expiry and coordinate
 * validity are delegated to [isEligibleArrivalDestination] so a hunt can never target
 * something the arrival service would refuse to accept.
 */
internal fun huntTargets(
    alerts: List<PokemonAlert>,
    definition: FilterDefinition,
    dismissedAlertIds: Set<String>,
    originLatitude: Double,
    originLongitude: Double,
    nowMillis: Long = System.currentTimeMillis(),
    costs: HuntLegCosts = HuntLegCosts.None,
    previousRouteIds: List<String> = emptyList(),
    pinnedId: String? = null,
    area: List<CatchPoint> = emptyList()
): HuntPlan {
    val matches = alerts.insideHuntArea(area).filter { alert ->
        alert.uniqueId !in dismissedAlertIds &&
            alert.isEligibleArrivalDestination(nowMillis) &&
            AlertFilterMatcher.matches(alert, definition)
    }
    return huntPlan(matches, originLatitude, originLongitude, nowMillis, costs, previousRouteIds, pinnedId)
}

/**
 * Plans the walk through [alerts]: as many stops as can be reached before they end, in the
 * order that walks them fastest, and without rearranging itself for nothing.
 *
 * Three rules:
 *
 * 1. **Only stops you will make.** A stop joins the route only if every stop on it --
 *    itself and all those after it -- is still reached in time. The route is built by
 *    cheapest feasible insertion: each round adds the stop, at the position, that costs
 *    the least extra walking while keeping the whole route on time. Nothing is added
 *    first and demoted afterwards, so nothing the plan cannot make is ever numbered.
 * 2. **No doubling back.** The route is then tightened with 2-opt (reverse a stretch)
 *    and or-opt (move one stop), accepting a move only if it is quicker *and* still on
 *    time, and anything the freed-up time now admits is inserted.
 * 3. **Stable.** [previousRouteIds] is the plan the trainer is already following. It is
 *    repaired -- stops that ended or can no longer be reached are dropped, new matches are
 *    inserted where they fit -- with its first stop, the one being walked to, kept first.
 *    A plan built from scratch replaces it only when clearly better: it reaches more
 *    stops, or its first [HUNT_ROUTE_SWITCH_HORIZON_STOPS] catches come at least
 *    [HUNT_ROUTE_SWITCH_MIN_SECONDS] and [HUNT_ROUTE_SWITCH_SHARE] sooner. Without this every new alert and every GPS fix
 *    reshuffled the route and bent it around the newcomer.
 *
 * [pinnedId] is a target the trainer chose by hand. It leads every plan, reachable or not,
 * and the route continues from it.
 *
 * "Walking" is a real routed leg wherever [costs] has one and a straight line with
 * [WalkingRouteUtils.DETOUR_FACTOR] everywhere else, measured to the point where the alert
 * becomes tappable; see [huntInteractionRadiusMeters]. Only the [HUNT_CHAIN_POOL] nearest
 * matches (plus the pinned target and its neighbours) are planned over; a broad hunt can
 * match hundreds, and the rest are neither walkable nor routed.
 */
internal fun huntPlan(
    alerts: List<PokemonAlert>,
    originLatitude: Double,
    originLongitude: Double,
    nowMillis: Long = System.currentTimeMillis(),
    costs: HuntLegCosts = HuntLegCosts.None,
    previousRouteIds: List<String> = emptyList(),
    pinnedId: String? = null
): HuntPlan {
    val byApproach = huntApproachOrder(alerts, originLatitude, originLongitude)
    if (byApproach.isEmpty()) return HuntPlan.Empty

    val pinned = pinnedId?.let { id -> byApproach.firstOrNull { it.uniqueId == id } }
    val pool = LinkedHashMap<String, PokemonAlert>()
    if (pinned != null) {
        pool[pinned.uniqueId] = pinned
        // Ranked around the pin too: a far tapped target's neighbours rank past the
        // trainer's nearest thirty and would otherwise never be candidates.
        val pinCoordinates = pinned.mapCoordinatesOrNull()
        if (pinCoordinates != null) {
            huntApproachOrder(byApproach, pinCoordinates.latitude, pinCoordinates.longitude)
                .asSequence()
                .take(HUNT_PIN_NEIGHBOURS + 1)
                .forEach { pool.putIfAbsent(it.uniqueId, it) }
        }
    }
    for (alert in byApproach) {
        if (pool.size >= HUNT_CHAIN_POOL + if (pinned != null) HUNT_PIN_NEIGHBOURS else 0) break
        pool.putIfAbsent(alert.uniqueId, alert)
    }

    val problem = HuntRouteProblem(
        stops = pool.values.toList(),
        originLatitude = originLatitude,
        originLongitude = originLongitude,
        nowMillis = nowMillis,
        costs = costs,
        pinnedIndex = if (pinned != null) 0 else -1
    )
    val index = problem.indexById()
    val head = if (pinned != null) listOf(0) else emptyList()

    val fresh = problem.solve(seed = head, fixedHead = head.size)
    val previous = previousRouteIds.mapNotNull { index[it] }.distinct()
    val chosen = if (previous.isEmpty() && pinned == null) {
        fresh
    } else {
        val seed = if (pinned != null) listOf(0) + previous.filter { it != 0 } else previous
        val kept = problem.solve(seed = seed, fixedHead = 1)
        if (isClearlyBetter(fresh, kept)) fresh else kept
    }

    val route = chosen.stops.map { problem.stops[it] }
    val routeIds = route.mapTo(HashSet()) { it.uniqueId }
    return HuntPlan(route = route, others = byApproach.filterNot { it.uniqueId in routeIds })
}

private fun isClearlyBetter(fresh: PlannedRoute, kept: PlannedRoute): Boolean {
    if (fresh.stops.size != kept.stops.size) return fresh.stops.size > kept.stops.size
    if (kept.arrivals.isEmpty()) return false
    // Judged on the near future only -- how soon the next few catches come -- because
    // that is the part of the route the trainer is about to walk and would see change.
    val k = minOf(HUNT_ROUTE_SWITCH_HORIZON_STOPS, kept.arrivals.size) - 1
    val keptSeconds = kept.arrivals[k]
    val margin = maxOf(HUNT_ROUTE_SWITCH_MIN_SECONDS.toDouble(), keptSeconds * HUNT_ROUTE_SWITCH_SHARE)
    return fresh.arrivals[k] < keptSeconds - margin
}

/** A solved route: stop indices in walking order, and the arrival second at each. */
private class PlannedRoute(val stops: List<Int>, val arrivals: LongArray)

/**
 * The planning problem for one pool of stops, with every leg priced once up front.
 *
 * The solver touches leg costs tens of thousands of times; resolving them through
 * [HuntLegCosts] each time was most of what made the old ordering slow. Here they are
 * looked up once into arrays and every move is integer arithmetic.
 */
private class HuntRouteProblem(
    val stops: List<PokemonAlert>,
    originLatitude: Double,
    originLongitude: Double,
    nowMillis: Long,
    costs: HuntLegCosts,
    /** Exempt from the deadline: the trainer chose it and is walking there regardless. */
    private val pinnedIndex: Int
) {
    private val size = stops.size
    private val fromOrigin = LongArray(size)
    private val legs = Array(size) { LongArray(size) }

    /** Latest acceptable arrival, in seconds from now, or [Long.MAX_VALUE] for no end time. */
    private val deadline = LongArray(size)

    init {
        for (i in 0 until size) {
            val to = stops[i]
            fromOrigin[i] = huntWalkSeconds(
                legMeters(null, originLatitude, originLongitude, to, costs).toInt()
            )
            val endMillis = TimeUtils.parseEndTimeToMillis(to.endTime)
            deadline[i] = when {
                i == pinnedIndex || endMillis == null -> Long.MAX_VALUE
                // No margin: the walking speed is already a slow one, so a stop the plan
                // reaches just in time is one a trainer reaches, and a numbered stop that
                // was expected to be late is exactly the route that made no sense.
                else -> (endMillis - nowMillis) / 1000L
            }
            val from = to.mapCoordinatesOrNull() ?: continue
            for (j in 0 until size) {
                if (i != j) {
                    legs[i][j] = huntWalkSeconds(
                        legMeters(to.uniqueId, from.latitude, from.longitude, stops[j], costs).toInt()
                    )
                }
            }
        }
    }

    fun indexById(): Map<String, Int> = stops.withIndex().associate { (i, alert) -> alert.uniqueId to i }

    /**
     * Repairs [seed] into a feasible route, keeps its first [fixedHead] stops first, and
     * completes it two ways -- by cheapest insertion, and by walking to the nearest stop
     * still reachable -- tightening each and keeping whichever reaches more stops, then
     * the quicker. Either heuristic alone gets stuck where the other does not: insertion
     * can open with a stop that only pays off later, and nearest-first can strand a stop
     * it passed.
     */
    fun solve(seed: List<Int>, fixedHead: Int): PlannedRoute {
        val repaired = ArrayList<Int>(size)
        var clock = 0L
        for (stop in seed) {
            val arrival = clock + leg(repaired.lastOrNull(), stop)
            // An unreachable stop is skipped, and does not advance the clock: you are
            // not going there, so one expired stop cannot condemn the ones behind it.
            if (arrival > deadline[stop]) continue
            repaired += stop
            clock = arrival
        }
        val head = minOf(fixedHead, repaired.size)
        val byInsertion = improve(ArrayList(repaired), head)
        val byNearest = improve(chainNearest(repaired), head)
        return if (byNearest.stops.size > byInsertion.stops.size ||
            (byNearest.stops.size == byInsertion.stops.size && byNearest.arrivals.sum() < byInsertion.arrivals.sum())
        ) byNearest else byInsertion
    }

    private fun improve(route: MutableList<Int>, head: Int): PlannedRoute {
        repeat(HUNT_SOLVE_ROUNDS) {
            val inserted = insertAll(route, head)
            val tightened = tighten(route, head)
            if (!inserted && !tightened) return PlannedRoute(route, arrivalsOrNull(route)!!)
        }
        return PlannedRoute(route, arrivalsOrNull(route)!!)
    }

    /** [start] extended by repeatedly walking to the nearest stop that can still be reached. */
    private fun chainNearest(start: List<Int>): MutableList<Int> {
        val route = ArrayList(start)
        val inRoute = BooleanArray(size).also { flags -> route.forEach { flags[it] = true } }
        var clock = arrivalsOrNull(route)?.lastOrNull() ?: 0L
        while (true) {
            val from = route.lastOrNull()
            var best = -1
            for (candidate in 0 until size) {
                if (inRoute[candidate] || clock + leg(from, candidate) > deadline[candidate]) continue
                if (best < 0 || leg(from, candidate) < leg(from, best)) best = candidate
            }
            if (best < 0) return route
            clock += leg(from, best)
            route += best
            inRoute[best] = true
        }
    }

    private fun leg(from: Int?, to: Int): Long = if (from == null) fromOrigin[to] else legs[from][to]

    /**
     * What a route costs: the sum of its arrival times, or null if it misses a stop.
     *
     * Summed rather than the time of the last arrival. The plan is redone as the trainer
     * walks and new alerts land, so its far end is rarely walked as planned; what matters
     * is catching the near things early. Scoring only the finish sent trainers on long
     * legs to outliers so the route could end tidily somewhere nobody would reach.
     */
    private fun scoreOrNull(route: List<Int>): Long? {
        var clock = 0L
        var sum = 0L
        var previous: Int? = null
        for (stop in route) {
            clock += leg(previous, stop)
            if (clock > deadline[stop]) return null
            sum += clock
            previous = stop
        }
        return sum
    }

    /** Arrival seconds at each stop, or null if any stop is reached after its deadline. */
    private fun arrivalsOrNull(route: List<Int>): LongArray? {
        val arrivals = LongArray(route.size)
        var clock = 0L
        var previous: Int? = null
        for (k in route.indices) {
            val stop = route[k]
            clock += leg(previous, stop)
            if (clock > deadline[stop]) return null
            arrivals[k] = clock
            previous = stop
        }
        return arrivals
    }

    /** Cheapest feasible insertion until nothing more fits. True if anything was added. */
    private fun insertAll(route: MutableList<Int>, head: Int): Boolean {
        val inRoute = BooleanArray(size).also { flags -> route.forEach { flags[it] = true } }
        var changed = false
        while (true) {
            val arrivals = arrivalsOrNull(route) ?: return changed
            val n = route.size
            // slack[k]: how much later stop k and everything after it may be reached.
            val slack = LongArray(n + 1).also { it[n] = Long.MAX_VALUE }
            for (k in n - 1 downTo 0) {
                val own = if (deadline[route[k]] == Long.MAX_VALUE) Long.MAX_VALUE else deadline[route[k]] - arrivals[k]
                slack[k] = minOf(own, slack[k + 1])
            }
            var bestStop = -1
            var bestPosition = -1
            var bestDelta = Long.MAX_VALUE
            for (candidate in 0 until size) {
                if (inRoute[candidate]) continue
                for (position in head..n) {
                    val before = if (position == 0) null else route[position - 1]
                    val arrival = (if (position == 0) 0L else arrivals[position - 1]) + leg(before, candidate)
                    if (arrival > deadline[candidate]) continue
                    // Added waiting, summed over every stop: the new stop's own arrival
                    // plus the delay it pushes onto each stop behind it.
                    val delta = if (position == n) {
                        arrival
                    } else {
                        val delay = arrival + legs[candidate][route[position]] - arrivals[position]
                        if (delay > slack[position]) continue
                        arrival + delay * (n - position)
                    }
                    if (delta < bestDelta || (delta == bestDelta && deadline[candidate] < deadline[bestStop])) {
                        bestStop = candidate
                        bestPosition = position
                        bestDelta = delta
                    }
                }
            }
            if (bestStop < 0) return changed
            route.add(bestPosition, bestStop)
            inRoute[bestStop] = true
            changed = true
        }
    }

    /** 2-opt and or-opt, first improvement, until neither helps. True if the order changed. */
    private fun tighten(route: MutableList<Int>, head: Int): Boolean {
        var best = scoreOrNull(route) ?: return false
        var changed = false
        var improved = true
        var sweeps = 0
        while (improved && sweeps++ < HUNT_TIGHTEN_MAX_SWEEPS) {
            improved = false
            for (i in head until route.size - 1) {
                for (j in i + 1 until route.size) {
                    route.subList(i, j + 1).reverse()
                    val total = scoreOrNull(route)
                    if (total != null && total < best) {
                        best = total
                        improved = true
                    } else {
                        route.subList(i, j + 1).reverse()
                    }
                }
            }
            for (from in head until route.size) {
                for (to in head until route.size) {
                    if (from == to) continue
                    val stop = route.removeAt(from)
                    route.add(to, stop)
                    val total = scoreOrNull(route)
                    if (total != null && total < best) {
                        best = total
                        improved = true
                    } else {
                        route.removeAt(to)
                        route.add(from, stop)
                    }
                }
            }
            if (improved) changed = true
        }
        return changed
    }
}

/**
 * Nearest-first by *approach* distance: how far there is to walk before the alert is
 * tappable, not how far away its pin is.
 *
 * Deliberately its own function rather than a change to
 * [com.example.pokemonalertsv2.ui.alerts.mapPipBrowseOrder], which is also the browse
 * cursor's order and should keep stepping through pins by pin distance.
 *
 * Ties break on the alert id, the same way the browse order does, so two alerts on one
 * PokeStop -- now genuinely tied at zero -- cannot swap places between recompositions.
 * Alerts without coordinates are dropped here, as they were before.
 */
internal fun huntApproachOrder(
    alerts: List<PokemonAlert>,
    originLatitude: Double,
    originLongitude: Double
): List<PokemonAlert> = alerts
    .mapNotNull { alert ->
        alert.mapCoordinatesOrNull()?.let { coordinates ->
            alert to huntApproachMeters(
                originLatitude,
                originLongitude,
                coordinates.latitude,
                coordinates.longitude,
                alert
            )
        }
    }
    .sortedWith(compareBy({ (_, meters) -> meters }, { (alert, _) -> alert.uniqueId }))
    .map { (alert, _) -> alert }

/**
 * Straight-line metres left to walk before [alert] is tappable.
 *
 * Zero once you are already standing inside its interaction radius, which is the
 * intended answer: something you can tap without moving belongs at the front of the
 * route, not wherever its pin happens to sit.
 */
private fun huntApproachMeters(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double,
    alert: PokemonAlert
): Double =
    (mapPipDistanceMeters(fromLatitude, fromLongitude, toLatitude, toLongitude) -
        huntInteractionRadiusMeters(alert)).coerceAtLeast(0.0)

/**
 * One leg in walked metres: routed where known, scaled straight line where not.
 *
 * Minus the radius you can interact with [to] from, because that stretch is priced into
 * every route the router returns and walked by nobody. A leg you are already inside costs
 * zero.
 */
private fun legMeters(
    fromId: String?,
    fromLatitude: Double,
    fromLongitude: Double,
    to: PokemonAlert,
    costs: HuntLegCosts
): Double {
    val coordinates = to.mapCoordinatesOrNull() ?: return 0.0
    val toPin = costs.walkedMetersOrNull(fromId, to.uniqueId)
        ?: (mapPipDistanceMeters(
            fromLatitude,
            fromLongitude,
            coordinates.latitude,
            coordinates.longitude
        ) * WalkingRouteUtils.DETOUR_FACTOR)
    return (toPin - huntInteractionRadiusMeters(to)).coerceAtLeast(0.0)
}

/** Enough to cover the cluster you are standing in, cheap enough to redo often. */
internal const val HUNT_CHAIN_POOL = 30

/** Extra candidates ranked around a hand-picked target, so the route can continue from it. */
private const val HUNT_PIN_NEIGHBOURS = 10

/** Insert-then-tighten rounds; each round only runs again if the last changed something. */
private const val HUNT_SOLVE_ROUNDS = 4

/** Bound on 2-opt/or-opt passes per round. */
private const val HUNT_TIGHTEN_MAX_SWEEPS = 6

/** About 150 m of walking: the least a fresh plan has to save before it replaces the current one. */
internal const val HUNT_ROUTE_SWITCH_MIN_SECONDS = 110L

/** ...and at least this share of the current route's time. */
internal const val HUNT_ROUTE_SWITCH_SHARE = 0.15

/** How many upcoming catches a fresh plan is compared on. */
internal const val HUNT_ROUTE_SWITCH_HORIZON_STOPS = 5

/**
 * Whether tapping [alert] should make it the hunt's target.
 *
 * Kept out of the service so the rule is testable on its own, the way
 * [com.example.pokemonalertsv2.ui.alerts.resolveMapPipTrackingIntent] is. Eligibility
 * is checked *here* rather than left to
 * [com.example.pokemonalertsv2.tracking.ArrivalTrackingRepository.startTracking],
 * which throws on a bad destination -- a tap on an expired pin should do nothing, not
 * crash a walk.
 */
internal fun shouldRetargetHuntTo(
    alert: PokemonAlert,
    currentTargetId: String?,
    huntActive: Boolean,
    nowMillis: Long = System.currentTimeMillis()
): Boolean {
    if (!huntActive) return false
    if (alert.uniqueId == currentTargetId) return false
    return alert.isEligibleArrivalDestination(nowMillis)
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
    nowMillis: Long = System.currentTimeMillis(),
    area: List<CatchPoint> = emptyList()
): Boolean =
    alert.isEligibleArrivalDestination(nowMillis) &&
        alert.insideHuntArea(area) &&
        AlertFilterMatcher.matches(alert, definition)

/**
 * A hunt limited to a drawn area only ever looks at alerts inside it; without an area (fewer than
 * three corners) nothing is excluded. Alerts without coordinates cannot be placed, so an area drops them.
 */
internal fun PokemonAlert.insideHuntArea(area: List<CatchPoint>): Boolean {
    if (!area.isArea()) return true
    val latitude = latitude ?: return false
    val longitude = longitude ?: return false
    return pointInArea(CatchPoint(latitude, longitude), area)
}

internal fun List<PokemonAlert>.insideHuntArea(area: List<CatchPoint>): List<PokemonAlert> =
    if (!area.isArea()) this else filter { it.insideHuntArea(area) }
