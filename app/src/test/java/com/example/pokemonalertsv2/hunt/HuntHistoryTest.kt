package com.example.pokemonalertsv2.hunt

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HuntHistoryTest {

    private fun hunt(start: Long, caught: Int = 0, meters: Double = 0.0, active: Long = 60_000L) = HuntSummary(
        name = "Hunt $start",
        startedAtMillis = start,
        endedAtMillis = start + active,
        activeMillis = active,
        distanceMeters = meters,
        catches = List(caught) { HuntCatch("c$it", "Mon $it", null, start) }
    )

    @Test
    fun `the newest hunt goes first`() {
        val history = appendHuntHistory(listOf(hunt(1), hunt(0)), hunt(2))
        assertEquals(listOf(2L, 1L, 0L), history.map { it.startedAtMillis })
    }

    @Test
    fun `the history keeps the last fifty`() {
        val full = (0L until 50L).map { hunt(it) }.reversed()
        val history = appendHuntHistory(full, hunt(50))
        assertEquals(HUNT_HISTORY_CAP, history.size)
        assertEquals(50L, history.first().startedAtMillis)
        assertEquals(1L, history.last().startedAtMillis)
    }

    @Test
    fun `the same hunt is never listed twice`() {
        val history = appendHuntHistory(listOf(hunt(7)), hunt(7, caught = 2))
        assertEquals(1, history.size)
        assertEquals(2, history.first().catches.size)
    }

    @Test
    fun `totals add every hunt up`() {
        val totals = huntHistoryTotals(listOf(hunt(1, 2, 1_000.0, 600_000L), hunt(2, 3, 500.0, 300_000L)))
        assertEquals(HuntHistoryTotals(hunts = 2, caught = 5, distanceMeters = 1_500.0, activeMillis = 900_000L), totals)
    }

    @Test
    fun `a summary stored before the raid offer existed still reads`() {
        val old = """{"name":"Raids","startedAtMillis":1,"endedAtMillis":2,"activeMillis":1,"distanceMeters":0.0,"catches":[]}"""
        val summary = Json { ignoreUnknownKeys = true }.decodeFromString(HuntSummary.serializer(), old)
        assertNull(summary.raidWatchStillShowing)
    }
}
