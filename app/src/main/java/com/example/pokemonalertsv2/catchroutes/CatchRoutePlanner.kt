package com.example.pokemonalertsv2.catchroutes

import com.example.pokemonalertsv2.data.RouteMatrixPoint
import com.example.pokemonalertsv2.data.RouteMatrixRequest
import com.example.pokemonalertsv2.data.RouteMatrixResponse
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import kotlin.math.*

/**
 * Points the backend accepts in one `POST /api/routes/matrix`. Every stop candidate the order
 * search may choose between travels in that one matrix, so this number bounds how many spawn
 * windows a route can weigh against each other. A backend still on the older limit answers 400,
 * which the planner treats as a signal to ask again with a smaller pool.
 */
internal const val CATCH_MATRIX_MAX_POINTS = 50
internal const val CATCH_MATRIX_FALLBACK_POINTS = 30

/** Points the backend accepts in one `POST /api/routes/path`; only the chosen stops are routed. */
internal const val CATCH_PATH_MAX_POINTS = 30

/** Candidate anchors that fit one matrix beside the start and the finish. */
internal fun catchAnchorCap(matrixPoints: Int) = matrixPoints - 2

/** Stops one routed path can carry beside the start and the finish. */
internal const val CATCH_MAX_STOPS = CATCH_PATH_MAX_POINTS - 2

class CatchRoutePlanner(private val service: CatchRoutesService) {
    private val availability = SpawnAvailabilityRepository(service)

    /** [preloaded] skips the windows download (recommendations load once for many candidates). */
    suspend fun generate(settings: CatchRouteSettings, visits: List<CatchVisit> = emptyList(),
        preloaded: SpawnAvailability? = null, progress: (String) -> Unit = {}): CatchItinerary = withContext(Dispatchers.Default) {
        settings.validate()
        if (settings.fixed) {
            val loaded = preloaded?.forRoute(settings) ?: availability.load(settings, progress)
            val data = loaded.copy(opportunities = loaded.opportunities.filterNot { o -> visits.any { sameCycle(it.opportunity, o, it.visitedAt) } })
            // Zero encounters is still a route: the trainer chose to walk it.
            return@withContext planFixedRoute(settings, data)
        }
        var best: CatchItinerary? = null
        var lastError: Exception? = null
        var requests = 0
        var leftArea = false
        suspend fun path(points: List<CatchPoint>, data: SpawnAvailability): CatchItinerary? {
            if (requests >= 8 || points.size !in 2..CATCH_PATH_MAX_POINTS) return null
            requests++
            val request = request(points)
            val response = service.path(request).catchBody()
            if (response.status == "unreachable") return null
            val positions = validateCatchPath(response, request, settings)
            if (positions.last().meters > settings.walkingBudgetMeters + 0.01) return null
            // The streets between two in-area stops can still leave the area; such a route is not an option.
            if (!pathInsideArea(positions, settings.area)) { leftArea = true; return null }
            // Where each routed point sits along the path: the path is scaled to leg distances.
            val stopMeters = response.legs.runningFold(0.0) { total, leg -> total + leg.distanceMeters }
            val stops = points.indices.drop(1).take(points.size - 1 - if (settings.destination != null) 1 else 0)
                .map { i -> points[i] to stopMeters[i] }
            val waits = planCatchWaits(settings, stops, data.opportunities)
                .takeIf { waits -> positions.last().meters + waits.sumOf { it.millis } / 1000.0 * settings.speedMps <= settings.walkingBudgetMeters + 0.01 }
                .orEmpty()
            val encounters = scoreCatchPath(positions, settings, data.opportunities, waits)
            val warnings = data.warnings + if (encounters.any { it.opportunity.uncertainty.isNotEmpty() })
                listOf("Predictions include uncertain timing. Open a spawnpoint for evidence.") else emptyList()
            return CatchItinerary(settings, positions, encounters, points, warnings, data.version, data.sources, waits)
        }
        val completed = withTimeoutOrNull(30_000) {
            val loaded = preloaded?.forRoute(settings) ?: availability.load(settings, progress)
            val data = loaded.copy(opportunities = loaded.opportunities.filterNot { o -> visits.any { sameCycle(it.opportunity, o, it.visitedAt) } })
            if (data.opportunities.isEmpty()) throw CatchApiException(if (data.restrictedPointCount > 0) "No usable spawn windows. ${data.restrictedPointCount} spawnpoints require live confirmation; predictions cannot enable them." else "No usable spawn windows in this area and time. Try another start or enable predictions.")
            val groups = candidateGroups(settings, data.opportunities)
            var matrixPoints = CATCH_MATRIX_MAX_POINTS
            for (seed in 0..2) {
                coroutineContext.ensureActive()
                try {
                    progress("Optimizing walking route ${seed + 1} of 3")
                    var anchors = selectCandidates(groups, settings.start, seed, catchAnchorCap(matrixPoints))
                    var points = listOf(settings.start) + anchors.map { it.point } + listOfNotNull(settings.destination)
                    if (points.size < 2) continue
                    requests++
                    var request = request(points)
                    val matrix = try {
                        service.matrix(request).catchBody()
                    } catch (e: CatchApiException) {
                        // A backend still on the older thirty-point limit rejects the wider pool. Ask
                        // once more with a pool it accepts, and keep that size for the later seeds.
                        if (e.status != 400 || matrixPoints <= CATCH_MATRIX_FALLBACK_POINTS || requests >= 8) throw e
                        matrixPoints = CATCH_MATRIX_FALLBACK_POINTS
                        anchors = selectCandidates(groups, settings.start, seed, catchAnchorCap(matrixPoints))
                        points = listOf(settings.start) + anchors.map { it.point } + listOfNotNull(settings.destination)
                        if (points.size < 2) continue
                        requests++
                        request = request(points)
                        service.matrix(request).catchBody()
                    }
                    val costs = validateCatchMatrix(matrix, request)
                    val legs = CatchLegCoverage(settings, points, data.opportunities)
                    val budget = settings.walkingBudgetMeters * CATCH_ORDER_BUDGET_SHARE
                    val order = improveCatchOrder(settings, anchors, costs, catchBeamOrder(settings, anchors, costs, legs = legs, budget = budget), legs, budget)
                    var selected = listOf(settings.start) + order.map { anchors[it].point } + listOfNotNull(settings.destination)
                    if (selected.size < 2) continue
                    // Remove consecutive equal positions but retain a closed route's final return.
                    selected = selected.filterIndexed { i, p -> i == 0 || catchDistance(selected[i - 1], p) > 0.2 }
                    val candidate = path(selected, data)
                    if (candidate != null && betterCatchRoute(candidate, best)) best = candidate
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    lastError = e
                    if (e is CatchApiException && e.retryAtMillis > 0) break
                }
            }
            // Two bounded refinements. Both are routed again; circle entry is never a free jump.
            val incumbent = best
            if (incumbent != null && requests < 8 && (lastError as? CatchApiException)?.retryAtMillis.orZero() == 0L) {
                val shifted = incumbent.anchors.toMutableList()
                var minMeters = 0.0
                for (i in 1 until shifted.size - if (settings.destination != null) 1 else 0) {
                    val entry = incumbent.path.zipWithNext().firstNotNullOfOrNull { (a, b) ->
                        if (b.meters < minMeters) null else catchCircleInterval(a.point, b.point, shifted[i], settings.radius * 0.9)?.let {
                            val t = it.start
                            CatchPathPosition(CatchPoint(a.point.latitude + (b.point.latitude - a.point.latitude) * t,
                                a.point.longitude + (b.point.longitude - a.point.longitude) * t), a.meters + (b.meters - a.meters) * t)
                        }
                    }
                    if (entry != null) { shifted[i] = entry.point; minMeters = entry.meters }
                }
                try {
                    progress("Validating encounter range along paths")
                    path(shifted, data)?.let { if (betterCatchRoute(it, best)) best = it }
                    val current = best!!
                    if (requests < 8 && current.anchors.size > 2) {
                        // Remove the most redundant anchor; final scoring includes all incidental spawns.
                        val remove = (1 until current.anchors.lastIndex).minByOrNull { i ->
                            data.opportunities.count { catchDistance(it.point, current.anchors[i]) <= settings.radius &&
                                catchDistance(it.point, current.anchors[i - 1]) > settings.radius &&
                                catchDistance(it.point, current.anchors[i + 1]) > settings.radius }
                        }
                        if (remove != null) path(current.anchors.filterIndexed { i, _ -> i != remove }, data)?.let {
                            if (betterCatchRoute(it, best)) best = it
                        }
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { lastError = e }
            }
            true
        } == true
        coroutineContext.ensureActive()
        best?.takeIf { it.encounters.isNotEmpty() }
            ?: throw (lastError ?: CatchApiException(if (leftArea) "Every walking route found leaves the area. Draw a larger area or move the start."
                else if (!completed) "Route planning took too long. Try a shorter session or a nearby start."
                else "No walking route fits this start and time. Try a longer session or a nearby start."))
    }
    private fun Long?.orZero() = this ?: 0L
    private fun request(points: List<CatchPoint>) = RouteMatrixRequest.pedestrian(points.mapIndexed { i, p -> RouteMatrixPoint("p$i", p.latitude, p.longitude) })
}

internal data class CatchAnchor(val point: CatchPoint, val opportunities: List<SpawnOpportunity>)

internal fun candidateGroups(settings: CatchRouteSettings, opportunities: List<SpawnOpportunity>): List<CatchAnchor> {
    val index = CatchSpatialIndex(opportunities) { it.point }
    // One seed per ~40 m cell; retain every opportunity in the spatial index for final path scoring.
    return opportunities.distinctBy { floor(it.point.latitude / 0.00036) to floor(it.point.longitude / 0.00055) }
        .map { o -> CatchAnchor(o.point, index.near(o.point, radius = settings.radius).filter { catchDistance(o.point, it.point) <= settings.radius }) }
        .sortedByDescending { it.opportunities.sumOf { o -> o.expectedCatch } / (1 + catchDistance(settings.start, it.point) / 1000) }
}

internal fun selectCandidates(groups: List<CatchAnchor>, start: CatchPoint, seed: Int,
    cap: Int = catchAnchorCap(CATCH_MATRIX_MAX_POINTS)): List<CatchAnchor> {
    val ordered = when (seed) {
        1 -> groups.sortedByDescending { it.opportunities.sumOf { o -> o.expectedCatch } / (1 + catchDistance(start, it.point) / 250) }
        2 -> groups.groupBy { floor((atan2(it.point.latitude - start.latitude, it.point.longitude - start.longitude) + PI) / (PI / 4)).toInt() }
            .values.toList().let { sectors -> (0 until (sectors.maxOfOrNull { it.size } ?: 0)).flatMap { i -> sectors.mapNotNull { it.getOrNull(i) } } }
        else -> groups
    }
    val result = mutableListOf<CatchAnchor>()
    for (g in ordered) {
        if (result.none { catchDistance(it.point, g.point) < 30 }) result += g
        if (result.size == cap) break
    }
    return result
}

internal fun validateCatchMatrix(matrix: RouteMatrixResponse, request: RouteMatrixRequest): List<List<Double?>> {
    val ids = request.points.map { it.id }
    if (matrix.ids != ids || matrix.distanceMeters.size != ids.size || matrix.durationSeconds.size != ids.size ||
        matrix.distanceMeters.any { it.size != ids.size } || matrix.durationSeconds.any { it.size != ids.size })
        throw CatchApiException("Invalid walking matrix returned by backend.")
    return matrix.distanceMeters.mapIndexed { i, row -> row.mapIndexed { j, d ->
        val t = matrix.durationSeconds[i][j]
        if ((d != null && d < 0) || (t != null && t < 0)) throw CatchApiException("Invalid walking costs.")
        if (d == null || t == null) null else d.toDouble()
    } }
}

/**
 * How long to stand at a stop reached at [arrival]: until the start, within the allowed wait, that
 * adds the most expected catches among [opportunities] not yet [covered]. Zero when waiting is off
 * or gains nothing; ties go to the shortest wait.
 */
internal fun catchWaitAt(settings: CatchRouteSettings, opportunities: List<SpawnOpportunity>, arrival: Long, covered: Set<String>): Long {
    if (settings.maxWaitMinutes <= 0) return 0
    val limit = min(arrival + settings.maxWaitMinutes * 60_000L, settings.endAtMillis)
    fun value(at: Long) = opportunities.sumOf { if (it.id !in covered && at >= it.availableFrom && at < it.despawnAt) it.expectedCatch else 0.0 }
    var bestAt = arrival
    var best = value(arrival)
    for (at in opportunities.asSequence().map { it.availableFrom }.filter { it in (arrival + 1)..limit }.distinct().sorted()) {
        val gained = value(at)
        if (gained > best + 1e-9) { best = gained; bestAt = at }
    }
    return bestAt - arrival
}

/** Waits along a routed path, stop by stop, with the same rule the order search used. */
internal fun planCatchWaits(settings: CatchRouteSettings, stops: List<Pair<CatchPoint, Double>>, opportunities: List<SpawnOpportunity>): List<CatchWait> {
    if (settings.maxWaitMinutes <= 0) return emptyList()
    val index = CatchSpatialIndex(opportunities) { it.point }
    val waits = mutableListOf<CatchWait>()
    val covered = mutableSetOf<String>()
    for ((point, meters) in stops) {
        val near = index.near(point, radius = settings.radius).filter { catchDistance(point, it.point) <= settings.radius }
        val arrival = catchTimeAt(settings, meters, waits)
        val wait = catchWaitAt(settings, near, arrival, covered)
        if (wait > 0) waits += CatchWait(meters, wait)
        near.filter { arrival + wait >= it.availableFrom && arrival + wait < it.despawnAt }.forEach { covered += it.id }
    }
    return waits
}

/**
 * Spawn windows passed on the way between two matrix points, with the straight line standing in for the
 * street: each with the fractions of the leg where its circle is entered and left. Lets the order search
 * value what a leg walks past, not only the circle at its end.
 */
internal class CatchLegCoverage(private val settings: CatchRouteSettings, private val points: List<CatchPoint>, opportunities: List<SpawnOpportunity>) {
    data class Pass(val opportunity: SpawnOpportunity, val enter: Double, val leave: Double)
    private val index = CatchSpatialIndex(opportunities) { it.point }
    private val cache = HashMap<Long, List<Pass>>()
    fun along(from: Int, to: Int): List<Pass> = cache.getOrPut(from * 4096L + to) {
        val a = points[from]; val b = points[to]
        index.near(a, b, settings.radius).mapNotNull { o -> catchCircleInterval(a, b, o.point, settings.radius)?.let { Pass(o, it.start, it.endInclusive) } }
    }
    /** Windows caught walking [distance] metres from point [from] to [to], leaving at [departAt], not yet in [covered]. */
    fun gained(from: Int, to: Int, distance: Double, departAt: Long, covered: Set<String>): List<SpawnOpportunity> =
        along(from, to).filter { pass ->
            val o = pass.opportunity
            if (o.id in covered) return@filter false
            val enter = departAt + (pass.enter * distance / settings.speedMps * 1000).toLong()
            val leave = departAt + (pass.leave * distance / settings.speedMps * 1000).toLong()
            val at = max(enter, o.availableFrom)
            at <= leave && at < o.despawnAt
        }.map { it.opportunity }
}

/** The order search plans to this share of the walking budget: the routed path is rarely exactly the matrix sum. */
internal const val CATCH_ORDER_BUDGET_SHARE = 0.97


/**
 * The beam's width is paired with the candidate pool: a wider pool only pays when the beam is
 * wide enough to keep its better states. Measured on recorded Alsbach and Darmstadt windows,
 * 48 candidates at width 96 find 2.6% more expected catches than 28 at width 48, and width 128
 * adds only a further 0.1% for another third of the search time.
 */
internal suspend fun catchBeamOrder(settings: CatchRouteSettings, anchors: List<CatchAnchor>, costs: List<List<Double?>>,
    width: Int = 96, depth: Int = CATCH_MAX_STOPS, legs: CatchLegCoverage? = null, budget: Double = settings.walkingBudgetMeters): List<Int> {
    data class State(val order: List<Int>, val covered: Set<String>, val expected: Double, val meters: Double, val waitMillis: Long = 0)
    val finishIndex = if (settings.destination != null) anchors.size + 1 else null
    fun finishCost(s: State): Double? = finishIndex?.let { costs[s.order.lastOrNull()?.plus(1) ?: 0][it] } ?: if (finishIndex == null) 0.0 else null
    fun spent(s: State) = s.meters + s.waitMillis / 1000.0 * settings.speedMps
    // Expected catches, not a count: a verified window is worth more than a guessed one.
    val comparator = compareByDescending<State> { round(it.expected * 1000) }.thenBy { spent(it) + (finishCost(it) ?: Double.POSITIVE_INFINITY) }
    var beam = listOf(State(emptyList(), emptySet(), 0.0, 0.0))
    var best: State? = beam.first().takeIf { finishCost(it)?.let { d -> d <= budget } == true }
    repeat(depth) {
        coroutineContext.ensureActive()
        val next = mutableListOf<State>()
        for (s in beam) for (i in anchors.indices) {
            if (s.order.lastOrNull() == i) continue
            val distance = costs[s.order.lastOrNull()?.plus(1) ?: 0][i + 1] ?: continue
            if (distance <= 0.0 && s.order.contains(i)) continue
            val meters = s.meters + distance
            val returnMeters = if (finishIndex != null) costs[i + 1][finishIndex] ?: continue else 0.0
            if (meters + returnMeters + s.waitMillis / 1000.0 * settings.speedMps > budget) continue
            val arrival = settings.startAtMillis + ceil(meters / settings.speedMps * 1000).toLong() + s.waitMillis
            val departAt = settings.startAtMillis + ceil(s.meters / settings.speedMps * 1000).toLong() + s.waitMillis
            val passed = legs?.gained(s.order.lastOrNull()?.plus(1) ?: 0, i + 1, distance, departAt, s.covered).orEmpty()
            val coveredOnArrival = if (passed.isEmpty()) s.covered else s.covered + passed.map { it.id }
            // Both walking on and, when it pays, waiting here are kept; the beam decides which ends better.
            val wait = catchWaitAt(settings, anchors[i].opportunities, arrival, coveredOnArrival)
            for (w in if (wait > 0) listOf(0L, wait) else listOf(0L)) {
                if (meters + returnMeters + (s.waitMillis + w) / 1000.0 * settings.speedMps > budget) continue
                val at = arrival + w
                val gained = passed + anchors[i].opportunities.filter { it.id !in coveredOnArrival && at >= it.availableFrom && at < it.despawnAt }
                if (gained.isEmpty() && i in s.order) continue
                val n = State(s.order + i, s.covered + gained.map { it.id }, s.expected + gained.sumOf { it.expectedCatch }, meters, s.waitMillis + w)
                next += n
                if (best == null || comparator.compare(n, best!!) < 0) best = n
            }
        }
        beam = next.sortedWith(comparator).distinctBy { Triple(it.order.lastOrNull(), it.covered, it.waitMillis) }.take(width)
        if (beam.isEmpty()) return best?.order.orEmpty()
    }
    return best?.order.orEmpty()
}

internal data class CatchOrderScore(val expected: Double, val meters: Double)

/**
 * Expected catches and walking (waits counted as distance) for visiting [anchors] in [order] on [costs],
 * or null when it breaks the budget or a leg is unroutable. With [legs], windows passed along each leg count too; without, only each anchor's own circle.
 */
internal fun scoreCatchOrder(settings: CatchRouteSettings, anchors: List<CatchAnchor>, costs: List<List<Double?>>, order: List<Int>,
    legs: CatchLegCoverage? = null, budget: Double = settings.walkingBudgetMeters): CatchOrderScore? {
    var previous = 0
    var meters = 0.0
    var waited = 0L
    val covered = mutableSetOf<String>()
    var expected = 0.0
    for (i in order) {
        val distance = costs[previous][i + 1] ?: return null
        val departAt = settings.startAtMillis + ceil(meters / settings.speedMps * 1000).toLong() + waited
        legs?.gained(previous, i + 1, distance, departAt, covered)?.forEach { if (covered.add(it.id)) expected += it.expectedCatch }
        meters += distance
        if (meters + waited / 1000.0 * settings.speedMps > budget) return null
        val arrival = settings.startAtMillis + ceil(meters / settings.speedMps * 1000).toLong() + waited
        val wait = catchWaitAt(settings, anchors[i].opportunities, arrival, covered)
        waited += wait
        for (o in anchors[i].opportunities) {
            if (arrival + wait >= o.availableFrom && arrival + wait < o.despawnAt && covered.add(o.id)) expected += o.expectedCatch
        }
        previous = i + 1
    }
    if (settings.destination != null) meters += costs[previous][anchors.size + 1] ?: return null
    val spent = meters + waited / 1000.0 * settings.speedMps
    return if (spent <= budget) CatchOrderScore(expected, spent) else null
}

/** Bounded local improvement on the same directed matrix. Geometry still decides the final score. */
internal suspend fun improveCatchOrder(settings: CatchRouteSettings, anchors: List<CatchAnchor>, costs: List<List<Double?>>,
    initial: List<Int>, legs: CatchLegCoverage? = null, budget: Double = settings.walkingBudgetMeters): List<Int> {
    fun score(order: List<Int>): CatchOrderScore? = scoreCatchOrder(settings, anchors, costs, order, legs, budget)
    val comparator = compareByDescending<CatchOrderScore> { round(it.expected * 1000) }.thenBy { it.meters }
    var best = initial
    var bestScore = score(initial)
    repeat(2) {
        val base = best
        suspend fun consider(order: List<Int>) {
            coroutineContext.ensureActive()
            val value = score(order) ?: return
            if (bestScore == null || comparator.compare(value, bestScore!!) < 0) { best = order; bestScore = value }
        }
        for (i in base.indices) {
            consider(base.filterIndexed { index, _ -> index != i })
            for (j in i + 1 until base.size) consider(base.take(i) + base.subList(i, j + 1).reversed() + base.drop(j + 1))
        }
        if (base.size < CATCH_MAX_STOPS) for (anchor in anchors.indices) for (at in 0..base.size) {
            if (base.getOrNull(at - 1) != anchor && base.getOrNull(at) != anchor)
                consider(base.take(at) + anchor + base.drop(at))
        }
        if (best == base) return best
    }
    return best
}

/** More expected catches wins; then more encounters; then the shorter walk. */
internal fun betterCatchRoute(candidate: CatchItinerary, current: CatchItinerary?): Boolean {
    if (current == null) return true
    val a = round(candidate.expectedCatches * 1000)
    val b = round(current.expectedCatches * 1000)
    if (a != b) return a > b
    if (candidate.encounters.size != current.encounters.size) return candidate.encounters.size > current.encounters.size
    return candidate.distanceMeters < current.distanceMeters
}

internal fun validateCatchPath(response: CatchPathResponse, request: RouteMatrixRequest, settings: CatchRouteSettings): List<CatchPathPosition> {
    if (response.status != "ok" || response.legs.size != request.points.size - 1 || response.snappedPoints.map { it.id } != request.points.map { it.id })
        throw CatchApiException("Backend returned an incomplete walking path.")
    val positions = mutableListOf<CatchPathPosition>()
    var cumulative = 0.0
    response.legs.forEachIndexed { i, leg ->
        if (leg.fromId != request.points[i].id || leg.toId != request.points[i + 1].id || leg.geometry.type != "LineString" ||
            !leg.distanceMeters.isFinite() || leg.distanceMeters < 0 || !leg.durationSeconds.isFinite() || leg.durationSeconds < 0)
            throw CatchApiException("Invalid walking path metadata.")
        val points = leg.geometry.coordinates.map { c ->
            if (c.size != 2) throw CatchApiException("Invalid path coordinates.")
            CatchPoint(c[1], c[0]).also { if (!it.valid) throw CatchApiException("Invalid path coordinates.") }
        }
        if (points.size < 2 || (positions.isNotEmpty() && catchDistance(positions.last().point, points.first()) > 3))
            throw CatchApiException("Walking path has a gap.")
        val lengths = points.zipWithNext().map { (a, b) -> catchDistance(a, b) }
        val sum = lengths.sum()
        if (sum > 5 && leg.distanceMeters < sum * 0.8) throw CatchApiException("Walking path distance is inconsistent.")
        if (positions.isEmpty()) positions += CatchPathPosition(points.first(), 0.0)
        points.drop(1).forEachIndexed { j, p ->
            cumulative += if (sum > 0) leg.distanceMeters * lengths[j] / sum else 0.0
            positions += CatchPathPosition(p, cumulative)
        }
    }
    if (catchDistance(positions.first().point, settings.start) > 25) throw CatchApiException("Start is too far from a walking path. Pick a point on a nearby street or footpath.")
    settings.destination?.let { if (catchDistance(positions.last().point, it) > 25) throw CatchApiException("Finish is too far from a walking path. Pick a nearby street or footpath.") }
    return positions
}
