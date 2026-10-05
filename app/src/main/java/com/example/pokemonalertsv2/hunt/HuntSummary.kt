package com.example.pokemonalertsv2.hunt

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** One target retired with "Got it" during a hunt. */
@Serializable
data class HuntCatch(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val atMillis: Long
)

/**
 * What a running hunt has done so far.
 *
 * Kept beside the [HuntSession] rather than inside it: the session is collected by the
 * tracking service and the map, and a distance that ticks up every few metres would make
 * both re-run their whole reaction to a changed hunt.
 */
@Serializable
data class HuntStats(
    val catches: List<HuntCatch> = emptyList(),
    val distanceMeters: Double = 0.0,
    /** Set while the route is parked, so the time spent paused can be left out. */
    val pausedSinceMillis: Long? = null,
    val pausedTotalMillis: Long = 0L,
    /** Where distance was last measured from; see [withFix]. */
    val anchorLatitude: Double? = null,
    val anchorLongitude: Double? = null
) {
    fun withCatch(catch: HuntCatch): HuntStats =
        copy(catches = catches.filterNot { it.id == catch.id } + catch)

    fun withoutCatch(id: String): HuntStats = copy(catches = catches.filterNot { it.id == id })

    fun paused(nowMillis: Long): HuntStats =
        if (pausedSinceMillis != null) this else copy(pausedSinceMillis = nowMillis)

    fun resumed(nowMillis: Long): HuntStats {
        val since = pausedSinceMillis ?: return this
        return copy(
            pausedSinceMillis = null,
            pausedTotalMillis = pausedTotalMillis + (nowMillis - since).coerceAtLeast(0L)
        )
    }

    /**
     * Adds the walk to a new fix.
     *
     * Distance is measured from an anchor that only moves once a fix is clearly somewhere
     * else -- further from it than the fix's own accuracy. A trainer standing still drifts
     * a few metres every fix, and summing that drift would turn an hour at one gym into a
     * kilometre walked. Coarse fixes are ignored outright.
     */
    fun withFix(latitude: Double, longitude: Double, accuracyMeters: Float): HuntStats {
        if (accuracyMeters > HUNT_DISTANCE_MAX_ACCURACY_METERS) return this
        val fromLatitude = anchorLatitude
        val fromLongitude = anchorLongitude
        if (fromLatitude == null || fromLongitude == null) {
            return copy(anchorLatitude = latitude, anchorLongitude = longitude)
        }
        val step = huntDistanceMeters(fromLatitude, fromLongitude, latitude, longitude)
        if (step < maxOf(accuracyMeters.toDouble(), HUNT_DISTANCE_MIN_STEP_METERS)) return this
        // A jump no walker makes between two fixes is a GPS teleport, not a walk.
        if (step > HUNT_DISTANCE_MAX_STEP_METERS) {
            return copy(anchorLatitude = latitude, anchorLongitude = longitude)
        }
        return copy(
            distanceMeters = distanceMeters + step,
            anchorLatitude = latitude,
            anchorLongitude = longitude
        )
    }
}

/** How a finished hunt went, kept until the trainer has seen it. */
@Serializable
data class HuntSummary(
    val name: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    /** Time spent hunting, with the paused stretches taken out. */
    val activeMillis: Long,
    val distanceMeters: Double,
    val catches: List<HuntCatch>,
    /**
     * The raid whose hundo CP and counters were still showing when the hunt stopped, so the
     * summary can offer to dismiss them. Null when there was none.
     */
    val raidWatchStillShowing: String? = null
)

/** Every hunt in the history, added up. */
internal data class HuntHistoryTotals(
    val hunts: Int,
    val caught: Int,
    val distanceMeters: Double,
    val activeMillis: Long
)

internal fun huntHistoryTotals(history: List<HuntSummary>): HuntHistoryTotals = HuntHistoryTotals(
    hunts = history.size,
    caught = history.sumOf { it.catches.size },
    distanceMeters = history.sumOf { it.distanceMeters },
    activeMillis = history.sumOf { it.activeMillis }
)

/** Newest first, one entry per hunt, and never more than [cap]. */
internal fun appendHuntHistory(
    history: List<HuntSummary>,
    summary: HuntSummary,
    cap: Int = HUNT_HISTORY_CAP
): List<HuntSummary> =
    (listOf(summary) + history.filterNot { it.startedAtMillis == summary.startedAtMillis }).take(cap)

internal const val HUNT_HISTORY_CAP = 50

internal fun huntSummary(
    session: HuntSession,
    stats: HuntStats,
    nowMillis: Long,
    raidWatchStillShowing: String? = null
): HuntSummary {
    val settled = stats.resumed(nowMillis)
    return HuntSummary(
        name = session.name,
        startedAtMillis = session.startedAtMillis,
        endedAtMillis = nowMillis,
        activeMillis = (nowMillis - session.startedAtMillis - settled.pausedTotalMillis).coerceAtLeast(0L),
        distanceMeters = settled.distanceMeters,
        catches = settled.catches,
        raidWatchStillShowing = raidWatchStillShowing
    )
}

/** A hunt started and stopped again straight away has nothing worth reporting. */
internal fun HuntSummary.isWorthShowing(): Boolean =
    catches.isNotEmpty() || activeMillis >= HUNT_SUMMARY_MIN_ACTIVE_MILLIS

/** "2.4 km", or metres under one kilometre. */
internal fun formatHuntDistance(meters: Double): String =
    if (meters < 1000) "${meters.toInt()} m" else String.format(java.util.Locale.US, "%.1f km", meters / 1000)

/** "52 min", or "1 h 05 min". */
internal fun formatHuntDuration(millis: Long): String {
    val minutes = (millis / 60_000L).coerceAtLeast(0L)
    return if (minutes < 60) "$minutes min" else String.format(java.util.Locale.US, "%d h %02d min", minutes / 60, minutes % 60)
}

/** The one-line readout the notification and the sheet share. */
internal fun HuntSummary.headline(): String =
    "${catches.size} caught · ${formatHuntDistance(distanceMeters)} · ${formatHuntDuration(activeMillis)}"

private fun huntDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6_371_000.0
    val dLat = (lat2 - lat1) * PI / 180
    val dLon = (lon2 - lon1) * PI / 180
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1 * PI / 180) * cos(lat2 * PI / 180) * sin(dLon / 2) * sin(dLon / 2)
    return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
}

internal const val HUNT_DISTANCE_MAX_ACCURACY_METERS = 25f
internal const val HUNT_DISTANCE_MIN_STEP_METERS = 5.0
internal const val HUNT_DISTANCE_MAX_STEP_METERS = 500.0
internal const val HUNT_SUMMARY_MIN_ACTIVE_MILLIS = 60_000L
