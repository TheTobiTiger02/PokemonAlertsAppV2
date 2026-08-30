package com.example.pokemonalertsv2.ui.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CountdownClockTest {
    @Test
    fun tickDelayTargetsTheNextAbsoluteSecondWithoutAccumulatingDrift() {
        assertEquals(1_000L, countdownTickDelay(nowMillis = 10_000L, tickMillis = 1_000L))
        assertEquals(999L, countdownTickDelay(nowMillis = 10_001L, tickMillis = 1_000L))
        assertEquals(1L, countdownTickDelay(nowMillis = 10_999L, tickMillis = 1_000L))
    }

    @Test
    fun coarseClockUsesTheSameAbsoluteBoundaryRule() {
        assertEquals(30_000L, countdownTickDelay(nowMillis = 60_000L, tickMillis = 30_000L))
        assertEquals(12_655L, countdownTickDelay(nowMillis = 77_345L, tickMillis = 30_000L))
    }

    @Test
    fun invalidTickDurationIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            countdownTickDelay(nowMillis = 0L, tickMillis = 0L)
        }
    }
}
