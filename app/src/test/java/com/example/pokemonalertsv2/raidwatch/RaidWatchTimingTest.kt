package com.example.pokemonalertsv2.raidwatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class RaidWatchTimingTest {

    private val now = 1_000_000L
    private fun minutes(n: Long) = TimeUnit.MINUTES.toMillis(n)
    private fun seconds(n: Long) = TimeUnit.SECONDS.toMillis(n)

    @Test
    fun `ticks once a minute while the raid has plenty of time left`() {
        assertEquals(
            seconds(60),
            RaidWatchTiming.nextTickDelayMillis(now, now + minutes(30))
        )
    }

    @Test
    fun `ticks every fifteen seconds inside the final stretch`() {
        assertEquals(
            seconds(15),
            RaidWatchTiming.nextTickDelayMillis(now, now + minutes(4))
        )
    }

    @Test
    fun `the final stretch boundary itself uses the fast cadence`() {
        assertEquals(
            seconds(15),
            RaidWatchTiming.nextTickDelayMillis(now, now + RaidWatchTiming.FINAL_STRETCH_MILLIS)
        )
    }

    @Test
    fun `the last tick lands exactly on expiry rather than overshooting it`() {
        // Under the 15s cadence the delay is clamped to what is left, so the final tick
        // lands on the expiry instead of leaving a stale countdown on the lock screen.
        assertEquals(
            seconds(5),
            RaidWatchTiming.nextTickDelayMillis(now, now + seconds(5))
        )
        assertEquals(1L, RaidWatchTiming.nextTickDelayMillis(now, now + 1))
    }

    @Test
    fun `above the cadence the cadence wins, not the remaining time`() {
        // 20s left is still inside the final stretch, so it ticks at 15s rather than
        // waiting the whole 20s out.
        assertEquals(
            seconds(15),
            RaidWatchTiming.nextTickDelayMillis(now, now + seconds(20))
        )
    }

    @Test
    fun `an ended raid schedules nothing so the watch is torn down`() {
        assertNull(RaidWatchTiming.nextTickDelayMillis(now, now))
        assertNull(RaidWatchTiming.nextTickDelayMillis(now, now - minutes(1)))
    }

    @Test
    fun `hasEnded is inclusive of the end instant`() {
        assertTrue(RaidWatchTiming.hasEnded(now, now))
        assertTrue(RaidWatchTiming.hasEnded(now, now - 1))
        assertFalse(RaidWatchTiming.hasEnded(now, now + 1))
    }

    @Test
    fun `remaining never goes negative`() {
        assertEquals(minutes(3), RaidWatchTiming.remainingMillis(now, now + minutes(3)))
        assertEquals(0L, RaidWatchTiming.remainingMillis(now, now - minutes(3)))
    }

}
