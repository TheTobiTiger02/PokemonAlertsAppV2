package com.example.pokemonalertsv2.catchroutes

/** Display geometry only. Never used by scoring, distance, or visit tracking. */
internal data class CatchDrawMarker(val point: CatchPoint, val label: String, val meters: Double = 0.0)

/** What the route map pins on the line: numbered [stops] where catches happen, and the two [endpoints]. */
internal data class CatchRouteDrawing(
    val stops: List<CatchDrawMarker>,
    val endpoints: List<CatchDrawMarker>,
)

/**
 * Places the numbered stops and endpoints of a routed [path]. [stopMeters] are the positions along
 * the path of the places where Pokémon are caught, in walking order.
 */
internal fun catchRouteDrawing(path: List<CatchPathPosition>, stopMeters: List<Double> = emptyList()): CatchRouteDrawing {
    if (path.isEmpty()) return CatchRouteDrawing(emptyList(), emptyList())
    val stops = stopMeters.sorted().mapIndexed { i, meters -> CatchDrawMarker(catchPointAt(path, meters), (i + 1).toString(), meters) }
    val closed = catchDistance(path.first().point, path.last().point) < 10
    val endpoints = if (closed) listOf(CatchDrawMarker(path.first().point, "Start / Finish"))
        else listOf(CatchDrawMarker(path.first().point, "Start"), CatchDrawMarker(path.last().point, "Finish", path.last().meters))
    return CatchRouteDrawing(stops, endpoints)
}

/**
 * The route cut into what is already [walked], the [next] leg to walk now, and everything [later].
 * Three lines, whatever the route's length, so redrawing on every fix stays cheap.
 */
internal data class CatchRouteSlices(val walked: List<CatchPoint>, val next: List<CatchPoint>, val later: List<CatchPoint>)

/**
 * Slices [path] at [walkedMeters] and [nextMeters]. A preview ([walkedMeters] null) is all "next":
 * the whole plan is what you would walk. Without a stop ahead the next leg runs to the finish.
 */
internal fun catchRouteSlices(path: List<CatchPathPosition>, walkedMeters: Double?, nextMeters: Double?): CatchRouteSlices {
    if (path.size < 2) return CatchRouteSlices(emptyList(), emptyList(), emptyList())
    val end = path.last().meters
    val walkedTo = (walkedMeters ?: 0.0).coerceIn(0.0, end)
    val nextTo = if (walkedMeters == null || nextMeters == null) end else nextMeters.coerceIn(walkedTo, end)
    return CatchRouteSlices(catchPathSlice(path, 0.0, walkedTo), catchPathSlice(path, walkedTo, nextTo), catchPathSlice(path, nextTo, end))
}

/** The part of [path] between [from] and [to] metres, with interpolated ends; empty when shorter than a metre. */
internal fun catchPathSlice(path: List<CatchPathPosition>, from: Double, to: Double): List<CatchPoint> {
    if (path.size < 2 || to - from < 1.0) return emptyList()
    return listOf(catchPointAt(path, from)) + path.filter { it.meters > from && it.meters < to }.map { it.point } + catchPointAt(path, to)
}

/** Where along [path] the walk is after [meters]; clamped to its ends. */
internal fun catchPointAt(path: List<CatchPathPosition>, meters: Double): CatchPoint {
    if (meters <= path.first().meters) return path.first().point
    val i = path.indexOfFirst { it.meters >= meters }
    if (i < 0) return path.last().point
    val a = path[i - 1]; val b = path[i]
    val t = ((meters - a.meters) / (b.meters - a.meters).coerceAtLeast(0.001)).coerceIn(0.0, 1.0)
    return CatchPoint(a.point.latitude + (b.point.latitude - a.point.latitude) * t, a.point.longitude + (b.point.longitude - a.point.longitude) * t)
}

/** How much route one numbered stop covers: catches within this stretch share a number. */
internal const val CATCH_STOP_SPAN_METERS = 150.0

/**
 * The route's numbered stops: catches grouped by walking order into stretches of at most
 * [CATCH_STOP_SPAN_METERS]. Few enough to read on the map, and the same list the stop timeline shows.
 */
internal fun catchStops(encounters: List<CatchEncounter>): List<List<CatchEncounter>> {
    val stops = mutableListOf<MutableList<CatchEncounter>>()
    for (e in encounters.sortedBy { it.meters }) {
        val stop = stops.lastOrNull()
        if (stop == null || e.meters - stop.first().meters > CATCH_STOP_SPAN_METERS) stops += mutableListOf(e) else stop += e
    }
    return stops
}

/** The middle of each stop's stretch: where its numbered badge sits. */
internal fun catchStopMeters(encounters: List<CatchEncounter>): List<Double> = catchStops(encounters).map { (it.first().meters + it.last().meters) / 2 }

/**
 * Where the leg to the next stop ends, in metres along the route: at that stop's numbered badge,
 * so the bold line, Follow and the distance all point at the same place. Once the badge is behind
 * the walk, the catch still ahead in that stop. Null when nothing is ahead.
 */
internal fun CatchSession.nextLegEnd(now: Long): Double? {
    val next = nextAt(now) ?: return null
    val stop = catchStops(itinerary.encounters).firstOrNull { s -> s.any { it.opportunity.id == next.opportunity.id } }
        ?: return next.meters
    val badge = (stop.first().meters + stop.last().meters) / 2
    return if (badge > progressMeters) badge else next.meters
}
