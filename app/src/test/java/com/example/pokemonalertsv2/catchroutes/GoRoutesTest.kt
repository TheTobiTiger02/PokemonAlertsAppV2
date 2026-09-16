package com.example.pokemonalertsv2.catchroutes

import com.example.pokemonalertsv2.data.RouteMatrixRequest
import com.example.pokemonalertsv2.data.RouteMatrixResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.time.Instant

class GoRoutesTest {
    private val now = Instant.parse("2026-09-12T12:00:00Z").toEpochMilli()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** [meters] east of the origin on the equator, so metres convert exactly. */
    private fun p(meters: Double, north: Double = 0.0) = CatchPoint(north / 111_195, meters / 111_195)

    private fun record(vararg points: CatchPoint, reversible: Int = 1) = GoRouteRecord(
        id = "route-1", name = "Abendrunde", reversible = reversible,
        geometry = points.map { listOf(it.latitude, it.longitude) },
    )

    @Test fun `route geometry is latitude first and a malformed pair rejects the whole route`() {
        val parsed = json.decodeFromString<GoRouteRecord>(
            """{"id":"x","name":"Schloss Runde","reversible":1,"geometry":[[49.733389,8.627441],[49.7401,8.6302]],"tags":["nature"]}""")
        assertEquals(listOf(CatchPoint(49.733389, 8.627441), CatchPoint(49.7401, 8.6302)), parsed.points())
        assertEquals(CatchPoint(49.7401, 8.6302), parsed.points(reverse = true).first())
        assertTrue(parsed.copy(geometry = listOf(listOf(49.7, 8.6), listOf(49.7))).points().isEmpty())
        assertTrue(parsed.copy(geometry = listOf(listOf(49.7, 8.6), listOf(95.0, 8.6))).points().isEmpty())
    }

    @Test fun `walking a route starts at its first point and lasts as long as the walk`() {
        val base = CatchRouteSettings(startAtMillis = now, speedMps = 1.0, area = listOf(p(0.0), p(10.0), p(0.0, 10.0)))
        val walking = base.walking(record(p(0.0), p(600.0), p(600.0, 600.0)), reverse = true)
        assertTrue(walking.fixed)
        assertEquals(p(600.0, 600.0), walking.start)
        assertEquals(CatchFinish.ANYWHERE, walking.finish)
        assertEquals(20, walking.durationMinutes)
        assertTrue("an area does not apply to a route walked as drawn", walking.area.isEmpty())
        walking.validate()
        // Moving the start away from the route is not a route walked as drawn any more.
        assertTrue(runCatching { walking.copy(start = p(100.0, 100.0)).validate() }.isFailure)
        val back = walking.withoutImport()
        assertFalse(back.fixed)
        assertNull(back.sourceRouteId)
    }

    @Test fun `older saved settings without the import fields still read`() {
        val old = """{"name":"Catch route","start":{"latitude":49.7,"longitude":8.6},"durationMinutes":60}"""
        val settings = json.decodeFromString<CatchRouteSettings>(old)
        assertFalse(settings.fixed)
        val walking = settings.copy(startAtMillis = now).walking(record(p(0.0), p(300.0)))
        assertEquals(walking, json.decodeFromString<CatchRouteSettings>(json.encodeToString(walking)))
    }

    @Test fun `a path is cut at the time budget on an interpolated point`() {
        val path = fixedCatchPath(listOf(p(0.0), p(400.0), p(1000.0)))
        assertEquals(1000.0, path.last().meters, 0.5)
        val cut = truncateCatchPath(path, 700.0)
        assertEquals(3, cut.size)
        assertEquals(700.0, cut.last().meters, 0.001)
        assertEquals(700.0, catchDistance(p(0.0), cut.last().point), 0.5)
        assertEquals(path, truncateCatchPath(path, 2000.0))
    }

    @Test fun `the remaining route starts at the trainer and does not jump back on a loop`() {
        // A loop: 300 m east, 300 m north, back west, back south to the start.
        val loop = listOf(p(0.0), p(300.0), p(300.0, 300.0), p(0.0, 300.0), p(0.0))
        val trainer = p(5.0, 150.0) // on the last leg, close to where the loop also began
        val remaining = remainingFixedPath(loop, trainer, walkedMeters = 1050.0)
        assertEquals(trainer, remaining.first())
        assertEquals(p(0.0), remaining.last())
        assertTrue("only the final leg is left", fixedCatchPath(remaining).last().meters < 200.0)
        val early = remainingFixedPath(loop, p(150.0, 3.0), walkedMeters = 0.0)
        assertTrue(fixedCatchPath(early).last().meters > 1000.0)
    }

    @Test fun `a fixed route scores spawns along it and zero encounters is still a route`() {
        val settings = CatchRouteSettings(start = p(0.0), startAtMillis = now, speedMps = 1.0, durationMinutes = 10,
            finish = CatchFinish.ANYWHERE, fixedPath = listOf(p(0.0), p(500.0)))
        val near = SpawnOpportunity("near", "near", p(200.0, 30.0), now, now + 3_600_000, "observed_encounter")
        val far = SpawnOpportunity("far", "far", p(200.0, 90.0), now, now + 3_600_000, "observed_encounter")
        val plan = planFixedRoute(settings, SpawnAvailability(listOf(near, far), "1", emptyList()))
        assertEquals(listOf("near"), plan.encounters.map { it.opportunity.id })
        assertEquals(500.0, plan.distanceMeters, 0.5)
        val empty = planFixedRoute(settings, SpawnAvailability(emptyList(), "1", emptyList()))
        assertTrue(empty.encounters.isEmpty())
        assertEquals(2, empty.path.size)
        // Longer than the session: cut to what ten minutes at 1 m/s reach, with a note.
        val short = planFixedRoute(settings.copy(fixedPath = listOf(p(0.0), p(900.0))), SpawnAvailability(emptyList(), "1", emptyList()))
        assertEquals(600.0, short.distanceMeters, 0.5)
        assertTrue(short.warnings.any { it.contains("ends before the route") })
    }

    @Test fun `planning a fixed route asks for spawn windows only, never for routing`() = runTest {
        val service = CountingService(windowsPage())
        val settings = CatchRouteSettings(start = p(0.0), startAtMillis = now, speedMps = 1.0, durationMinutes = 10,
            finish = CatchFinish.ANYWHERE, fixedPath = listOf(p(0.0), p(300.0)))
        val plan = CatchRoutePlanner(service).generate(settings)
        assertEquals(0, service.routingCalls)
        assertEquals(1, service.windowCalls)
        assertEquals(listOf("one"), plan.encounters.map { it.opportunity.pointId })
    }

    @Test fun `a guide area contains the whole route with room around it`() {
        val u = listOf(p(0.0), p(0.0, 800.0), p(400.0, 800.0), p(400.0), p(800.0), p(800.0, 800.0))
        val area = bufferedRouteArea(u)
        assertTrue(area.isArea())
        for (point in fixedCatchPath(u).let { path -> (0..path.last().meters.toInt() step 20).map { catchPathPointAt(path, it.toDouble()) } }) {
            assertTrue("route point $point inside", pointInArea(point, area))
        }
        assertTrue("buffered sideways", pointInArea(p(-140.0, 400.0), area))
        assertFalse("but not far away", pointInArea(p(-300.0, 400.0), area))
        val guided = CatchRouteSettings(startAtMillis = now).guidedBy(record(*u.toTypedArray()))
        assertFalse(guided.fixed)
        assertEquals(p(0.0), guided.start)
        guided.validate()
    }

    @Test fun `the repository turns a missing route into a message and caches details`() = runTest {
        val routes = object : GoRoutesService {
            var detailCalls = 0
            override suspend fun routes(query: Map<String, String>) = Response.success(GoRouteList(2, listOf(
                GoRouteSummary("far", startLatitude = 0.0, startLongitude = 0.01),
                GoRouteSummary("near", startLatitude = 0.0, startLongitude = 0.001),
            )))
            override suspend fun route(id: String): Response<GoRouteRecord> {
                detailCalls++
                return if (id == "gone") Response.error(404, """{"code":"ROUTE_NOT_FOUND"}""".toResponseBody())
                else Response.success(record(p(0.0), p(100.0)).copy(id = id))
            }
        }
        val repository = GoRouteRepository(routes)
        assertEquals(listOf("near", "far"), repository.nearby(p(0.0)).map { it.id })
        repository.detail("a"); repository.detail("a")
        assertEquals(1, routes.detailCalls)
        assertEquals("That route is no longer published.", runCatching { repository.detail("gone") }.exceptionOrNull()?.message)
    }

    private fun windowsPage(): JsonObject = Json.parseToJsonElement(
        """{"dataVersion":"1","truncated":false,"nextCursor":null,"sources":[{"source":"wingull","complete":true}],"data":[""" +
            """{"id":"one","source":"wingull","latitude":0.0001,"longitude":0.0009,"windows":[{"opportunityId":"one@1","availableFrom":"2026-09-12T12:00:00Z","despawnAt":"2026-09-12T13:00:00Z","basis":"observed_encounter"}]}""" +
            "]}").jsonObject

    private class CountingService(private val page: JsonObject) : CatchRoutesService {
        var routingCalls = 0
        var windowCalls = 0
        override suspend fun windows(query: Map<String, String>): Response<JsonObject> { windowCalls++; return Response.success(page) }
        override suspend fun catalogue(query: Map<String, String>, etag: String?): Response<JsonObject> = error("not used")
        override suspend fun spawnpoint(id: String): Response<JsonObject> = error("not used")
        override suspend fun matrix(request: RouteMatrixRequest): Response<RouteMatrixResponse> { routingCalls++; error("no routing for a fixed route") }
        override suspend fun path(request: RouteMatrixRequest): Response<CatchPathResponse> { routingCalls++; error("no routing for a fixed route") }
    }
}
