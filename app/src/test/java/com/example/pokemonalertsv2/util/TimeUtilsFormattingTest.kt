package com.example.pokemonalertsv2.util

import java.time.ZoneId
import java.time.ZonedDateTime
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
}
