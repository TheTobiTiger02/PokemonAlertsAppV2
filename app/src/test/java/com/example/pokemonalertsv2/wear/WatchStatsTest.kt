package com.example.pokemonalertsv2.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchStatsTest {
    @Test fun `heart rate is averaged and bucketed per minute, steps and calories are totals`() {
        var stats = WatchStats()
        assertFalse(stats.hasData)
        assertNull(stats.averageHeartRate)
        stats = stats.withSample(WatchSample(atMillis = 0, heartRate = 100, stepsTotal = 10, caloriesTotal = 1.0), "Galaxy Watch")
        stats = stats.withSample(WatchSample(atMillis = 30_000, heartRate = 110, stepsTotal = 60, caloriesTotal = 3.5))
        stats = stats.withSample(WatchSample(atMillis = 125_000, heartRate = 90, stepsTotal = 200, caloriesTotal = 9.0))
        assertEquals(100, stats.averageHeartRate)
        assertEquals(90, stats.minHeartRate)
        assertEquals(110, stats.maxHeartRate)
        assertEquals(200L, stats.steps)
        assertEquals(9.0, stats.calories, 0.0)
        assertEquals(listOf(105, 0, 90), stats.heartRatePerMinute)
        assertEquals("Galaxy Watch", stats.deviceName)
        assertTrue(stats.hasData)
    }

    @Test fun `implausible heart rates and older totals are ignored`() {
        val stats = WatchStats()
            .withSample(WatchSample(0, heartRate = 300, stepsTotal = 50))
            .withSample(WatchSample(1_000, heartRate = null, stepsTotal = 20))
        assertEquals(0, stats.heartRateCount)
        assertEquals(50L, stats.steps)
    }

    @Test fun `wire format round-trips`() {
        val state = WearSessionState(kind = WearSessionState.KIND_HUNT, name = "Hundos", caught = 3, distanceMeters = 1200.0, target = "Dratini")
        assertEquals(state, WearProtocol.decodeState(WearProtocol.encodeState(state)))
        assertFalse(WearSessionState().active)
        val sample = WatchSample(5, 120, 300, 12.5)
        assertEquals(sample, WearProtocol.decodeSample(WearProtocol.encodeSample(sample)))
        assertEquals(WearProtocol.Command.CAUGHT, WearProtocol.Command.fromWire("caught"))
        assertNull(WearProtocol.Command.fromWire("dance"))
        // The watch module parses this exact shape; keep field names stable.
        assertTrue(WearProtocol.encodeState(state).contains("\"kind\":\"hunt\""))
    }
}
