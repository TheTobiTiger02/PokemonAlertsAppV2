package com.example.pokemonalertsv2.catchroutes

import com.example.pokemonalertsv2.data.RouteMatrixRequest
import com.example.pokemonalertsv2.data.RouteMatrixResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Response
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * How close generated catch routes get to the best route the data allows.
 *
 * Opt-in and offline: `-Pcatch.bench.dir=<dir>` with recorded `/api/spawnpoints/windows` responses
 * (`<scenario>.json`: `{latitude, longitude, startAtMillis, pages:[...]}`). Streets are modelled as
 * straight lines × [DETOUR] for both the matrix and the path, so no routing endpoint is called and
 * runs are repeatable. For every scenario it runs the real [CatchRoutePlanner] and a slow reference
 * search (many more anchors, a much wider beam, many seeds) on the same model, and prints the gap.
 */
class CatchRouteBenchmarkTest {
    @Test fun benchmark() = runBlocking {
        val dir = System.getProperty("catch.bench.dir")?.let(::File)
        assumeTrue("set -Pcatch.bench.dir to run", dir != null && dir.isDirectory)
        val rows = mutableListOf<String>()
        rows += "scenario | min | finish | planner expected | encounters | km | requests | ms | reference expected | gap"
        for (file in dir!!.listFiles { f -> f.name.endsWith(".json") }!!.sorted()) {
            val fixture = Json.parseToJsonElement(file.readText()).jsonObject
            val start = CatchPoint(fixture.getValue("latitude").jsonPrimitive.double, fixture.getValue("longitude").jsonPrimitive.double)
            val startAt = fixture.getValue("startAtMillis").jsonPrimitive.long
            val all = fixture.getValue("pages").jsonArray.flatMap { page -> page.jsonObject.getValue("data").jsonArray }
                .flatMap { parseSpawnWindows(it.jsonObject).orEmpty() }
            for (minutes in listOf(30, 60, 120)) for (finish in listOf(CatchFinish.ROUND_TRIP, CatchFinish.ANYWHERE)) {
                val settings = CatchRouteSettings(start = start, startAtMillis = startAt, durationMinutes = minutes, finish = finish)
                val data = SpawnAvailability(all.filter { usableFor(settings, it) }, "bench", emptyList())
                val service = StraightLineService()
                var plan: CatchItinerary? = null
                var failure: String? = null
                val ms = measureTimeMillis { plan = runCatching { CatchRoutePlanner(service).generate(settings, preloaded = data) }
                    .onFailure { failure = it.message }.getOrNull() }
                failure?.let { println("${file.nameWithoutExtension} $minutes $finish failed: $it") }
                val reference = referenceSearch(settings, data.opportunities)
                val got = plan?.expectedCatches ?: 0.0
                rows += "${file.nameWithoutExtension} | $minutes | $finish | ${"%.2f".format(got)} | ${plan?.encounters?.size ?: 0} | " +
                    "${"%.2f".format((plan?.distanceMeters ?: 0.0) / 1000)} | ${service.requests} | $ms | ${"%.2f".format(reference)} | " +
                    (if (reference > 0) "${"%.1f".format((reference - got) / reference * 100)}%" else "-")
            }
        }
        val report = rows.joinToString("\n")
        println(report)
        File(dir, "report-${System.getProperty("catch.bench.label") ?: "run"}.txt").writeText(report)
    }

    /** Slow upper-bound estimate: 5 anchor orderings × 60 anchors × beam 300, scored on the same straight-line paths. */
    private suspend fun referenceSearch(settings: CatchRouteSettings, opportunities: List<SpawnOpportunity>): Double {
        if (opportunities.isEmpty()) return 0.0
        val groups = candidateGroups(settings, opportunities)
        var best = 0.0
        for (seed in 0..4) {
            val ordered = when (seed) { in 0..2 -> selectCandidatesPool(groups, settings.start, seed, 60); 3 -> groups.take(60)
                else -> groups.sortedByDescending { it.opportunities.sumOf { o -> o.expectedCatch } }.take(60) }
            if (ordered.isEmpty()) continue
            val points = listOf(settings.start) + ordered.map { it.point } + listOfNotNull(settings.destination)
            val costs = points.map { a -> points.map { b -> catchDistance(a, b) * DETOUR } }
            val legs = CatchLegCoverage(settings, points, opportunities)
            val order = improveCatchOrder(settings, ordered, costs, catchBeamOrder(settings, ordered, costs, width = 300, depth = ordered.size, legs = legs), legs)
            val stops = listOf(settings.start) + order.map { ordered[it].point } + listOfNotNull(settings.destination)
            var meters = 0.0
            val path = stops.mapIndexed { i, p -> if (i > 0) meters += catchDistance(stops[i - 1], p) * DETOUR; CatchPathPosition(p, meters) }
            if (meters > settings.walkingBudgetMeters) continue
            val stopMeters = path.drop(1).take(order.size).map { it.point to it.meters }
            val waits = planCatchWaits(settings, stopMeters, opportunities)
            best = maxOf(best, scoreCatchPath(path, settings, opportunities, waits).sumOf { it.opportunity.expectedCatch })
        }
        return best
    }

    private fun selectCandidatesPool(groups: List<CatchAnchor>, start: CatchPoint, seed: Int, size: Int): List<CatchAnchor> {
        val picked = mutableListOf<CatchAnchor>()
        val ordered = selectCandidates(groups, start, seed).map { it.point }.toSet()
        for (g in groups.sortedByDescending { if (it.point in ordered) 1 else 0 }) {
            if (picked.none { catchDistance(it.point, g.point) < 30 }) picked += g
            if (picked.size == size) break
        }
        return picked
    }

    private class StraightLineService : CatchRoutesService {
        var requests = 0
        override suspend fun windows(query: Map<String, String>): Response<JsonObject> = error("preloaded")
        override suspend fun catalogue(query: Map<String, String>, etag: String?): Response<JsonObject> = error("not used")
        override suspend fun spawnpoint(id: String): Response<JsonObject> = error("not used")
        override suspend fun matrix(request: RouteMatrixRequest): Response<RouteMatrixResponse> {
            requests++
            val costs = request.points.map { a -> request.points.map { b -> (catchDistance(CatchPoint(a.latitude, a.longitude), CatchPoint(b.latitude, b.longitude)) * DETOUR).toInt() } }
            return Response.success(RouteMatrixResponse(ids = request.points.map { it.id }, distanceMeters = costs, durationSeconds = costs))
        }
        override suspend fun path(request: RouteMatrixRequest): Response<CatchPathResponse> {
            requests++
            return Response.success(CatchPathResponse("ok", "bench", "2026-09-15T00:00:00Z", request.points.map { CatchSnappedPoint(it.id, it.latitude, it.longitude) },
                request.points.zipWithNext().map { (a, b) ->
                    val d = catchDistance(CatchPoint(a.latitude, a.longitude), CatchPoint(b.latitude, b.longitude)) * DETOUR
                    CatchPathLeg(a.id, b.id, d, d, CatchPathGeometry("LineString", listOf(listOf(a.longitude, a.latitude), listOf(b.longitude, b.latitude))))
                }))
        }
    }

    companion object { const val DETOUR = 1.25 }
}
