package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.util.TimeUtils
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

    @Test
    fun boundaryStripsTheJitterAWakeUpArrivedWith() {
        assertEquals(10_000L, countdownTickBoundary(nowMillis = 10_000L, tickMillis = 1_000L))
        assertEquals(10_000L, countdownTickBoundary(nowMillis = 10_001L, tickMillis = 1_000L))
        assertEquals(10_000L, countdownTickBoundary(nowMillis = 10_999L, tickMillis = 1_000L))
        assertEquals(60_000L, countdownTickBoundary(nowMillis = 77_345L, tickMillis = 30_000L))
    }

    @Test
    fun invalidTickDurationIsRejectedByTheBoundary() {
        assertThrows(IllegalArgumentException::class.java) {
            countdownTickBoundary(nowMillis = 0L, tickMillis = 0L)
        }
    }

    /**
     * The regression this file exists for: `delay` wakes late by a different amount every
     * tick, and the deadline sits at an awkward sub-second phase. Publishing the observed
     * time made the label repeat a second and then skip one; publishing the boundary must
     * step down by exactly one every time.
     */
    @Test
    fun jitteredWakeUpsStillStepTheLabelDownOneSecondAtATime() {
        val endMillis = 1_000_000_437L
        val jitterPerTick = listOf(3L, 180L, 40L, 260L, 7L, 512L, 1L, 96L)

        val labels = jitterPerTick.mapIndexed { index, jitter ->
            val observed = 1_000_000_000L - (jitterPerTick.size - index) * 1_000L + jitter
            val published = countdownTickBoundary(observed, 1_000L)
            TimeUtils.formatDurationShort(endMillis - published)
        }

        assertEquals(
            listOf("9s", "8s", "7s", "6s", "5s", "4s", "3s", "2s"),
            labels
        )
    }
}
