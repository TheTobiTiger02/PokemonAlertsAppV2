package com.example.pokemonalertsv2.catchroutes

import kotlin.math.*

/** Processes measured fixes only. No interpolation across a missed GPS interval. */
class CatchRouteProgress {
    private var previousAt = 0L
    private var candidates = emptyMap<String, Long>()
    fun reset() { previousAt = 0; candidates = emptyMap() }
    fun accept(session: CatchSession, point: CatchPoint, accuracy: Double, fixAt: Long, now: Long): CatchSession {
        if (session.paused || session.finished || !point.valid || !accuracy.isFinite() || accuracy !in 0.0..40.0 ||
            now - fixAt !in 0..10_000 || fixAt <= previousAt || fixAt < session.itinerary.settings.startAtMillis ||
            fixAt >= session.itinerary.settings.endAtMillis) return session
        val inRange = session.remaining.filter { e ->
            fixAt >= e.opportunity.availableFrom && fixAt < e.opportunity.despawnAt &&
                catchDistance(point, e.opportunity.point) <= session.itinerary.settings.radius
        }
        val contiguous = previousAt > 0 && fixAt - previousAt in 1..15_000
        candidates = inRange.associate { it.opportunity.id to (if (contiguous) candidates[it.opportunity.id] ?: fixAt else fixAt) }
        val visited = inRange.filter { fixAt - candidates.getValue(it.opportunity.id) >= 2_000 }.map { CatchVisit(it.opportunity, fixAt) }
        previousAt = fixAt
        val projected = projectCatchProgress(session.itinerary.path, point, session.progressMeters)
        return session.copy(visits = session.visits + visited, progressMeters = projected)
    }
}

fun projectCatchProgress(path: List<CatchPathPosition>, point: CatchPoint, previousMeters: Double): Double {
    var best = previousMeters
    var closest = 60.0
    // Restrict to nearby progress to prevent jumping to a future lap at a crossing.
    path.zipWithNext().forEach { (a, b) ->
        if (b.meters < previousMeters - 30 || a.meters > previousMeters + 250) return@forEach
        val scale = cos(Math.toRadians(point.latitude))
        val dx = (b.point.longitude - a.point.longitude) * scale
        val dy = b.point.latitude - a.point.latitude
        val t = if (dx * dx + dy * dy < 1e-18) 0.0 else
            (((point.longitude - a.point.longitude) * scale * dx + (point.latitude - a.point.latitude) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        val projected = CatchPoint(a.point.latitude + (b.point.latitude - a.point.latitude) * t, a.point.longitude + (b.point.longitude - a.point.longitude) * t)
        val d = catchDistance(point, projected)
        if (d < closest) { closest = d; best = max(previousMeters, a.meters + (b.meters - a.meters) * t) }
    }
    return best
}
