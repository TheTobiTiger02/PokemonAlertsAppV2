package com.example.pokemonalertsv2.ui.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MapCountdownTest {
    @Test
    fun visibleCountdownRefreshKeyChangesEverySecond() {
        assertNotEquals(
            mapCountdownRefreshKey(showTimeLabels = true, nowMillis = 1_000L),
            mapCountdownRefreshKey(showTimeLabels = true, nowMillis = 2_000L)
        )
    }

    @Test
    fun hiddenCountdownRefreshKeyUsesThirtySecondBuckets() {
        assertEquals(
            mapCountdownRefreshKey(showTimeLabels = false, nowMillis = 1_000L),
            mapCountdownRefreshKey(showTimeLabels = false, nowMillis = 29_999L)
        )
        assertNotEquals(
            mapCountdownRefreshKey(showTimeLabels = false, nowMillis = 29_999L),
            mapCountdownRefreshKey(showTimeLabels = false, nowMillis = 30_000L)
        )
    }

    @Test
    fun countdownLabelAdvancesSecondsAndExpires() {
        // Real epoch millis: the countdown label only cares about the remaining time.
        val now = 1_756_000_000_000L
        val endTime = (now + 312_000L).toString() // 5m 12s out

        assertEquals("5m 12s", mapCountdownLabel(endTime, now))
        assertEquals("5m 11s", mapCountdownLabel(endTime, now + 1_000L))
        assertEquals("Expired", mapCountdownLabel(endTime, now + 313_000L))
    }

    @Test
    fun countdownAboveOneHourKeepsCompactFormat() {
        val now = 1_756_000_000_000L
        assertEquals("1h 03m", mapCountdownLabel((now + 3_805_000L).toString(), now))
    }
}
