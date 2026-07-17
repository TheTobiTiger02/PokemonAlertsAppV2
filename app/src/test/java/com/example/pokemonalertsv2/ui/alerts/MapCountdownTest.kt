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
        val endTime = "313000"

        assertEquals("5m 12s", mapCountdownLabel(endTime, 1_000L))
        assertEquals("5m 11s", mapCountdownLabel(endTime, 2_000L))
        assertEquals("Expired", mapCountdownLabel(endTime, 313_000L))
    }

    @Test
    fun countdownAboveOneHourKeepsCompactFormat() {
        assertEquals("1h 03m", mapCountdownLabel("3805000", 1_000L))
    }
}
