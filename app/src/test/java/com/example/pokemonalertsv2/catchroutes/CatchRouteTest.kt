package com.example.pokemonalertsv2.catchroutes

import com.example.pokemonalertsv2.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.Headers
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.time.Instant

class CatchRouteTest {
    private val now = Instant.parse("2026-09-12T12:00:00Z").toEpochMilli()
    private fun p(meters: Double) = CatchPoint(0.0, meters / 111_195)
    private fun settings() = CatchRouteSettings(start = p(0.0), startAtMillis = now, speedMps = 1.0, finish = CatchFinish.ANYWHERE, durationMinutes = 10)
    private fun opportunity(id: String = "one", meters: Double = 100.0, from: Long = now, until: Long = now + 300_000) =
        SpawnOpportunity(id, "point-$id", p(meters), from, until, "assumed_duration")
    private fun path(vararg meters: Double) = meters.map { CatchPathPosition(p(it), it) }
    private fun row(id: String) = """{"id":"$id","source":"wingull","latitude":0.0,"longitude":0.0009,"windows":[{"opportunityId":"$id@1","availableFrom":"2026-09-12T12:00:00Z","despawnAt":"2026-09-12T13:00:00Z","basis":"assumed_duration","uncertainty":["assumed_spawn_duration"]}]}"""
    private fun page(ids: List<String>, cursor: String? = null, version: String = "1"): JsonObject = Json.parseToJsonElement(
        """{"dataVersion":"$version","truncated":${cursor != null},"nextCursor":${cursor?.let { "\"$it\"" } ?: "null"},"sources":[{"source":"pogomapper","complete":true}],"data":[${ids.joinToString(",") { row(it) }}]}""").jsonObject

    @Test fun `counts overlapping circles and incidental opportunities once`() {
        val o = opportunity()
        val encounters = scoreCatchPath(path(0.0, 100.0, 200.0), settings(), listOf(o, opportunity("two", 120.0)))
        assertEquals(2, encounters.size)
        assertEquals(now + 60_000, encounters.first().arrivalMillis)
        assertEquals(2, encounters.map { it.opportunity.id }.distinct().size)
    }
    @Test fun `future spawn counts only while still inside circle and expiry is exclusive`() {
        val a = opportunity(from = now + 120_000)
        assertEquals(now + 120_000, scoreCatchPath(path(0.0, 200.0), settings(), listOf(a)).single().arrivalMillis)
        assertTrue(scoreCatchPath(path(0.0, 200.0), settings(), listOf(a.copy(availableFrom = now + 141_000))).isEmpty())
        assertTrue(scoreCatchPath(path(0.0, 200.0), settings(), listOf(a.copy(availableFrom = now, despawnAt = now + 60_000))).isEmpty())
    }
    @Test fun `80 meter range changes encounter time without subtracting distances`() {
        val regular = scoreCatchPath(path(0.0, 200.0), settings(), listOf(opportunity())).single()
        val rend = scoreCatchPath(path(0.0, 200.0), settings().copy(spacialRend = true), listOf(opportunity())).single()
        assertEquals(40_000L, regular.arrivalMillis - rend.arrivalMillis)
    }
    @Test fun `actual detour geometry cannot catch a spawn across an unwalked shortcut`() {
        val route = listOf(CatchPathPosition(p(0.0), 0.0), CatchPathPosition(CatchPoint(0.002, 0.0), 222.0), CatchPathPosition(CatchPoint(0.002, 0.002), 444.0))
        assertTrue(scoreCatchPath(route, settings(), listOf(opportunity(meters = 150.0))).isEmpty())
    }
    @Test fun `later hourly cycles are separate opportunities`() {
        val first = opportunity(until = now + 120_000)
        val second = first.copy(id = "second", availableFrom = now + 3_600_000, despawnAt = now + 3_900_000)
        val route = listOf(CatchPathPosition(p(0.0), 0.0), CatchPathPosition(p(100.0), 100.0), CatchPathPosition(p(100.0), 3700.0))
        assertEquals(2, scoreCatchPath(route, settings().copy(durationMinutes = 120), listOf(first, second)).size)
        assertFalse(sameCycle(first, second, now + 100_000))
        assertTrue(sameCycle(first, first.copy(id = "corrected", despawnAt = first.despawnAt + 10_000), now + 100_000))
    }
    @Test fun `one second fixes confirm visit after dwell without recording catches`() {
        val plan = CatchItinerary(settings(), path(0.0, 200.0), listOf(CatchEncounter(opportunity(), now + 100_000, 100.0)), emptyList())
        val progress = CatchRouteProgress()
        var session = CatchSession(plan)
        session = progress.accept(session, p(100.0), 5.0, now, now)
        session = progress.accept(session, p(100.0), 5.0, now + 1000, now + 1000)
        assertTrue(session.visits.isEmpty())
        session = progress.accept(session, p(100.0), 5.0, now + 2000, now + 2000)
        assertEquals(1, session.visits.size)
        assertEquals(0, session.caught)
    }
    @Test fun `GPS gaps paused sessions stale fixes and poor accuracy cannot consume opportunities`() {
        val plan = CatchItinerary(settings(), path(0.0, 200.0), listOf(CatchEncounter(opportunity(), now + 100_000, 100.0)), emptyList())
        val progress = CatchRouteProgress()
        val session = CatchSession(plan)
        progress.accept(session, p(100.0), 5.0, now, now)
        assertTrue(progress.accept(session, p(100.0), 5.0, now + 20_000, now + 20_000).visits.isEmpty())
        assertTrue(progress.accept(session.copy(paused = true), p(100.0), 5.0, now + 23_000, now + 23_000).visits.isEmpty())
        assertTrue(progress.accept(session, p(100.0), 70.0, now + 23_000, now + 23_000).visits.isEmpty())
        assertTrue(progress.accept(session, p(100.0), 5.0, now + 23_000, now + 60_000).visits.isEmpty())
    }
    @Test fun `pagination atomically restarts and retains missing source warnings`() = runTest {
        val fake = Fake()
        fake.pages += Response.success(page(listOf("old"), "next"))
        fake.pages += Response.error(409, "{}".toResponseBody())
        fake.pages += Response.success(page(listOf("new"), "next2", "2"))
        fake.pages += Response.success(page(listOf("last"), version = "2"))
        val result = SpawnAvailabilityRepository(fake).load(settings())
        assertEquals(setOf("new", "last"), result.opportunities.map { it.pointId }.toSet())
        assertEquals("2", result.version)
        assertTrue(result.warnings.any { it.contains("wingull") && it.contains("unknown") })
        assertEquals(null, fake.queries[2]["cursor"])
        assertEquals(fake.queries[0]["from"], fake.queries[3]["from"])
        assertFalse(fake.queries.any { "source" in it })
    }
    @Test fun `three revision failures stop instead of looping`() = runTest {
        val fake = Fake()
        repeat(3) { fake.pages += Response.error(409, "{}".toResponseBody()) }
        assertTrue(runCatching { SpawnAvailabilityRepository(fake).load(settings()) }.exceptionOrNull() is CatchApiException)
        assertEquals(3, fake.queries.size)
    }
    @Test fun `legacy envelope and repeated cursors rejected`() = runTest {
        assertTrue(runCatching { validateEnvelope(Json.parseToJsonElement("""{"count":1,"data":[],"truncated":false}""").jsonObject) }.isFailure)
        val fake = Fake()
        repeat(2) { fake.pages += Response.success(page(listOf("same"), "same")) }
        assertTrue(runCatching { SpawnAvailabilityRepository(fake).load(settings()) }.exceptionOrNull() is CatchApiException)
    }
    @Test fun `catalogue etags bind full query and windows do not enter cache`() = runTest {
        val fake = Fake()
        val p = page(listOf("one"))
        fake.catalogues += Response.success(p, Headers.headersOf("ETag", "\"a\""))
        val unchanged = okhttp3.Response.Builder().code(304).protocol(okhttp3.Protocol.HTTP_1_1).message("Not modified")
            .request(okhttp3.Request.Builder().url("https://example.com").build()).build()
        fake.catalogues += Response.error("".toResponseBody(), unchanged)
        fake.catalogues += Response.success(page(listOf("other")))
        val repository = SpawnAvailabilityRepository(fake)
        assertEquals(p, repository.catalogue(mapOf("limit" to "1")))
        assertEquals(p, repository.catalogue(mapOf("limit" to "1")))
        repository.catalogue(mapOf("limit" to "2"))
        assertEquals(listOf(null, "\"a\"", null), fake.etags)
    }
    @Test fun `429 carries retry deadline`() {
        val raw = okhttp3.Response.Builder().code(429).protocol(okhttp3.Protocol.HTTP_1_1).message("busy")
            .request(okhttp3.Request.Builder().url("https://example.com").build()).header("Retry-After", "42").build()
        val error = runCatching { Response.error<JsonObject>("{}".toResponseBody(), raw).catchBody(now) }.exceptionOrNull() as CatchApiException
        assertEquals(now + 42_000, error.retryAtMillis)
    }
    @Test fun `window parse preserves absolute DST timestamps ambiguity and duration evidence`() {
        val point = Json.parseToJsonElement(row("x")).jsonObject
        val ambiguous = JsonObject(point + ("associationAmbiguous" to JsonPrimitive(true)))
        assertTrue(parseSpawnWindows(ambiguous)!!.isEmpty())
        val malformed = JsonObject(point + ("latitude" to JsonPrimitive(200)))
        assertNull(parseSpawnWindows(malformed))
        val window = Json.parseToJsonElement("""{"id":"dst","latitude":49.0,"longitude":8.0,"observedDurationLowerBoundSeconds":2400,"windows":[{"opportunityId":"dst@1","availableFrom":"2026-10-25T02:50:00+02:00","despawnAt":"2026-10-25T02:20:00+01:00","basis":"observed_encounter"}]}""").jsonObject
        val parsed = parseSpawnWindows(window)!!.single()
        assertEquals(1_800_000L, parsed.despawnAt - parsed.availableFrom)
        assertEquals(2400, parsed.lowerBoundSeconds)
        assertTrue(parsed.observed)
    }
    @Test fun `beam search reserves endpoint and preserves directed unreachable edges`() = runTest {
        val anchors = listOf(CatchAnchor(p(100.0), listOf(opportunity("a"))), CatchAnchor(p(200.0), listOf(opportunity("b", 200.0))))
        val costs = listOf(listOf(0.0,100.0,200.0,0.0), listOf(100.0,0.0,100.0,100.0), listOf(200.0,null,0.0,500.0), listOf(0.0,100.0,200.0,0.0))
        val order = catchBeamOrder(settings().copy(finish = CatchFinish.ROUND_TRIP), anchors, costs)
        assertEquals(listOf(0), order)
        val free = catchBeamOrder(settings(), anchors, costs.take(3).map { it.take(3) })
        assertEquals(listOf(0,1), free)
    }
    @Test fun `matrix never coerces unavailable cells to zero`() {
        val request = RouteMatrixRequest.pedestrian(listOf(RouteMatrixPoint("a",0.0,0.0), RouteMatrixPoint("b",0.0,1.0)))
        val matrix = RouteMatrixResponse(ids = listOf("a","b"), distanceMeters = listOf(listOf(0,null),listOf(10,0)), durationSeconds = listOf(listOf(0,null),listOf(10,0)))
        assertNull(validateCatchMatrix(matrix, request)[0][1])
        assertTrue(runCatching { validateCatchMatrix(matrix.copy(ids = listOf("b","a")), request) }.isFailure)
    }
    @Test fun `internal replans retain exact deadline and permit final minutes`() {
        val original = settings()
        val start = now + 541_234
        val replan = original.copy(startAtMillis = start, durationMinutes = 1, deadlineMillis = original.endAtMillis)
        replan.validate()
        assertEquals(original.endAtMillis, replan.endAtMillis)
        assertEquals(58.766, replan.walkingBudgetMeters, 0.0001)
        assertTrue(runCatching { replan.copy(deadlineMillis = null).validate() }.isFailure)
    }
    @Test fun `planner returns validated paths and never exceeds eight routing requests`() = runTest {
        val fake = Fake()
        fake.pages += Response.success(page(listOf("one","two")))
        val plan = CatchRoutePlanner(fake).generate(settings())
        assertEquals(2, plan.encounters.size)
        assertTrue(fake.routingCalls in 2..8)
        assertTrue(plan.distanceMeters > 50)
        assertTrue(plan.finishAtMillis <= plan.settings.endAtMillis)
    }
    @Test fun `small beam result matches exhaustive optimum through greedy trap`() = runTest {
        val opts = listOf(opportunity("near", 10.0, until = now + 500_000), opportunity("urgent", 80.0, until = now + 90_000), opportunity("last", 150.0, until = now + 300_000))
        val anchors = opts.map { CatchAnchor(it.point, listOf(it)) }
        val costs = listOf(listOf(0.0,10.0,80.0,150.0),listOf(10.0,0.0,100.0,100.0),listOf(80.0,70.0,0.0,70.0),listOf(150.0,100.0,70.0,0.0))
        fun count(order: List<Int>): Int {
            var meters = 0.0; var previous = 0; var count = 0
            for (i in order) { meters += costs[previous][i + 1]; previous = i + 1
                val at = now + (meters * 1000).toLong()
                if (at >= opts[i].availableFrom && at < opts[i].despawnAt) count++
            }; return count
        }
        fun orders(prefix: List<Int>, remaining: List<Int>): List<List<Int>> = listOf(prefix) + remaining.flatMap { i -> orders(prefix + i, remaining - i) }
        val exact = orders(emptyList(), listOf(0,1,2)).maxOf(::count)
        val chosen = catchBeamOrder(settings(), anchors, costs)
        assertEquals(3, exact)
        assertEquals(exact, count(chosen))
        assertEquals(1, chosen.first())
    }
    @Test fun `local insertion and reordering recover urgent opportunity`() = runTest {
        val anchors = listOf(CatchAnchor(p(10.0), listOf(opportunity("near"))), CatchAnchor(p(80.0), listOf(opportunity("urgent", 80.0, until = now + 90_000))))
        val costs = listOf(listOf(0.0,10.0,80.0),listOf(10.0,0.0,100.0),listOf(80.0,70.0,0.0))
        assertEquals(listOf(1,0), improveCatchOrder(settings(), anchors, costs, listOf(0)))
        assertEquals(listOf(1,0), improveCatchOrder(settings(), anchors, costs, listOf(0,1)))
        val unavailable = listOf(listOf(0.0,10.0,null),listOf(10.0,0.0,null),listOf(null,null,0.0))
        assertEquals(listOf(0), improveCatchOrder(settings(), anchors, unavailable, listOf(0)))
    }
    @Test fun `path validation rejects gaps far snaps and wrong geometry types`() {
        val request = RouteMatrixRequest.pedestrian(listOf(RouteMatrixPoint("a",0.0,0.0),RouteMatrixPoint("b",0.0,0.001)))
        val leg = CatchPathLeg("a","b",111.195,111.0,CatchPathGeometry("LineString",listOf(listOf(0.0,0.0),listOf(0.001,0.0))))
        val response = CatchPathResponse("ok","test","",listOf(CatchSnappedPoint("a",0.0,0.0),CatchSnappedPoint("b",0.0,0.001)),listOf(leg))
        assertEquals(2, validateCatchPath(response, request, settings()).size)
        assertTrue(runCatching { validateCatchPath(response.copy(legs=listOf(leg.copy(geometry=leg.geometry.copy(type="Point")))),request,settings()) }.isFailure)
        assertTrue(runCatching { validateCatchPath(response,request,settings().copy(start=p(1000.0))) }.isFailure)
    }
    @Test fun `cancellation propagates rather than publishing partial results`() = runTest {
        val fake = Fake().apply { failWithCancellation = true }
        try { CatchRoutePlanner(fake).generate(settings()); fail("Expected cancellation") } catch (_: CancellationException) { }
    }

    private class Fake : CatchRoutesService {
        val pages = mutableListOf<Response<JsonObject>>()
        val catalogues = mutableListOf<Response<JsonObject>>()
        val etags = mutableListOf<String?>()
        val queries = mutableListOf<Map<String,String>>()
        var routingCalls = 0
        var failWithCancellation = false
        override suspend fun windows(query: Map<String,String>): Response<JsonObject> {
            if (failWithCancellation) throw CancellationException()
            queries += query
            return pages.removeAt(0)
        }
        override suspend fun catalogue(query: Map<String,String>, etag: String?): Response<JsonObject> { etags += etag; return catalogues.removeAt(0) }
        override suspend fun matrix(request: RouteMatrixRequest): Response<RouteMatrixResponse> {
            routingCalls++
            val costs = request.points.map { a -> request.points.map { b -> catchDistance(CatchPoint(a.latitude,a.longitude),CatchPoint(b.latitude,b.longitude)).toInt() } }
            return Response.success(RouteMatrixResponse(ids=request.points.map {it.id},distanceMeters=costs,durationSeconds=costs))
        }
        override suspend fun path(request: RouteMatrixRequest): Response<CatchPathResponse> {
            routingCalls++
            return Response.success(CatchPathResponse("ok","fixture","2026-09-12T12:00:00Z",request.points.map { CatchSnappedPoint(it.id,it.latitude,it.longitude) },
                request.points.zipWithNext().map { (a,b) ->
                    val d = catchDistance(CatchPoint(a.latitude,a.longitude),CatchPoint(b.latitude,b.longitude))
                    CatchPathLeg(a.id,b.id,d,d,CatchPathGeometry("LineString",listOf(listOf(a.longitude,a.latitude),listOf(b.longitude,b.latitude))))
                }))
        }
    }
}
