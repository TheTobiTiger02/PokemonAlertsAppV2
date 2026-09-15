package com.example.pokemonalertsv2.catchroutes

import com.example.pokemonalertsv2.data.RouteMatrixRequest
import com.example.pokemonalertsv2.data.RouteMatrixResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.time.Instant

class CatchAreaRecommendTest {
    private val now = Instant.parse("2026-09-12T12:00:00Z").toEpochMilli()
    /** A point [x] metres east and [y] metres north of the origin near Darmstadt. */
    private fun p(x: Double, y: Double = 0.0) = CatchPoint(49.87 + y / 111_195, 8.65 + x / (111_195 * kotlin.math.cos(Math.toRadians(49.87))))
    private val square = listOf(p(-100.0, -100.0), p(100.0, -100.0), p(100.0, 100.0), p(-100.0, 100.0))

    @Test fun `polygon containment, edge distance and path tolerance`() {
        assertTrue(pointInArea(p(0.0), square))
        assertFalse(pointInArea(p(150.0), square))
        assertTrue("no area allows everything", pointInArea(p(5000.0), emptyList()))
        // A U shape: the gap between the arms is outside.
        val u = listOf(p(-100.0, -100.0), p(100.0, -100.0), p(100.0, 100.0), p(50.0, 100.0), p(50.0, -50.0), p(-50.0, -50.0), p(-50.0, 100.0), p(-100.0, 100.0))
        assertTrue(pointInArea(p(-75.0, 50.0), u))
        assertFalse(pointInArea(p(0.0, 50.0), u))
        assertEquals(50.0, distanceToAreaEdge(p(150.0), square), 1.0)
        assertTrue(pathInsideArea(listOf(CatchPathPosition(p(0.0), 0.0), CatchPathPosition(p(110.0), 110.0)), square, toleranceMeters = 20.0))
        assertFalse(pathInsideArea(listOf(CatchPathPosition(p(0.0), 0.0), CatchPathPosition(p(140.0), 140.0)), square, toleranceMeters = 20.0))
        assertTrue(pointInArea(areaInteriorPoint(u), u))
    }

    @Test fun `event windows and windows outside the area are not used unless allowed`() {
        val settings = CatchRouteSettings(start = p(0.0), startAtMillis = now, durationMinutes = 30, finish = CatchFinish.ANYWHERE)
        val regular = SpawnOpportunity("r", "r", p(50.0), now, now + 1_800_000, "recurring_schedule")
        val event = regular.copy(id = "e", pointId = "e", activityPattern = "event_only")
        val flagged = regular.copy(id = "f", pointId = "f", uncertainty = listOf("event_only_spawnpoint"))
        val outside = regular.copy(id = "o", pointId = "o", point = p(300.0))
        assertEquals(listOf("r", "o"), listOf(regular, event, flagged, outside).filter { usableFor(settings, it) }.map { it.id })
        assertEquals(listOf("r", "e", "f", "o"), listOf(regular, event, flagged, outside).filter { usableFor(settings.copy(includeEventSpawns = true), it) }.map { it.id })
        assertEquals(listOf("r"), listOf(regular, event, flagged, outside).filter { usableFor(settings.copy(area = square), it) }.map { it.id })
    }

    @Test fun `settings need the start inside the area and old saved settings still read`() {
        val base = CatchRouteSettings(start = p(0.0), startAtMillis = now)
        base.copy(area = square).validate()
        assertThrows(IllegalArgumentException::class.java) { base.copy(start = p(500.0), area = square).validate() }
        assertThrows(IllegalArgumentException::class.java) { base.copy(area = square.take(2)).validate() }
        val old = Json { ignoreUnknownKeys = true }.decodeFromString(CatchRouteSettings.serializer(), """{"name":"Old","start":{"latitude":49.87,"longitude":8.65}}""")
        assertFalse(old.includeEventSpawns)
        assertTrue(old.area.isEmpty())
    }

    @Test fun `generated routes stay inside the area`() = runTest {
        // Spawns in both arms of a U; the straight walk between the arms would cross the gap.
        val u = listOf(p(-200.0, -120.0), p(200.0, -120.0), p(200.0, 200.0), p(120.0, 200.0), p(120.0, -40.0), p(-120.0, -40.0), p(-120.0, 200.0), p(-200.0, 200.0))
        val service = Fake(listOf(row("west", p(-160.0, 150.0)), row("east", p(160.0, 150.0)), row("south", p(0.0, -80.0))))
        val settings = CatchRouteSettings(start = p(0.0, -80.0), startAtMillis = now, durationMinutes = 20, speedMps = 1.36,
            finish = CatchFinish.ANYWHERE, area = u)
        val route = runCatching { CatchRoutePlanner(service).generate(settings) }
        route.onSuccess { assertTrue(pathInsideArea(it.path, u)) }
            .onFailure { assertTrue(it.message.orEmpty(), it.message.orEmpty().contains("area") || it.message.orEmpty().contains("route")) }
        assertTrue("the windows query stays within the area's box", service.queries.all { q ->
            q.getValue("east").toDouble() <= u.maxOf { it.longitude } + 1e-9 && q.getValue("north").toDouble() <= u.maxOf { it.latitude } + 1e-9 })
    }

    @Test fun `recommended start moves to where the spawns are`() = runTest {
        val cluster = (0 until 6).map { i -> row("c$i", p(900.0 + i * 15.0, 10.0 * i)) }
        val service = Fake(cluster + row("lonely", p(0.0, 400.0)))
        val settings = CatchRouteSettings(start = p(0.0), startAtMillis = now, durationMinutes = 20, speedMps = 1.36, finish = CatchFinish.ANYWHERE)
        val found = CatchRouteRecommender(service).recommendStart(settings, searchRadiusMeters = 1_500.0)
        assertTrue("start near the cluster: ${catchDistance(found.settings.start, p(950.0))}", catchDistance(found.settings.start, p(950.0)) < 200)
        assertTrue(found.itinerary.encounters.size >= 5)
        assertEquals("one windows download for all candidates", 1, service.queries.size)
        assertTrue("only the best few are routed", service.pathCalls <= CatchRouteRecommender.REAL_PLANS * 8)
    }

    @Test fun `recommended time waits for spawns that start later`() = runTest {
        val later = now + 90 * 60_000L
        val service = Fake((0 until 5).map { i -> row("l$i", p(40.0 * i), from = later, until = later + 30 * 60_000L) })
        val settings = CatchRouteSettings(start = p(0.0), startAtMillis = now, durationMinutes = 20, speedMps = 1.36, finish = CatchFinish.ANYWHERE)
        val found = CatchRouteRecommender(service).recommendTime(settings, horizonMillis = 3 * 3_600_000L)
        assertTrue("departure ${(found.settings.startAtMillis - now) / 60_000} min", found.settings.startAtMillis in (later - 20 * 60_000L)..(later + 20 * 60_000L))
        assertTrue(found.itinerary.encounters.isNotEmpty())
        assertEquals(Instant.ofEpochMilli(now + 3 * 3_600_000L).toString(), service.queries.single().getValue("to"))
    }

    private fun row(id: String, at: CatchPoint, from: Long = now, until: Long = now + 3_600_000L) =
        """{"id":"$id","source":"wingull","latitude":${at.latitude},"longitude":${at.longitude},"windows":[{"opportunityId":"$id@1","availableFrom":"${Instant.ofEpochMilli(from)}","despawnAt":"${Instant.ofEpochMilli(until)}","basis":"recurring_schedule","probability":1.0}]}"""

    private class Fake(rows: List<String>) : CatchRoutesService {
        private val body = Json.parseToJsonElement("""{"dataVersion":"1","truncated":false,"nextCursor":null,"sources":[{"source":"wingull","complete":true}],"data":[${rows.joinToString(",")}]}""").jsonObject
        val queries = mutableListOf<Map<String, String>>()
        var pathCalls = 0
        override suspend fun windows(query: Map<String, String>): Response<JsonObject> { queries += query; return Response.success(body) }
        override suspend fun catalogue(query: Map<String, String>, etag: String?): Response<JsonObject> = error("not used")
        override suspend fun spawnpoint(id: String): Response<JsonObject> = error("not used")
        override suspend fun matrix(request: RouteMatrixRequest): Response<RouteMatrixResponse> {
            pathCalls++
            val costs = request.points.map { a -> request.points.map { b -> catchDistance(CatchPoint(a.latitude, a.longitude), CatchPoint(b.latitude, b.longitude)).toInt() } }
            return Response.success(RouteMatrixResponse(ids = request.points.map { it.id }, distanceMeters = costs, durationSeconds = costs))
        }
        override suspend fun path(request: RouteMatrixRequest): Response<CatchPathResponse> {
            pathCalls++
            return Response.success(CatchPathResponse("ok", "fixture", "2026-09-12T12:00:00Z", request.points.map { CatchSnappedPoint(it.id, it.latitude, it.longitude) },
                request.points.zipWithNext().map { (a, b) ->
                    val d = catchDistance(CatchPoint(a.latitude, a.longitude), CatchPoint(b.latitude, b.longitude))
                    CatchPathLeg(a.id, b.id, d, d, CatchPathGeometry("LineString", listOf(listOf(a.longitude, a.latitude), listOf(b.longitude, b.latitude))))
                }))
        }
    }
}
