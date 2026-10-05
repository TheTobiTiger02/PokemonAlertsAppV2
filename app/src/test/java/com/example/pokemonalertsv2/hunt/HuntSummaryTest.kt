package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.FilterDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HuntSummaryTest {

    private val start = 1_700_000_000_000L
    private val session = HuntSession(name = "Dragon grunts", definition = FilterDefinition(), startedAtMillis = start)

    /** About 11 m of latitude. */
    private val step = 0.0001

    @Test
    fun `the first fix only sets where distance is measured from`() {
        val stats = HuntStats().withFix(50.0, 8.0, 5f)
        assertEquals(0.0, stats.distanceMeters, 0.0)
        assertEquals(50.0, stats.anchorLatitude!!, 0.0)
    }

    @Test
    fun `walking adds up`() {
        var stats = HuntStats()
        repeat(11) { i -> stats = stats.withFix(50.0 + i * step, 8.0, 5f) }
        // Ten legs of ~11.1 m.
        assertEquals(111.0, stats.distanceMeters, 2.0)
    }

    @Test
    fun `standing still with GPS drift adds nothing`() {
        var stats = HuntStats().withFix(50.0, 8.0, 10f)
        // Wobbling ~3 m either way, inside a 10 m accuracy.
        repeat(20) { i -> stats = stats.withFix(50.0 + if (i % 2 == 0) 0.00003 else -0.00003, 8.0, 10f) }
        assertEquals(0.0, stats.distanceMeters, 0.0)
    }

    @Test
    fun `coarse fixes are ignored`() {
        val stats = HuntStats().withFix(50.0, 8.0, 5f).withFix(50.01, 8.0, 60f)
        assertEquals(0.0, stats.distanceMeters, 0.0)
        assertEquals(50.0, stats.anchorLatitude!!, 0.0)
    }

    @Test
    fun `a teleport moves the anchor without counting the jump`() {
        val stats = HuntStats().withFix(50.0, 8.0, 5f).withFix(50.1, 8.0, 5f)
        assertEquals(0.0, stats.distanceMeters, 0.0)
        assertEquals(50.1, stats.anchorLatitude!!, 0.0)
    }

    @Test
    fun `paused time is left out of the duration`() {
        val stats = HuntStats()
            .paused(start + 10 * 60_000L)
            .resumed(start + 25 * 60_000L)
        val summary = huntSummary(session, stats, start + 60 * 60_000L)
        assertEquals(45 * 60_000L, summary.activeMillis)
    }

    @Test
    fun `a hunt still paused at the stop counts the pause up to then`() {
        val stats = HuntStats().paused(start + 50 * 60_000L)
        val summary = huntSummary(session, stats, start + 60 * 60_000L)
        assertEquals(50 * 60_000L, summary.activeMillis)
    }

    @Test
    fun `pausing twice keeps the first start of the pause`() {
        val stats = HuntStats().paused(start + 1_000L).paused(start + 5_000L)
        assertEquals(start + 1_000L, stats.pausedSinceMillis)
    }

    @Test
    fun `resuming a running hunt changes nothing`() {
        assertEquals(HuntStats(), HuntStats().resumed(start))
    }

    @Test
    fun `catches are kept once each and can be taken back`() {
        val catch = HuntCatch("a", "Mewtwo", null, start)
        val stats = HuntStats().withCatch(catch).withCatch(catch.copy(atMillis = start + 1))
        assertEquals(1, stats.catches.size)
        assertTrue(stats.withoutCatch("a").catches.isEmpty())
    }

    @Test
    fun `an instant stop is not worth a summary, a catch always is`() {
        val quick = huntSummary(session, HuntStats(), start + 10_000L)
        assertFalse(quick.isWorthShowing())
        val caught = huntSummary(session, HuntStats().withCatch(HuntCatch("a", "Mewtwo", null, start)), start + 10_000L)
        assertTrue(caught.isWorthShowing())
        assertTrue(huntSummary(session, HuntStats(), start + HUNT_SUMMARY_MIN_ACTIVE_MILLIS).isWorthShowing())
    }

    @Test
    fun `the headline reads at a glance`() {
        val summary = HuntSummary(
            name = "Dragon grunts",
            startedAtMillis = start,
            endedAtMillis = start + 3_900_000L,
            activeMillis = 3_900_000L,
            distanceMeters = 2_430.0,
            catches = listOf(HuntCatch("a", "Mewtwo", null, start))
        )
        assertEquals("1 caught · 2.4 km · 1 h 05 min", summary.headline())
        assertEquals("850 m", formatHuntDistance(850.0))
        assertEquals("52 min", formatHuntDuration(52 * 60_000L))
    }

    @Test
    fun `editing the targets keeps the trip`() {
        val running = session.copy(targetUniqueId = "walking-to", paused = true, savedHuntId = "row")
        val edited = running.withTargets(
            name = "Raids",
            definition = FilterDefinition(),
            savedHuntId = "row",
            area = emptyList()
        )
        assertEquals(start, edited.startedAtMillis)
        assertEquals("walking-to", edited.targetUniqueId)
        assertEquals("Raids", edited.name)
        assertFalse(edited.paused)
    }
}
