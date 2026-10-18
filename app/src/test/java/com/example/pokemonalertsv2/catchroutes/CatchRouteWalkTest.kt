package com.example.pokemonalertsv2.catchroutes

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Guidance while walking a route: what is ahead, pace, the walked track, backtracks and drawn areas. */
class CatchRouteWalkTest {
    private val now = Instant.parse("2026-09-12T12:00:00Z").toEpochMilli()
    private fun p(meters: Double, north: Double = 0.0) = CatchPoint(north / 111_195, meters / 111_195)
    private fun settings() = CatchRouteSettings(start = p(0.0), startAtMillis = now, speedMps = 1.0, finish = CatchFinish.ANYWHERE, durationMinutes = 30)
    private fun opportunity(id: String, meters: Double, from: Long = now, until: Long = now + 1_800_000) =
        SpawnOpportunity(id, "point-$id", p(meters), from, until, "assumed_duration")
    private fun path(vararg meters: Double) = meters.map { CatchPathPosition(p(it), it) }
    private fun encounter(id: String, meters: Double, until: Long = now + 1_800_000) =
        CatchEncounter(opportunity(id, meters, until = until), now + (meters * 1000).toLong(), meters)

    @Test fun `a stop walked past without a visit is missed, not the next stop forever`() {
        val plan = CatchItinerary(settings(), path(0.0, 1000.0), listOf(encounter("a", 100.0), encounter("b", 400.0)), emptyList())
        val walked = CatchSession(plan, progressMeters = 300.0)
        assertEquals(listOf("b"), walked.ahead(now).map { it.opportunity.id })
        assertEquals(listOf("a"), walked.missed(now).map { it.opportunity.id })
        assertEquals(p(400.0), walked.nextStopAt(now))
        val guidance = catchGuidance(walked, now)
        assertTrue(guidance, guidance.startsWith("Stop 2 · 100 m"))
    }

    @Test fun `a despawned stop drops off the route ahead`() {
        val plan = CatchItinerary(settings(), path(0.0, 1000.0), listOf(encounter("a", 100.0, until = now + 60_000), encounter("b", 400.0)), emptyList())
        val session = CatchSession(plan)
        assertEquals(listOf("a", "b"), session.ahead(now).map { it.opportunity.id })
        assertEquals(listOf("b"), session.ahead(now + 61_000).map { it.opportunity.id })
    }

    @Test fun `guidance counts catches and tells a late walker to speed up`() {
        val plan = CatchItinerary(settings(), path(0.0, 1000.0), listOf(encounter("a", 100.0), encounter("b", 600.0)), emptyList())
        val session = CatchSession(plan, visits = listOf(CatchVisit(plan.encounters.first().opportunity, now + 100_000)), progressMeters = 150.0)
        // 150 m at 1 m/s is planned for 12:02:30; at 12:06 the walk is 3 min 30 s behind.
        val late = catchGuidance(session, now + 360_000)
        assertTrue(late, late.contains("1 left · 1 caught"))
        assertTrue(late, late.contains("3 min behind, speed up"))
        assertTrue(catchPaceHint(session, now + 60_000)!!.contains("early"))
        assertNull(catchPaceHint(session, now + 150_000))
        assertNull(catchPaceHint(session.copy(paused = true), now + 360_000))
    }

    @Test fun `standing through a planned wait is on schedule`() {
        val plan = CatchItinerary(settings(), path(0.0, 1000.0), listOf(encounter("a", 100.0)), emptyList(), waits = listOf(CatchWait(100.0, 300_000)))
        val session = CatchSession(plan, progressMeters = 100.0)
        assertEquals(0L, catchScheduleDelta(session, now + 300_000))
        assertEquals(60_000L, catchScheduleDelta(session, now + 460_000))
    }

    @Test fun `a circle crossed between two fixes counts as a visit`() {
        val plan = CatchItinerary(settings(), path(0.0, 1000.0), listOf(encounter("a", 100.0)), emptyList())
        val progress = CatchRouteProgress()
        // Fixes 120 m apart either side of the spawn, 10 s apart: neither lands inside its 40 m circle.
        val first = progress.accept(CatchSession(plan), p(40.0, 50.0), 5.0, now + 1_000, now + 1_000)
        val second = progress.accept(first, p(160.0), 5.0, now + 11_000, now + 11_000)
        assertTrue(first.visits.isEmpty())
        assertEquals(1, second.visits.size)
    }

    @Test fun `progress is picked up again after a detour`() {
        val plan = CatchItinerary(settings(), path(0.0, 500.0, 1000.0), emptyList(), emptyList())
        val progress = CatchRouteProgress()
        var session = progress.accept(CatchSession(plan), p(10.0), 5.0, now + 1_000, now + 1_000)
        // Off the route, then back on it 600 m further along: beyond the nearby window, so only a search finds it.
        session = progress.accept(session, p(300.0, 200.0), 5.0, now + 5_000, now + 5_000)
        session = progress.accept(session, p(600.0), 5.0, now + 26_000, now + 26_000)
        assertEquals(600.0, session.progressMeters, 1.0)
    }

    @Test fun `the walked track ignores drift and teleports and keeps paused time out`() {
        val plan = CatchItinerary(settings(), path(0.0, 1000.0), emptyList(), emptyList())
        var session = CatchSession(plan, startedAtMillis = now)
        session = session.withWalk(p(0.0), 5.0).withWalk(p(2.0), 5.0).withWalk(p(20.0), 5.0).withWalk(p(2000.0), 5.0).withWalk(p(30.0), 60.0)
        assertEquals(20.0, session.walkedMeters, 0.5)
        assertEquals(3, session.track.size)
        session = session.pausedAt(now + 60_000).resumedAt(now + 120_000)
        assertEquals(240_000L, session.activeMillis(now + 300_000))
    }

    @Test fun `a summary counts catches, misses and speed, and survives serialization`() {
        val plan = CatchItinerary(settings(), path(0.0, 1000.0), listOf(encounter("a", 100.0), encounter("b", 400.0)), emptyList())
        val session = CatchSession(plan, visits = listOf(CatchVisit(plan.encounters.first().opportunity, now)), startedAtMillis = now,
            walkedMeters = 1200.0, track = listOf(p(0.0), p(600.0)))
        val summary = catchRouteSummary(session, plan.settings, now + 900_000)
        assertEquals(1, summary.caught)
        assertEquals(1, summary.missed)
        assertEquals(2, summary.planned)
        assertEquals(4.8, summary.averageKmh!!, 0.01)
        assertTrue(summary.isWorthKeeping())
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        assertEquals(summary, json.decodeFromString<CatchRouteSummary>(json.encodeToString(CatchRouteSummary.serializer(), summary)))
        // Sessions saved before the walk was tracked still load.
        val old = json.decodeFromString<CatchSession>(json.encodeToString(CatchSession.serializer(), CatchSession(plan))
            .replace(Regex(",\"startedAtMillis\".*\"replans\":0"), ""))
        assertEquals(0.0, old.walkedMeters, 0.0)
    }

    @Test fun `walking back the same street for nothing counts as retracing, a loop does not`() {
        val out = (0..20).map { CatchPathPosition(p(it * 10.0), it * 10.0) }
        val back = (19 downTo 0).map { CatchPathPosition(p(it * 10.0), 200.0 + (20 - it) * 10.0) }
        val spur = out + back
        assertEquals(175.0, catchRetraceMeters(spur, emptyList(), 40.0), 25.0)
        // Catching something on the way back makes that stretch worth walking.
        assertTrue(catchRetraceMeters(spur, listOf(encounter("x", 300.0)), 40.0) < 120.0)
        val loop = listOf(p(0.0), p(200.0), p(200.0, 200.0), p(0.0, 200.0), p(0.0, 30.0)).let { pts ->
            var m = 0.0
            pts.mapIndexed { i, pt -> if (i > 0) m += catchDistance(pts[i - 1], pt); CatchPathPosition(pt, m) }
        }
        assertEquals(0.0, catchRetraceMeters(loop, emptyList(), 40.0), 0.0)
        // A replan pays for streets already walked before it.
        assertTrue(catchRetraceMeters(out, emptyList(), 40.0, priorTrack = listOf(p(0.0), p(200.0))) > 150.0)
    }

    @Test fun `of two routes with equal catches the one without a backtrack wins`() {
        val e = listOf(encounter("a", 100.0))
        val spur = CatchItinerary(settings(), path(0.0, 400.0), e, emptyList(), retraceMeters = 200.0)
        val loop = CatchItinerary(settings(), path(0.0, 400.0), e, emptyList())
        assertTrue(betterCatchRoute(loop, spur))
        assertFalse(betterCatchRoute(spur, loop))
    }

    @Test fun `turning straight back onto an empty leg is penalised, turning a corner is not`() {
        val points = listOf(p(0.0), p(200.0), p(0.0, 10.0), p(200.0, 200.0))
        assertTrue(catchUTurnPenalty(points, listOf(1), 2, emptyLeg = true, expected = 0.0, meters = 0.0) > 0.5)
        assertEquals(0.0, catchUTurnPenalty(points, listOf(1), 2, emptyLeg = false, expected = 0.0, meters = 0.0), 0.0)
        assertEquals(0.0, catchUTurnPenalty(points, listOf(1), 3, emptyLeg = true, expected = 0.0, meters = 0.0), 0.0)
    }

    @Test fun `a drawn outline becomes a few corners that do not cross`() {
        val circle = (0..360 step 3).map { a -> p(100 * kotlin.math.cos(Math.toRadians(a.toDouble())), 100 * kotlin.math.sin(Math.toRadians(a.toDouble()))) }
        val area = simplifyDrawnArea(circle)
        assertTrue("${area.size}", area.size in 6..CATCH_AREA_MAX_DRAWN_CORNERS)
        assertFalse(areaSelfIntersects(area))
        assertTrue(pointInArea(p(0.0), area))
        val figureEight = listOf(p(0.0), p(100.0, 100.0), p(100.0), p(0.0, 100.0), p(0.0, 1.0))
        assertFalse(areaSelfIntersects(simplifyDrawnArea(figureEight, toleranceMeters = 1.0)))
        assertTrue(simplifyDrawnArea(listOf(p(0.0), p(10.0))).isEmpty())
    }

    @Test fun `a tapped corner goes into the nearest edge`() {
        val square = listOf(p(0.0), p(100.0), p(100.0, 100.0), p(0.0, 100.0))
        val bent = insertAreaCorner(square, p(50.0, -20.0))
        assertEquals(p(50.0, -20.0), bent[1])
        assertFalse(areaSelfIntersects(bent))
        assertEquals(4, areaEdgeMidpoints(square).size)
        assertNotNull(insertAreaCorner(emptyList(), p(1.0)).singleOrNull())
    }
}
