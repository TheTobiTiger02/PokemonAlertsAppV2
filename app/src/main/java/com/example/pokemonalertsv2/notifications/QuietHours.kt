package com.example.pokemonalertsv2.notifications

import java.util.Calendar
import java.util.TimeZone

/**
 * A recurring nightly window during which notifications are held back.
 *
 * Distinct from the existing `silence_until` timestamp, which is a one-off "quiet for the
 * next N hours" action. This is the standing schedule the user guide has always described.
 *
 * Times are minutes-from-midnight in the device's local time, so the window follows the
 * user across time zones rather than being pinned to wherever it was set.
 */
object QuietHours {

    const val MINUTES_PER_DAY = 24 * 60

    /**
     * True when [nowMillis] falls inside [startMinute, endMinute).
     *
     * The interesting case is a window that wraps past midnight (22:00 to 07:00), which is
     * the normal way to configure this and the case a naive `start <= now && now < end`
     * check gets wrong.
     *
     * A start equal to the end means a zero-length window, not a 24-hour one: the user has
     * effectively set no quiet period, and silencing them around the clock would be a
     * surprising reading of that.
     */
    fun isQuiet(
        enabled: Boolean,
        startMinute: Int,
        endMinute: Int,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean {
        if (!enabled) return false
        val start = normalize(startMinute)
        val end = normalize(endMinute)
        if (start == end) return false

        val now = minuteOfDay(nowMillis, timeZone)
        return if (start < end) {
            now >= start && now < end
        } else {
            // Wraps past midnight: quiet from `start` to the end of the day, and again
            // from the start of the day to `end`.
            now >= start || now < end
        }
    }

    fun minuteOfDay(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): Int {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

    /** Wraps rather than clamps, so 24:00 reads as midnight instead of 23:59. */
    fun normalize(minute: Int): Int = ((minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY

    fun format(minute: Int): String {
        val normalized = normalize(minute)
        return "%02d:%02d".format(normalized / 60, normalized % 60)
    }
}
