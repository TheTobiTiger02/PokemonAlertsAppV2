package com.example.pokemonalertsv2.catchroutes

import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.roundToInt

/**
 * How one guided route went. Catches are the spawn windows the walk passed through while they were
 * open: the app cannot see the game, so a visit is the closest honest stand-in for a catch.
 */
@Serializable
data class CatchRouteSummary(
    val id: String,
    val name: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    /** Guidance time with the paused stretches left out. */
    val activeMillis: Long,
    val walkedMeters: Double,
    val caught: Int,
    val skipped: Int,
    /** Planned windows of the last route that were walked past, or despawned, without a visit. */
    val missed: Int,
    /** Planned windows the route offered over the whole walk, across replans. */
    val planned: Int,
    val replans: Int = 0,
    /** The settings of the walk as first planned: what "Walk again" starts from. */
    val settings: CatchRouteSettings,
    /** What was walked, drift-filtered. */
    val track: List<CatchPoint> = emptyList(),
    /** The last planned route, thinned for drawing. */
    val plannedPath: List<CatchPoint> = emptyList(),
    /** What a connected watch measured; null when none was connected. */
    val watch: com.example.pokemonalertsv2.wear.WatchStats? = null,
) {
    /** Kilometres per hour over the active time; null when too short to mean anything. */
    val averageKmh: Double? get() = if (activeMillis < 60_000 || walkedMeters < 50) null else walkedMeters / (activeMillis / 1000.0) * 3.6
}

/** Too short to be worth a history entry: started and stopped without walking or catching anything. */
internal fun CatchRouteSummary.isWorthKeeping(): Boolean = caught > 0 || activeMillis >= 60_000

internal const val CATCH_ROUTE_HISTORY_CAP = 50

/**
 * The summary of [session] ending at [now]. [original] is the route as first started, kept across
 * replans so "Walk again" offers the walk the trainer chose, not an internal remainder of it.
 */
internal fun catchRouteSummary(session: CatchSession, original: CatchRouteSettings, now: Long, replans: Int = 0): CatchRouteSummary {
    val ended = minOf(now, session.itinerary.settings.endAtMillis).coerceAtLeast(session.startedAtMillis)
    val started = session.startedAtMillis.takeIf { it > 0 } ?: session.itinerary.settings.startAtMillis
    val settled = session.resumedAt(ended)
    val active = if (session.startedAtMillis > 0) settled.activeMillis(ended) else (ended - started).coerceAtLeast(0)
    val plannedIds = (session.visits.map { it.opportunity.id } + session.itinerary.encounters.map { it.opportunity.id }).toSet()
    return CatchRouteSummary(
        id = "$started-${original.name.hashCode()}",
        name = original.name,
        startedAtMillis = started,
        endedAtMillis = ended,
        activeMillis = active,
        walkedMeters = session.walkedMeters,
        caught = session.caught,
        skipped = session.visits.count { it.skipped },
        missed = session.remaining.size,
        planned = plannedIds.size,
        replans = replans,
        settings = original,
        track = session.track,
        plannedPath = thinCatchPath(session.itinerary.path.map { it.point }),
        watch = session.watch?.takeIf { it.hasData },
    )
}

/** Drops points closer than [meters] to the last kept one; ends always stay. */
internal fun thinCatchPath(points: List<CatchPoint>, meters: Double = 8.0): List<CatchPoint> {
    if (points.size <= 2) return points
    val kept = mutableListOf(points.first())
    for (p in points.subList(1, points.lastIndex)) if (catchDistance(kept.last(), p) >= meters) kept += p
    kept += points.last()
    return kept
}

/** "4.2 km/h". */
internal fun formatCatchSpeed(kmh: Double?): String = kmh?.let { String.format(Locale.getDefault(), "%.1f km/h", it) } ?: "–"

/** "3 caught · 2.1 km · 34 min". */
internal fun CatchRouteSummary.headline(): String =
    "$caught caught · ${formatCatchKm(walkedMeters)} · ${(activeMillis / 60_000).toInt()} min"

internal fun formatCatchKm(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()} m" else String.format(Locale.getDefault(), "%.1f km", meters / 1000)
