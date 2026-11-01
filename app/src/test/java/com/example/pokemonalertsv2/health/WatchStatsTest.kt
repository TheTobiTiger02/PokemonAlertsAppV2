package com.example.pokemonalertsv2.health

import com.example.pokemonalertsv2.hunt.HuntSummary
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchStatsTest {
    private val start = 1_000_000_000L
    private val end = start + 10 * 60_000L

    @Test fun `heart rate is averaged per minute within the session window`() {
        val stats = WatchStats.fromSamples(
            start, end,
            heartRates = listOf(
                start - 60_000L to 70,           // before the walk: ignored
                start + 5_000L to 100, start + 50_000L to 110,
                start + 125_000L to 90,
                start + 130_000L to 400,         // implausible: ignored
                end + 60_000L to 150             // after: ignored
            ),
            steps = 1234, calories = 56.7, deviceName = "Galaxy Watch6", fetchedAtMillis = end
        )!!
        assertEquals(3, stats.heartRateCount)
        assertEquals(100, stats.averageHeartRate)
        assertEquals(90, stats.minHeartRate)
        assertEquals(110, stats.maxHeartRate)
        assertEquals(listOf(105, 0, 90), stats.heartRatePerMinute)
        assertEquals(1234L, stats.steps)
        assertEquals("Galaxy Watch6", stats.deviceName)
        assertTrue(stats.hasData)
    }

    @Test fun `nothing recorded gives null, steps alone still count`() {
        assertNull(WatchStats.fromSamples(start, end, emptyList(), 0, 0.0, null, end))
        val stepsOnly = WatchStats.fromSamples(start, end, emptyList(), 500, 0.0, null, end)!!
        assertNull(stepsOnly.averageHeartRate)
        assertTrue(stepsOnly.heartRatePerMinute.isEmpty())
    }

    @Test fun `recent sessions are read until the watch has surely synced`() {
        assertTrue(needsHealthRead(end, null, end + 60_000L))
        val early = WatchStats(steps = 10, fetchedAtMillis = end + 5 * 60_000L)
        assertTrue(needsHealthRead(end, early, end + 10 * 60_000L))
        val settled = early.copy(fetchedAtMillis = end + HEALTH_SYNC_SETTLE_MILLIS)
        assertFalse(needsHealthRead(end, settled, end + HEALTH_SYNC_SETTLE_MILLIS + 1))
        // Old walks are left alone.
        assertFalse(needsHealthRead(end, null, end + HEALTH_READ_MAX_AGE_MILLIS + 1))
    }

    @Test fun `summaries with and without watch data decode`() {
        val json = Json { ignoreUnknownKeys = true }
        val plain = """{"name":"A","startedAtMillis":1,"endedAtMillis":2,"activeMillis":1,"distanceMeters":3.0,"catches":[]}"""
        assertNull(json.decodeFromString(HuntSummary.serializer(), plain).watch)
        // Written by 1.26.0 (watch app era): extra fields are fine, fetchedAtMillis defaults to 0.
        val withWatch = """{"name":"A","startedAtMillis":1,"endedAtMillis":2,"activeMillis":1,"distanceMeters":3.0,"catches":[],
            "watch":{"heartRateSum":200,"heartRateCount":2,"minHeartRate":90,"maxHeartRate":110,"steps":50}}"""
        val watch = json.decodeFromString(HuntSummary.serializer(), withWatch).watch!!
        assertEquals(100, watch.averageHeartRate)
        assertEquals(0L, watch.fetchedAtMillis)
    }
}
