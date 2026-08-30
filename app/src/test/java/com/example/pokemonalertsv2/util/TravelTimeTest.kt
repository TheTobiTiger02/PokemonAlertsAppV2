package com.example.pokemonalertsv2.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class TravelTimeTest {

    private fun minutes(n: Long) = TimeUnit.MINUTES.toSeconds(n)

    // --- The fallback behaviour, which is the point of the class ---------------

    @Test
    fun `an alert with no route is kept, so a routing outage cannot empty the feed`() {
        assertTrue(TravelTime.isReachableWithin(walkingDurationSeconds = null, maxMinutes = 5))
    }

    @Test
    fun `a route with no remaining time never raises the arrival warning`() {
        assertFalse(TravelTime.expiresBeforeArrival(null, TimeUnit.MINUTES.toMillis(1)))
        assertFalse(TravelTime.expiresBeforeArrival(minutes(30), null))
    }

    // --- The filter -----------------------------------------------------------

    @Test
    fun `no limit keeps everything`() {
        assertTrue(TravelTime.isReachableWithin(minutes(120), TravelTime.NO_LIMIT))
        assertTrue(TravelTime.isReachableWithin(minutes(120), -5))
    }

    @Test
    fun `the limit is inclusive of its own boundary`() {
        assertTrue(TravelTime.isReachableWithin(minutes(10), 10))
        assertFalse(TravelTime.isReachableWithin(minutes(10) + 1, 10))
    }

    @Test
    fun `a long walk is filtered out`() {
        assertFalse(TravelTime.isReachableWithin(minutes(45), 15))
    }

    // --- The warning ----------------------------------------------------------

    @Test
    fun `a walk longer than the time left warns`() {
        assertTrue(
            TravelTime.expiresBeforeArrival(
                walkingDurationSeconds = minutes(20),
                remainingMillis = TimeUnit.MINUTES.toMillis(10)
            )
        )
    }

    @Test
    fun `a walk that finishes in time does not warn`() {
        assertFalse(
            TravelTime.expiresBeforeArrival(
                walkingDurationSeconds = minutes(5),
                remainingMillis = TimeUnit.MINUTES.toMillis(10)
            )
        )
    }

    @Test
    fun `an already expired alert does not warn - it is simply gone`() {
        assertFalse(TravelTime.expiresBeforeArrival(minutes(5), 0L))
        assertFalse(TravelTime.expiresBeforeArrival(minutes(5), -1000L))
    }

    // --- Formatting -----------------------------------------------------------

    @Test
    fun `minutes round up and never read as zero`() {
        assertEquals(1, TravelTime.minutes(1))
        assertEquals(1, TravelTime.minutes(60))
        assertEquals(2, TravelTime.minutes(61))
        assertEquals(5, TravelTime.minutes(minutes(5)))
    }

    @Test
    fun `the off option is labelled rather than shown as zero minutes`() {
        assertEquals("Any", TravelTime.label(TravelTime.NO_LIMIT))
        assertEquals("15 min", TravelTime.label(15))
    }

    @Test
    fun `the preset list starts with off`() {
        assertEquals(TravelTime.NO_LIMIT, TravelTime.PRESET_MINUTES.first())
        assertTrue(TravelTime.PRESET_MINUTES.drop(1).all { it > 0 })
    }
}
