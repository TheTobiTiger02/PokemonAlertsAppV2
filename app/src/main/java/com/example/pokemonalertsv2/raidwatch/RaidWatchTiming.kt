package com.example.pokemonalertsv2.raidwatch

import java.util.concurrent.TimeUnit

/**
 * Cadence maths for the raid Live Update.
 *
 * A Live Update does not refresh itself, so the notification has to be re-posted on a
 * schedule. Kept here as pure functions of (now, end) so the cadence and the expiry edge
 * can be unit-tested without an AlarmManager.
 */
object RaidWatchTiming {

    /** Under this much time left, the countdown is worth updating more often. */
    val FINAL_STRETCH_MILLIS: Long = TimeUnit.MINUTES.toMillis(5)

    private val SLOW_TICK_MILLIS: Long = TimeUnit.SECONDS.toMillis(60)
    private val FAST_TICK_MILLIS: Long = TimeUnit.SECONDS.toMillis(15)

    /**
     * How long until the notification should next be re-posted, or null when the raid has
     * ended and the watch should be torn down instead.
     *
     * The delay is clamped to the time actually remaining, so the last tick lands on the
     * expiry rather than overshooting it and leaving a stale countdown on the lock screen.
     */
    fun nextTickDelayMillis(nowMillis: Long, endMillis: Long): Long? {
        val remaining = endMillis - nowMillis
        if (remaining <= 0L) return null
        val cadence = if (remaining <= FINAL_STRETCH_MILLIS) FAST_TICK_MILLIS else SLOW_TICK_MILLIS
        return minOf(cadence, remaining)
    }

    fun hasEnded(nowMillis: Long, endMillis: Long): Boolean = nowMillis >= endMillis

    fun remainingMillis(nowMillis: Long, endMillis: Long): Long =
        (endMillis - nowMillis).coerceAtLeast(0L)
}
