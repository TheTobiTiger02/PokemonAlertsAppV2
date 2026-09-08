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
    fun secondsAreOnlySpentInsideTheWindowForMarkerLabels() {
        // A ticking seconds figure changes the marker's icon cache key every second, which
        // costs a bitmap per marker per second. It is worth that near the despawn and not
        // hours out, so labels drawn into markers coarsen past the window.
        val now = 1_756_000_000_000L
        val insideWindow = (now + MAP_SECOND_PRECISION_WINDOW_MS - 60_000L).toString()
        val outsideWindow = (now + MAP_SECOND_PRECISION_WINDOW_MS + 60_000L).toString()

        assertEquals("14m 00s", mapCountdownLabel(insideWindow, now, coarsenBeyondWindow = true))
        assertEquals("16m", mapCountdownLabel(outsideWindow, now, coarsenBeyondWindow = true))
        // Still one string per minute out there, so the icon cache holds between ticks.
        assertEquals(
            mapCountdownLabel(outsideWindow, now, coarsenBeyondWindow = true),
            mapCountdownLabel(outsideWindow, now + 1_000L, coarsenBeyondWindow = true)
        )
        // Single chips are one element, not hundreds, so they keep their seconds.
        assertEquals("16m 00s", mapCountdownLabel(outsideWindow, now))
    }

    @Test
    fun countdownAboveOneHourKeepsCompactFormat() {
        val now = 1_756_000_000_000L
        assertEquals("1h 03m", mapCountdownLabel((now + 3_805_000L).toString(), now))
    }

    @Test
    fun minutePrecisionLabelsRoundUpAndChangeOncePerMinute() {
        val now = 1_756_000_000_000L
        val endTime = (now + 312_000L).toString() // 5m 12s out

        // Rounds up like the seconds label; only the minute boundary changes the string,
        // which is what lets a crowded map skip per-second marker rebuilds.
        assertEquals("6m", mapCountdownLabel(endTime, now, minutePrecision = true))
        assertEquals("5m", mapCountdownLabel(endTime, now + 30_000L, minutePrecision = true))
        assertEquals("5m", mapCountdownLabel(endTime, now + 60_000L, minutePrecision = true))
        assertEquals("3m", mapCountdownLabel(endTime, now + 150_000L, minutePrecision = true))
        assertEquals("1h 04m", mapCountdownLabel((now + 3_805_000L).toString(), now, minutePrecision = true))
        assertEquals("Expired", mapCountdownLabel(endTime, now + 313_000L, minutePrecision = true))
    }

    @Test
    fun refreshKeyIntervalFollowsTheAdaptiveClock() {
        // Crowded map: labels refresh once a minute, so the key must use minute buckets.
        assertEquals(
            mapCountdownRefreshKey(true, 1_000L, refreshIntervalMillis = 60_000L),
            mapCountdownRefreshKey(true, 59_999L, refreshIntervalMillis = 60_000L)
        )
        assertNotEquals(
            mapCountdownRefreshKey(true, 59_999L, refreshIntervalMillis = 60_000L),
            mapCountdownRefreshKey(true, 60_000L, refreshIntervalMillis = 60_000L)
        )
    }
}
