package com.example.pokemonalertsv2.util

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeUtilsFormattingTest {
    @Test
    fun deadlineUsesLocalizedFriendlyCopyInsteadOfRawTimestamp() {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.of(2026, 7, 10, 12, 0, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
        val end = ZonedDateTime.of(2026, 7, 10, 13, 15, 0, 0, zone)
            .toInstant()
            .toEpochMilli()

        val formatted = TimeUtils.formatAlertEndTime(end, now)

        assertTrue(formatted.startsWith("Ends today at"))
        assertFalse(formatted.contains("2026-07-10"))
    }

    @Test
    fun remainingSecondsRoundUpSoTheLastSecondIsVisible() {
        assertEquals("1s", TimeUtils.formatDurationShort(1L))
        assertEquals("1s", TimeUtils.formatDurationShort(1_000L))
        assertEquals("2s", TimeUtils.formatDurationShort(1_001L))
        assertEquals("1m 00s", TimeUtils.formatDurationShort(59_001L))
        assertEquals("1h 00m", TimeUtils.formatDurationShort(3_599_001L))
    }

    @Test
    fun nonPositiveRemainderReadsAsZero() {
        assertEquals("0s", TimeUtils.formatDurationShort(0L))
        assertEquals("0s", TimeUtils.formatDurationShort(-5_000L))
    }
}
