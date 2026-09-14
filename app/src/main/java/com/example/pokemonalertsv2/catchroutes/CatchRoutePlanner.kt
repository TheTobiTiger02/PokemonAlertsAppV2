package com.example.pokemonalertsv2.catchroutes

import com.example.pokemonalertsv2.data.RouteMatrixPoint
import com.example.pokemonalertsv2.data.RouteMatrixRequest
import com.example.pokemonalertsv2.data.RouteMatrixResponse
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import kotlin.math.*

class CatchRoutePlanner(private val service: CatchRoutesService) {
    private val availability = SpawnAvailabilityRepository(service)

    suspend fun generate(settings: CatchRouteSettings, visits: List<CatchVisit> = emptyList(),
        progress: (String) -> Unit = {}): CatchItinerary = withContext(Dispatchers.Default) {
        settings.validate()
        var best: CatchItinerary? = null
        var lastError: Exception? = null
        var requests = 0
        suspend fun path(points: List<CatchPoint>, data: SpawnAvailability): CatchItinerary? {
            if (requests >= 8 || points.size !in 2..30) return null
            requests++
            val request = request(points)
            val response = service.path(request).catchBody()
            if (response.status == "unreachable") return null
            val positions = validateCatchPath(response, request, settings)
            if (positions.last().meters > settings.walkingBudgetMeters + 0.01) return null
            val encounters = scoreCatchPath(positions, settings, data.opportunities)
            val warnings = data.warnings + if (encounters.any { it.opportunity.uncertainty.isNotEmpty() })
                listOf("Predictions include uncertain timing. Open a group for evidence.") else emptyList()
            return CatchItinerary(settings, positions, encounters, points, warnings, data.version, data.sources)
        }
        withTimeoutOrNull(30_000) {
            val loaded = availability.load(settings, progress)
            val data = loaded.copy(opportunities = loaded.opportunities.filterNot { o -> visits.any { sameCycle(it.opportunity, o, it.visitedAt) } })
            if (data.opportunities.isEmpty()) throw CatchApiException(if (data.restrictedPointCount > 0) "No usable spawn windows. ${data.restrictedPointCount} spawnpoints require live confirmation; predictions cannot enable them." else "No usable spawn windows in this area and time. Try another start or enable predictions.")
            val groups = candidateGroups(settings, data.opportunities)
            for (seed in 0..2) {
                coroutineContext.ensureActive()
                try {
                    progress("Optimizing walking route ${seed + 1} of 3")
                    val anchors = selectCandidates(groups, settings.start, seed)
                    val points = listOf(settings.start) + anchors.map { it.point } + listOfNotNull(settings.destination)
                    if (points.size < 2) continue
                    requests++
                    val request = request(points)
                    val matrix = service.matrix(request).catchBody()
                    val costs = validateCatchMatrix(matrix, request)
                    val order = improveCatchOrder(settings, anchors, costs, catchBeamOrder(settings, anchors, costs))
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
        }
        coroutineContext.ensureActive()
        best?.takeIf { it.encounters.isNotEmpty() }
            ?: throw (lastError ?: CatchApiException("No complete walking route found within 30 seconds. Try a shorter session or a nearby start."))
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
        .sortedByDescending { it.opportunities.size / (1 + catchDistance(settings.start, it.point) / 1000) }
}

internal fun selectCandidates(groups: List<CatchAnchor>, start: CatchPoint, seed: Int): List<CatchAnchor> {
    val ordered = when (seed) {
        1 -> groups.sortedByDescending { it.opportunities.size / (1 + catchDistance(start, it.point) / 250) }
        2 -> groups.groupBy { floor((atan2(it.point.latitude - start.latitude, it.point.longitude - start.longitude) + PI) / (PI / 4)).toInt() }
            .values.toList().let { sectors -> (0 until (sectors.maxOfOrNull { it.size } ?: 0)).flatMap { i -> sectors.mapNotNull { it.getOrNull(i) } } }
        else -> groups
    }
    val result = mutableListOf<CatchAnchor>()
    for (g in ordered) {
        if (result.none { catchDistance(it.point, g.point) < 30 }) result += g
        if (result.size == 28) break
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

internal suspend fun catchBeamOrder(settings: CatchRouteSettings, anchors: List<CatchAnchor>, costs: List<List<Double?>>): List<Int> {
    data class State(val order: List<Int>, val covered: Set<String>, val evidence: Int, val meters: Double)
    val budget = settings.walkingBudgetMeters
    val finishIndex = if (settings.destination != null) anchors.size + 1 else null
    fun finishCost(s: State): Double? = finishIndex?.let { costs[s.order.lastOrNull()?.plus(1) ?: 0][it] } ?: if (finishIndex == null) 0.0 else null
    val comparator = compareByDescending<State> { it.covered.size }.thenByDescending { it.evidence }.thenBy { it.meters + (finishCost(it) ?: Double.POSITIVE_INFINITY) }
    var beam = listOf(State(emptyList(), emptySet(), 0, 0.0))
    var best: State? = beam.first().takeIf { finishCost(it)?.let { d -> d <= budget } == true }
    repeat(28) {
        coroutineContext.ensureActive()
        val next = mutableListOf<State>()
        for (s in beam) for (i in anchors.indices) {
            if (s.order.lastOrNull() == i) continue
            val distance = costs[s.order.lastOrNull()?.plus(1) ?: 0][i + 1] ?: continue
            if (distance <= 0.0 && s.order.contains(i)) continue
            val meters = s.meters + distance
            val returnMeters = if (finishIndex != null) costs[i + 1][finishIndex] ?: continue else 0.0
            if (meters + returnMeters > budget) continue
            val at = settings.startAtMillis + ceil(meters / settings.speedMps * 1000).toLong()
            val gained = anchors[i].opportunities.filter { it.id !in s.covered && at >= it.availableFrom && at < it.despawnAt }
            if (gained.isEmpty() && i in s.order) continue
            val n = State(s.order + i, s.covered + gained.map { it.id }, s.evidence + gained.sumOf { it.evidenceRank }, meters)
            next += n
            if (best == null || comparator.compare(n, best!!) < 0) best = n
        }
        beam = next.sortedWith(comparator).distinctBy { it.order.lastOrNull() to it.covered }.take(48)
        if (beam.isEmpty()) return best?.order.orEmpty()
    }
    return best?.order.orEmpty()
}

/** Bounded local improvement on the same directed matrix. Geometry still decides the final score. */
internal suspend fun improveCatchOrder(settings: CatchRouteSettings, anchors: List<CatchAnchor>, costs: List<List<Double?>>,
    initial: List<Int>): List<Int> {
    data class Score(val count: Int, val evidence: Int, val meters: Double)
    fun score(order: List<Int>): Score? {
        var previous = 0
        var meters = 0.0
        val covered = mutableSetOf<String>()
        var evidence = 0
        for (i in order) {
            meters += costs[previous][i + 1] ?: return null
            if (meters > settings.walkingBudgetMeters) return null
            val at = settings.startAtMillis + ceil(meters / settings.speedMps * 1000).toLong()
            for (o in anchors[i].opportunities) {
                if (at >= o.availableFrom && at < o.despawnAt && covered.add(o.id)) evidence += o.evidenceRank
            }
            previous = i + 1
        }
        if (settings.destination != null) meters += costs[previous][anchors.size + 1] ?: return null
        return if (meters <= settings.walkingBudgetMeters) Score(covered.size, evidence, meters) else null
    }
    val comparator = compareByDescending<Score> { it.count }.thenByDescending { it.evidence }.thenBy { it.meters }
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
        if (base.size < 28) for (anchor in anchors.indices) for (at in 0..base.size) {
            if (base.getOrNull(at - 1) != anchor && base.getOrNull(at) != anchor)
                consider(base.take(at) + anchor + base.drop(at))
        }
        if (best == base) return best
    }
    return best
}

internal fun betterCatchRoute(candidate: CatchItinerary, current: CatchItinerary?): Boolean = current == null ||
    candidate.encounters.size > current.encounters.size ||
    (candidate.encounters.size == current.encounters.size &&
        (candidate.encounters.sumOf { it.opportunity.evidenceRank } > current.encounters.sumOf { it.opportunity.evidenceRank } ||
        (candidate.encounters.sumOf { it.opportunity.evidenceRank } == current.encounters.sumOf { it.opportunity.evidenceRank } && candidate.distanceMeters < current.distanceMeters)))

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
