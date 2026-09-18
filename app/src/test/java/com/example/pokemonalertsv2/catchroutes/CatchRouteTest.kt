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

    @Test fun `automatic learns without overriding and manual settings remain explicit`() = runTest {
        for ((mode, duration) in listOf(CatchPrediction.AUTOMATIC to null, CatchPrediction.THIRTY_MINUTES to "1800",
            CatchPrediction.SIXTY_MINUTES to "3600", CatchPrediction.SUPPORTED_ONLY to null)) {
            val fake = Fake().apply { pages += Response.success(page(listOf("one"))) }
            SpawnAvailabilityRepository(fake).load(settings().copy(prediction = mode))
            assertEquals(duration, fake.queries.single()["assumedDurationSeconds"])
            assertEquals((mode != CatchPrediction.SUPPORTED_ONLY).toString(), fake.queries.single()["includePredictions"])
        }
        assertEquals(CatchPrediction.AUTOMATIC, CatchRouteSettings().prediction)
    }

    @Test fun `mixed windows preserve learned timing and enforce event restrictions`() {
        val original = Json.parseToJsonElement(row("event")).jsonObject
        val window = original.getValue("windows").jsonArray.single().jsonObject
        fun w(basis: String) = JsonObject(window + mapOf("basis" to JsonPrimitive(basis), "despawnBasis" to JsonPrimitive("verified_observation")))
        val warnings = mutableListOf<String>()
        val mixed = JsonObject(original + mapOf("windows" to JsonArray(listOf(w("inferred_lifetime"), w("future_basis"), w("observed_encounter")))))
        val parsed = parseSpawnWindows(mixed) { warnings += it }!!
        assertEquals(listOf("inferred_lifetime", "observed_encounter"), parsed.map { it.basis })
        assertEquals("verified_observation", parsed.first().despawnBasis)
        assertEquals(1, warnings.size)
        val restricted = JsonObject(mixed + mapOf("requiresLiveConfirmation" to JsonPrimitive(true), "activityPattern" to JsonPrimitive("likely_event")))
        val only = parseSpawnWindows(restricted)!!.single()
        assertTrue(only.observed && only.requiresLiveConfirmation)
        assertEquals("likely_event", only.activityPattern)
        assertTrue(parsed.last().evidenceRank > parsed.first().evidenceRank)
        assertTrue(parsed.first().evidenceRank > opportunity().evidenceRank)
    }

    @Test fun `live snapshot and empty restricted availability survive repository`() = runTest {
        val point = JsonObject(Json.parseToJsonElement(row("event")).jsonObject + mapOf("windows" to JsonArray(emptyList()), "requiresLiveConfirmation" to JsonPrimitive(true)))
        val source = Json.parseToJsonElement("""{"source":"wingull","complete":true,"liveSnapshot":{"refreshedAt":"2026-09-12T12:00:00Z","complete":false,"returned":12,"dropped":1,"coverageKind":"live_snapshot"}}""").jsonObject
        val response = JsonObject(page(emptyList()) + mapOf("data" to JsonArray(listOf(point)), "sources" to JsonArray(listOf(source))))
        val fake = Fake().apply { pages += Response.success(response) }
        val data = SpawnAvailabilityRepository(fake).load(settings())
        assertEquals(1, data.restrictedPointCount)
        assertTrue(data.opportunities.isEmpty())
        assertEquals(12, data.sources.single().liveSnapshot?.returned)
        assertTrue(data.warnings.any { it.contains("incomplete live coverage") })
        fake.pages += Response.success(response)
        val error = runCatching { CatchRoutePlanner(fake).generate(settings()) }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("predictions cannot enable them"))
    }

    @Test fun `a guided session keeps its stops on screen and keeps counting visits`() {
        // Hiding the route while it was being rebuilt, every couple of minutes, is what made
        // guidance unusable: the stops, the next-stop line and visit counting all stopped.
        val plan = CatchItinerary(settings(), path(0.0, 200.0), listOf(CatchEncounter(opportunity(), now, 100.0)), emptyList())
        val session = CatchSession(plan)
        assertEquals(1, session.remaining.size)
        assertEquals(p(100.0), session.nextStop)
        assertEquals("1 remaining", session.availabilityReadout)
        val progress = CatchRouteProgress()
        progress.accept(session, p(100.0), 5.0, now, now)
        assertEquals(1, progress.accept(session, p(100.0), 5.0, now + 2500, now + 2500).visits.size)
    }

    @Test fun `guidance names the next stop and only advises a replan`() {
        val plan = CatchItinerary(settings(), path(0.0, 200.0), listOf(CatchEncounter(opportunity(), now, 100.0)), emptyList())
        val session = CatchSession(plan)
        val plain = catchGuidance(session, now)
        assertTrue(plain, plain.startsWith("Stop 1 · 100 m"))
        assertTrue(plain, "out of date" !in plain)
        // Out of date adds advice to the guidance; it never replaces the next stop.
        val stale = catchGuidance(session, now, outOfDate = true)
        assertTrue(stale, stale.startsWith("Stop 1 · 100 m") && stale.endsWith("Route out of date, tap Replan"))
        val replanning = catchGuidance(session, now, outOfDate = true, replanning = true)
        assertTrue(replanning, replanning.startsWith("Stop 1") && replanning.endsWith("Replanning…"))
    }

    @Test fun `old serialized settings and opportunities retain compatibility`() {
        val json = kotlinx.serialization.json.Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val old = json.decodeFromString<CatchRouteSettings>("""{"prediction":"THIRTY_MINUTES"}""")
        assertEquals(CatchPrediction.THIRTY_MINUTES, old.prediction)
        val oldOpportunity = json.decodeFromString<SpawnOpportunity>("""{"id":"x","pointId":"p","point":{"latitude":0.0,"longitude":0.0},"availableFrom":1,"despawnAt":2,"basis":"observed_encounter"}""")
        assertFalse(oldOpportunity.requiresLiveConfirmation)
        assertEquals("unknown", oldOpportunity.activityPattern)
    }

    @Test fun `windows carry probability and schedule, and older payloads fall back per basis`() {
        val row = Json.parseToJsonElement("""{"id":"s","latitude":0.0,"longitude":0.0009,
            "schedule":{"despawnSecondOfHour":900,"spawnSecondOfHour":2700,"durationSeconds":1800,"durationBasis":"observed_30","confidence":0.75,"supportCycles":3,"totalCycles":4,"lastVerifiedAt":"2026-09-12T11:15:00Z"},
            "windows":[{"opportunityId":"s@1","availableFrom":"2026-09-12T12:00:00Z","despawnAt":"2026-09-12T12:30:00Z","basis":"recurring_schedule","probability":0.62},
                       {"opportunityId":"s@2","availableFrom":"2026-09-12T13:00:00Z","despawnAt":"2026-09-12T13:30:00Z","basis":"recurring_schedule","probability":7}]}""").jsonObject
        val parsed = parseSpawnWindows(row)!!
        assertEquals(0.62, parsed[0].expectedCatch, 1e-9)
        assertEquals(1.0, parsed[1].expectedCatch, 1e-9)
        assertEquals(1800, parsed[0].schedule?.durationSeconds)
        assertEquals("observed_30", parsed[0].schedule?.durationBasis)
        val old = parseSpawnWindows(Json.parseToJsonElement(row("legacy")).jsonObject)!!.single()
        assertNull(old.probability)
        assertEquals(0.5, old.expectedCatch, 1e-9)
        assertTrue(opportunity().copy(basis = "observed_encounter").expectedCatch > old.expectedCatch)
    }

    @Test fun `beam prefers the likelier window over a guess at equal distance`() = runTest {
        val guess = opportunity("guess", 50.0, until = now + 600_000).copy(probability = 0.3)
        val verified = opportunity("verified", 50.0, until = now + 600_000).copy(point = CatchPoint(0.0005, 50.0 / 111_195), probability = 0.9)
        val anchors = listOf(CatchAnchor(guess.point, listOf(guess)), CatchAnchor(verified.point, listOf(verified)))
        // Budget reaches only one of the two.
        val costs = listOf(listOf(0.0, 500.0, 500.0), listOf(500.0, 0.0, 900.0), listOf(500.0, 900.0, 0.0))
        assertEquals(listOf(1), catchBeamOrder(settings(), anchors, costs))
    }

    @Test fun `waiting is off by default and, when allowed, waits for a spawn that starts soon`() = runTest {
        val soon = opportunity("soon", 100.0, from = now + 180_000)
        val anchors = listOf(CatchAnchor(soon.point, listOf(soon)))
        val costs = listOf(listOf(0.0, 100.0), listOf(100.0, 0.0))
        assertEquals(0L, catchWaitAt(settings(), listOf(soon), now + 100_000, emptySet()))
        assertTrue(catchBeamOrder(settings(), anchors, costs).isEmpty())
        val patient = settings().copy(maxWaitMinutes = 2)
        assertEquals(80_000L, catchWaitAt(patient, listOf(soon), now + 100_000, emptySet()))
        assertEquals(listOf(0), catchBeamOrder(patient, anchors, costs))
        // Too far off for the allowed wait.
        assertEquals(0L, catchWaitAt(patient.copy(maxWaitMinutes = 1), listOf(soon), now + 100_000, emptySet()))
        // A wait counts against the walking budget, and inside the circle it makes the encounter.
        val waits = planCatchWaits(patient, listOf(soon.point to 100.0), listOf(soon))
        assertEquals(listOf(CatchWait(100.0, 80_000L)), waits)
        val encounter = scoreCatchPath(path(0.0, 100.0, 140.0), patient, listOf(soon), waits).single()
        assertEquals(now + 180_000, encounter.arrivalMillis)
        assertTrue(scoreCatchPath(path(0.0, 100.0, 140.0), patient, listOf(soon)).isEmpty())
        assertTrue(runCatching { settings().copy(maxWaitMinutes = 16).validate() }.isFailure)
    }

    @Test fun `spawnpoint detail parses schedule, species and upcoming windows`() {
        val body = Json.parseToJsonElement("""{"id":"derived:x","source":"pogomapper","latitude":49.74,"longitude":8.62,
            "encounterCount":5,"verifiedEncounterCount":4,"liveLastSeenAt":"2026-09-12T11:50:00Z","timingConflict":false,"requiresLiveConfirmation":false,
            "schedule":{"despawnSecondOfHour":1200,"durationSeconds":3600,"durationBasis":"observed_60","confidence":1.0,"supportCycles":4,"totalCycles":4},
            "recentEncounters":[{"source":"pogomapper","pokemonId":25,"firstSeenAt":"2026-09-12T11:30:00Z","upstreamFirstSeenAt":"2026-09-12T11:05:00Z","verifiedExpiry":1789214400},
                                {"source":"pogomapper","pokemonId":null,"firstSeenAt":"2026-09-12T10:30:00Z","unverifiedExpiry":1789210800}],
            "upcomingWindows":[{"opportunityId":"derived:x@1","availableFrom":"2026-09-12T12:20:00Z","despawnAt":"2026-09-12T13:20:00Z","basis":"inferred_lifetime","probability":0.9}],
            "uncertainty":["no_recent_live_observation"]}""").jsonObject
        val detail = parseSpawnpointDetail(body)!!
        assertEquals(3600, detail.schedule?.durationSeconds)
        assertEquals(listOf(25, null), detail.sightings.map { it.pokemonId })
        assertEquals(Instant.parse("2026-09-12T11:05:00Z").toEpochMilli(), detail.sightings.first().firstSeenAt)
        assertTrue(detail.sightings.first().verified && !detail.sightings.last().verified)
        assertEquals(0.9, detail.upcoming.single().expectedCatch, 1e-9)
        assertNull(parseSpawnpointDetail(JsonObject(body + ("latitude" to JsonPrimitive(300)))))
    }

    @Test fun `details sheet reads the spawnpoint's state and hour in words`() {
        val active = opportunity(from = now - 60_000, until = now + 600_000)
        assertEquals(SpawnState.ACTIVE, spawnStatus(listOf(active), now).state)
        assertEquals("Active · 10m 00s left", spawnStatus(listOf(active), now).text)
        val later = opportunity(from = now + 240_000, until = now + 2_000_000).copy(id = "one-later")
        assertEquals("Spawns in 4m 00s", spawnStatus(listOf(later), now).text)
        assertEquals(SpawnState.ENDED, spawnStatus(listOf(opportunity(until = now - 1)), now).state)
        assertEquals(":05:09", secondOfHourLabel(309))
        // A 30-minute spawn despawning at :10 started at :40 the hour before: two segments.
        val wrapped = hourBar(SpawnSchedule(despawnSecondOfHour = 600, durationSeconds = 1800, durationBasis = "observed_30"), null, java.time.ZoneOffset.UTC)
        assertEquals(2, wrapped.segments.size)
        assertEquals(0f, wrapped.segments[0].start)
        assertEquals(2400 / 3600f, wrapped.segments[1].start, 1e-6f)
        assertTrue(wrapped.certain)
        assertEquals(listOf(0f..1f), hourBar(SpawnSchedule(despawnSecondOfHour = 600, durationSeconds = 3600, durationBasis = "observed_60"), null).segments)
        assertFalse(hourBar(null, opportunity(), java.time.ZoneOffset.UTC).certain)
        assertEquals("Estimated 60-minute lifetime", basisLabel("inferred_lifetime"))
        assertEquals("Verified schedule", confidenceLabel(opportunity().copy(probability = 0.9)))
        val selections = spawnpointSelections(listOf(CatchEncounter(active, now, 10.0), CatchEncounter(later, now + 240_000, 10.0),
            CatchEncounter(opportunity("other", 200.0), now, 200.0)))
        assertEquals(2, selections.size)
        assertEquals(2, selections.first().opportunities.size)
    }

    @Test fun `event spawnpoints read as such in the detail`() {
        val body = Json.parseToJsonElement("""{"id":"e","latitude":49.87,"longitude":8.65,"activityPattern":"event_only",
            "eventTypes":["pokemon-spotlight-hour"],"lastActiveAt":"2026-09-10T16:00:00.000Z","reason":"event_only_inactive",
            "nextEvent":{"name":"Rattata Spotlight Hour","eventType":"pokemon-spotlight-hour","startAt":"2026-09-17T16:00:00.000Z","endAt":"2026-09-17T17:00:00.000Z"},
            "upcomingWindows":[]}""").jsonObject
        val detail = parseSpawnpointDetail(body)!!
        assertEquals(listOf("pokemon-spotlight-hour"), detail.eventTypes)
        assertEquals("Rattata Spotlight Hour", detail.nextEvent?.name)
        assertEquals("event_only_inactive", detail.reason)
        val notice = activityNotice(detail.activityPattern, detail.eventTypes, detail.nextEvent, detail.reason, now)!!
        assertTrue(notice.startsWith("Event spawnpoint: only spawns during Spotlight Hours."))
        assertTrue(notice.contains("Next: Rattata Spotlight Hour"))
        val during = activityNotice("event_only", detail.eventTypes, detail.nextEvent, null, detail.nextEvent!!.startAt + 60_000)!!
        assertTrue(during.contains("Active now"))
        assertTrue(activityNotice("unknown", emptyList(), null, "catalogue_only", now)!!.contains("catalogue"))
        assertNull(activityNotice("regular", emptyList(), null, null, now))
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
        override suspend fun spawnpoint(id: String): Response<JsonObject> = error("not used")
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
