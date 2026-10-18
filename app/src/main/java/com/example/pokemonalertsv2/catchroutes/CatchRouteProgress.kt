package com.example.pokemonalertsv2.catchroutes

import kotlin.math.*

/** Processes measured fixes only. Between two fixes close in time, the straight line walked counts too. */
class CatchRouteProgress {
    private var previousAt = 0L
    private var previousPoint: CatchPoint? = null
    private var candidates = emptyMap<String, Long>()
    /** When a fix last sat on the route near the known progress; long enough off it and progress is searched for again. */
    private var lastOnRouteAt = 0L
    fun reset() { previousAt = 0; previousPoint = null; candidates = emptyMap(); lastOnRouteAt = 0 }
    fun accept(session: CatchSession, point: CatchPoint, accuracy: Double, fixAt: Long, now: Long): CatchSession {
        if (session.paused || session.finished || !point.valid || !accuracy.isFinite() || accuracy !in 0.0..40.0 ||
            now - fixAt !in 0..10_000 || fixAt <= previousAt || fixAt < session.itinerary.settings.startAtMillis ||
            fixAt >= session.itinerary.settings.endAtMillis) return session
        val radius = session.itinerary.settings.radius
        val inRange = session.remaining.filter { e ->
            fixAt >= e.opportunity.availableFrom && fixAt < e.opportunity.despawnAt &&
                catchDistance(point, e.opportunity.point) <= radius
        }
        val contiguous = previousAt > 0 && fixAt - previousAt in 1..15_000
        candidates = inRange.associate { it.opportunity.id to (if (contiguous) candidates[it.opportunity.id] ?: fixAt else fixAt) }
        val dwelt = inRange.filter { fixAt - candidates.getValue(it.opportunity.id) >= 2_000 }
        // A circle crossed between two fixes, while its window was open, was walked through even
        // though neither fix landed inside long enough: fixes only arrive every few metres.
        val from = previousPoint
        val crossed = if (contiguous && from != null) session.remaining.filter { e ->
            e !in dwelt && catchCircleInterval(from, point, e.opportunity.point, radius)?.let { t ->
                val enter = previousAt + ((fixAt - previousAt) * t.start).toLong()
                val leave = previousAt + ((fixAt - previousAt) * t.endInclusive).toLong()
                min(leave, e.opportunity.despawnAt) - max(enter, e.opportunity.availableFrom) >= 2_000
            } == true
        } else emptyList()
        val visited = (dwelt + crossed).distinctBy { it.opportunity.id }.map { CatchVisit(it.opportunity, fixAt) }
        previousAt = fixAt
        previousPoint = point
        val path = session.itinerary.path
        val near = nearestCatchProgress(path, point, session.progressMeters)
        val projected = when {
            near != null -> { lastOnRouteAt = fixAt; near }
            // Off the stretch around the known progress for a while: a detour, a shortcut or a GPS gap.
            // Pick the route up again wherever the trainer has rejoined it, ahead of where they were.
            lastOnRouteAt > 0 && fixAt - lastOnRouteAt >= CATCH_PROGRESS_REACQUIRE_MILLIS ->
                rejoinCatchProgress(path, point, session.progressMeters)?.also { lastOnRouteAt = fixAt } ?: session.progressMeters
            else -> { if (lastOnRouteAt == 0L) lastOnRouteAt = fixAt; session.progressMeters }
        }
        return session.copy(visits = session.visits + visited, progressMeters = projected)
    }
}

/** Off the route around its known progress for this long before the whole route is searched. */
internal const val CATCH_PROGRESS_REACQUIRE_MILLIS = 20_000L

fun projectCatchProgress(path: List<CatchPathPosition>, point: CatchPoint, previousMeters: Double): Double =
    nearestCatchProgress(path, point, previousMeters) ?: previousMeters

/** Progress near [previousMeters] the fix projects onto, or null when no nearby stretch of route is within 60 m. */
internal fun nearestCatchProgress(path: List<CatchPathPosition>, point: CatchPoint, previousMeters: Double): Double? {
    var best: Double? = null
    var closest = 60.0
    // Restrict to nearby progress to prevent jumping to a future lap at a crossing.
    path.zipWithNext().forEach { (a, b) ->
        if (b.meters < previousMeters - 30 || a.meters > previousMeters + 250) return@forEach
        val (meters, d) = projectOnLeg(a, b, point)
        if (d < closest) { closest = d; best = max(previousMeters, meters) }
    }
    return best
}

/** The first place ahead of [previousMeters] where the route passes within 30 m of the fix. */
internal fun rejoinCatchProgress(path: List<CatchPathPosition>, point: CatchPoint, previousMeters: Double): Double? {
    for ((a, b) in path.zipWithNext()) {
        if (b.meters < previousMeters) continue
        val (meters, d) = projectOnLeg(a, b, point)
        if (d <= 30.0) return max(previousMeters, meters)
    }
    return null
}

private fun projectOnLeg(a: CatchPathPosition, b: CatchPathPosition, point: CatchPoint): Pair<Double, Double> {
    val scale = cos(Math.toRadians(point.latitude))
    val dx = (b.point.longitude - a.point.longitude) * scale
    val dy = b.point.latitude - a.point.latitude
    val t = if (dx * dx + dy * dy < 1e-18) 0.0 else
        (((point.longitude - a.point.longitude) * scale * dx + (point.latitude - a.point.latitude) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
    val projected = CatchPoint(a.point.latitude + (b.point.latitude - a.point.latitude) * t, a.point.longitude + (b.point.longitude - a.point.longitude) * t)
    return (a.meters + (b.meters - a.meters) * t) to catchDistance(point, projected)
}
