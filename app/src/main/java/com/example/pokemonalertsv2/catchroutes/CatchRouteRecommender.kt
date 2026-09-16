package com.example.pokemonalertsv2.catchroutes

import com.example.pokemonalertsv2.util.WalkingRouteUtils
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import kotlin.math.*

/** A recommended start: the route planned from it, and how the next-best candidates compared. */
data class CatchRecommendation(
    val settings: CatchRouteSettings,
    val itinerary: CatchItinerary,
    /** Runner-up candidates with their estimated expected catches, best first. */
    val alternatives: List<Pair<CatchRouteSettings, Double>>,
    /** How many start spots or departures were compared. */
    val compared: Int = alternatives.size + 1,
)

/**
 * Finds where or when to start a catch route for the most expected catches.
 *
 * Spawn windows are downloaded once for all candidates. Every candidate is estimated offline on
 * straight-line walking costs (× [WalkingRouteUtils.DETOUR_FACTOR]) with the planner's own order search,
 * so ranking costs no routing requests; only the best [REAL_PLANS] are planned for real on streets.
 */
class CatchRouteRecommender(private val service: CatchRoutesService) {
    private val availability = SpawnAvailabilityRepository(service)
    private val planner = CatchRoutePlanner(service)

    /** Best start position for a departure at `settings.startAtMillis`: inside the area, else within [searchRadiusMeters] of `settings.start`. */
    suspend fun recommendStart(settings: CatchRouteSettings, searchRadiusMeters: Double = 2_000.0,
        progress: (String) -> Unit = {}): CatchRecommendation = withContext(Dispatchers.Default) {
        val around = if (settings.area.isArea()) settings.copy(start = areaInteriorPoint(settings.area)) else settings
        val radius = if (settings.area.isArea()) settings.area.maxOf { catchDistance(it, around.start) } else searchRadiusMeters
        progress("Loading spawn windows")
        val data = availability.load(around, progress, searchRadiusMeters = radius)
        val candidates = startCandidates(around, data.opportunities, radius)
        if (candidates.isEmpty()) throw CatchApiException("No spawn windows nearby to start from. Try another time or area.")
        val ranked = candidates.mapIndexed { i, start ->
            coroutineContext.ensureActive()
            progress("Comparing starts ${i + 1} of ${candidates.size}")
            val candidate = settings.copy(start = start, end = if (settings.finish == CatchFinish.PIN) settings.end else null)
            candidate to estimateExpectedCatches(candidate, data)
        }.sortedByDescending { it.second }
        planBest(ranked, data, progress)
    }

    /** Best departure within [horizonMillis] from `settings.startAtMillis` for the start as set, in [stepMinutes] steps. */
    suspend fun recommendTime(settings: CatchRouteSettings, horizonMillis: Long = 6 * 3_600_000L, stepMinutes: Int = 15,
        progress: (String) -> Unit = {}): CatchRecommendation = withContext(Dispatchers.Default) {
        val until = settings.startAtMillis + horizonMillis
        val lastDeparture = until - settings.durationMinutes * 60_000L
        if (lastDeparture < settings.startAtMillis) throw CatchApiException("The route is longer than the time range to search.")
        progress("Loading spawn windows for the next ${horizonMillis / 3_600_000} hours")
        val data = availability.load(settings, progress, untilMillis = until)
        val departures = generateSequence(settings.startAtMillis) { it + stepMinutes * 60_000L }.takeWhile { it <= lastDeparture }.toList()
        val ranked = departures.mapIndexed { i, at ->
            coroutineContext.ensureActive()
            progress("Comparing departures ${i + 1} of ${departures.size}")
            val candidate = settings.copy(startAtMillis = at)
            candidate to estimateExpectedCatches(candidate, data)
        }.sortedByDescending { it.second }
        planBest(ranked, data, progress)
    }

    private suspend fun planBest(ranked: List<Pair<CatchRouteSettings, Double>>, data: SpawnAvailability,
        progress: (String) -> Unit): CatchRecommendation {
        var best: Pair<CatchRouteSettings, CatchItinerary>? = null
        var lastError: Exception? = null
        for ((i, candidate) in ranked.filter { it.second > 0 }.take(REAL_PLANS).withIndex()) {
            coroutineContext.ensureActive()
            try {
                val itinerary = planner.generate(candidate.first, progress = { progress("Planning option ${i + 1}: $it") }, preloaded = data)
                if (best == null || betterCatchRoute(itinerary, best.second)) best = candidate.first to itinerary
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { lastError = e; if (e is CatchApiException && e.retryAtMillis > 0) break }
        }
        val chosen = best ?: throw (lastError ?: CatchApiException("No usable route from any candidate. Try another time or area."))
        return CatchRecommendation(chosen.first, chosen.second, ranked.filter { it.first != chosen.first }.take(3), ranked.size)
    }

    companion object {
        /** Routed plans per recommendation; each costs up to eight routing requests. */
        const val REAL_PLANS = 2
        const val START_CANDIDATES = 12
    }
}

/**
 * Start positions worth comparing: the densest spawn clusters (by expected catches within range) at
 * least 150 m apart, inside the area and the search radius, plus the current start.
 */
internal fun startCandidates(settings: CatchRouteSettings, opportunities: List<SpawnOpportunity>, searchRadiusMeters: Double,
    limit: Int = CatchRouteRecommender.START_CANDIDATES): List<CatchPoint> {
    val inReach = opportunities.filter { catchDistance(it.point, settings.start) <= searchRadiusMeters && pointInArea(it.point, settings.area) }
    val index = CatchSpatialIndex(inReach) { it.point }
    // Value of standing at a spawnpoint: what a short walk around it can catch in this session.
    val scored = inReach.distinctBy { it.pointId }.map { o ->
        o.point to index.near(o.point, radius = 250.0).filter { catchDistance(it.point, o.point) <= 250.0 }.sumOf { it.expectedCatch }
    }.sortedByDescending { it.second }
    val picked = mutableListOf<CatchPoint>()
    if (pointInArea(settings.start, settings.area)) picked += settings.start
    for ((point, _) in scored) {
        if (picked.none { catchDistance(it, point) < 150 }) picked += point
        if (picked.size >= limit) break
    }
    return picked
}

/** The planner's own order search on straight-line costs: a routing-free estimate of expected catches. */
internal suspend fun estimateExpectedCatches(settings: CatchRouteSettings, data: SpawnAvailability): Double {
    if (settings.fixed) return planFixedRoute(settings, data.forRoute(settings)).expectedCatches
    val usable = data.opportunities.filter { usableFor(settings, it) }
    if (usable.isEmpty()) return 0.0
    val anchors = selectCandidates(candidateGroups(settings, usable), settings.start, 0)
    if (anchors.isEmpty()) return 0.0
    val points = listOf(settings.start) + anchors.map { it.point } + listOfNotNull(settings.destination)
    val costs = points.map { a -> points.map { b -> catchDistance(a, b) * WalkingRouteUtils.DETOUR_FACTOR } }
    val legs = CatchLegCoverage(settings, points, usable)
    // A narrower beam than the real planner: this only ranks candidates, and runs once per candidate.
    val order = improveCatchOrder(settings, anchors, costs, catchBeamOrder(settings, anchors, costs, width = 24, legs = legs), legs)
    return scoreCatchOrder(settings, anchors, costs, order, legs)?.expected ?: 0.0
}

/** A point inside the polygon: its centroid when that is inside, else the middle of a diagonal that is. */
internal fun areaInteriorPoint(area: List<CatchPoint>): CatchPoint {
    val centroid = CatchPoint(area.map { it.latitude }.average(), area.map { it.longitude }.average())
    if (pointInArea(centroid, area)) return centroid
    for (i in area.indices) for (j in i + 2 until area.size) {
        val mid = CatchPoint((area[i].latitude + area[j].latitude) / 2, (area[i].longitude + area[j].longitude) / 2)
        if (pointInArea(mid, area)) return mid
    }
    return area.first()
}
