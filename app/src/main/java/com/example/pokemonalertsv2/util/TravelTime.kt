package com.example.pokemonalertsv2.util

import java.util.concurrent.TimeUnit

/**
 * Filtering and warnings based on how long it actually takes to walk to an alert.
 *
 * The straight-line distance the feed has always filtered on is a poor proxy on foot: a
 * raid 400 m away across a river reads as nearer than one 900 m down the street.
 * [WalkingRouteRepository] already fetches real routes for the visible alerts, so this
 * turns those durations into a filter and a "you will not make it" warning.
 *
 * Every decision here degrades to "keep the alert" when the routing data is missing. A
 * routing outage must never look like a quiet evening.
 */
object TravelTime {

    /** 0 means the filter is off. */
    const val NO_LIMIT = 0

    /**
     * True when the alert should survive a "reachable in [maxMinutes]" filter.
     *
     * A null duration means routing had nothing to say -- offline, outside the routed area,
     * or the request timed out -- and the alert is kept rather than silently hidden.
     */
    fun isReachableWithin(
        walkingDurationSeconds: Long?,
        maxMinutes: Int
    ): Boolean {
        if (maxMinutes <= NO_LIMIT) return true
        val duration = walkingDurationSeconds ?: return true
        return duration <= TimeUnit.MINUTES.toSeconds(maxMinutes.toLong())
    }

    /**
     * True when the alert will have expired before the walk is over.
     *
     * Deliberately only a warning on the card, never a filter: the user may be cycling, or
     * driving, or already halfway there, and none of that is knowable here.
     */
    fun expiresBeforeArrival(
        walkingDurationSeconds: Long?,
        remainingMillis: Long?
    ): Boolean {
        val duration = walkingDurationSeconds ?: return false
        val remaining = remainingMillis ?: return false
        if (remaining <= 0L) return false
        return TimeUnit.SECONDS.toMillis(duration) > remaining
    }

    /** Rounded up, and never below one minute, so a nearby alert does not read as "0 min". */
    fun minutes(walkingDurationSeconds: Long): Int =
        ((walkingDurationSeconds + 59) / 60).toInt().coerceAtLeast(1)

    /** The choices offered in the UI; the first is "off". */
    val PRESET_MINUTES: List<Int> = listOf(NO_LIMIT, 5, 10, 15, 30, 60)

    fun label(minutes: Int): String =
        if (minutes <= NO_LIMIT) "Any" else "$minutes min"
}
