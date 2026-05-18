package com.example.pokemonalertsv2.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class AlertSnoozeSchedulerTest {

    @Test
    fun calculateTriggerAt_returnsFutureTriggerForActiveAlert() {
        val now = 1_000L
        val end = now + TimeUnit.MINUTES.toMillis(30)

        val triggerAt = AlertSnoozeScheduler.calculateTriggerAt(
            nowMillis = now,
            durationMinutes = 10,
            alertEndMillis = end
        )

        assertEquals(now + TimeUnit.MINUTES.toMillis(10), triggerAt)
    }

    @Test
    fun calculateTriggerAt_returnsNullForExpiredAlert() {
        val now = 1_000L
        val end = now

        val triggerAt = AlertSnoozeScheduler.calculateTriggerAt(
            nowMillis = now,
            durationMinutes = 10,
            alertEndMillis = end
        )

        assertNull(triggerAt)
    }

    @Test
    fun calculateTriggerAt_returnsNullWhenSnoozeWouldFireAfterAlertEnds() {
        val now = 1_000L
        val end = now + TimeUnit.MINUTES.toMillis(5)

        val triggerAt = AlertSnoozeScheduler.calculateTriggerAt(
            nowMillis = now,
            durationMinutes = 10,
            alertEndMillis = end
        )

        assertNull(triggerAt)
    }

    @Test
    fun normalizedDurationMinutes_clampsCustomDurations() {
        assertEquals(1, AlertSnoozeScheduler.normalizedDurationMinutes(0))
        assertEquals(15, AlertSnoozeScheduler.normalizedDurationMinutes(15))
        assertEquals(24 * 60, AlertSnoozeScheduler.normalizedDurationMinutes(3_000))
    }
}
