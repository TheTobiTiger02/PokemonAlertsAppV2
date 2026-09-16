package com.example.pokemonalertsv2.catchroutes

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/**
 * A catch route as a Google Maps walking link.
 *
 * Google Maps takes an origin, a destination and at most [MAX_WAYPOINTS] points in between, while a
 * route has hundreds of path points and can have far more stops, so the link keeps the places where
 * Pokémon are caught first and fills the rest with the corners that carry the route's shape. Maps then
 * walks its own streets between them, which is close enough to follow but never exactly the planned path.
 */
internal const val MAX_WAYPOINTS = 9

/** Two waypoints closer than this are the same place to a walker. */
private const val WAYPOINT_SPACING_METERS = 40.0

/** A waypoint this close to the start or the finish would only repeat them. */
private const val ENDPOINT_MARGIN_METERS = 25.0

/** The point [meters] along [path], interpolated inside the segment it falls in. */
internal fun catchPathPointAt(path: List<CatchPathPosition>, meters: Double): CatchPoint {
    if (path.isEmpty()) return CatchPoint(0.0, 0.0)
    val index = path.indexOfFirst { it.meters >= meters }
    if (index <= 0) return path.first().point
    val before = path[index - 1]
    val after = path[index]
    val span = after.meters - before.meters
    val t = if (span <= 0.0) 0.0 else ((meters - before.meters) / span).coerceIn(0.0, 1.0)
    return CatchPoint(before.point.latitude + (after.point.latitude - before.point.latitude) * t,
        before.point.longitude + (after.point.longitude - before.point.longitude) * t)
}

/**
 * Indices of the [keep] points that carry the shape of [path] (Douglas-Peucker, iterative): the two ends,
 * then repeatedly the point furthest from the line it currently sits between.
 */
internal fun douglasPeuckerIndices(path: List<CatchPathPosition>, keep: Int): List<Int> {
    if (path.size <= 2 || keep <= 2) return path.indices.take(keep.coerceAtMost(path.size)).toList()
    val chosen = sortedSetOf(0, path.lastIndex)
    while (chosen.size < keep.coerceAtMost(path.size)) {
        var bestIndex = -1
        var bestDeviation = 0.0
        val bounds = chosen.toList()
        for ((from, to) in bounds.zipWithNext()) {
            for (i in from + 1 until to) {
                val deviation = perpendicularMeters(path[i].point, path[from].point, path[to].point)
                if (deviation > bestDeviation) { bestDeviation = deviation; bestIndex = i }
            }
        }
        if (bestIndex < 0 || bestDeviation <= 1.0) break
        chosen += bestIndex
    }
    return chosen.toList()
}

/** Distance from [point] to the segment [a]–[b], in metres, in a local flat projection. */
private fun perpendicularMeters(point: CatchPoint, a: CatchPoint, b: CatchPoint): Double {
    val scale = 111_195.0 * cos(Math.toRadians(a.latitude)).coerceAtLeast(0.01)
    val px = (point.longitude - a.longitude) * scale
    val py = (point.latitude - a.latitude) * 111_195.0
    val bx = (b.longitude - a.longitude) * scale
    val by = (b.latitude - a.latitude) * 111_195.0
    val length = hypot(bx, by)
    if (length < 0.01) return hypot(px, py)
    return abs(px * by - py * bx) / length
}

/**
 * Up to [max] waypoints along [path], in walking order: the numbered stops (thinned evenly when there are
 * more than fit), then shape corners where there is room left. Points at the very start or finish are left
 * out, because Maps already has those as origin and destination.
 */
internal fun catchRouteWaypoints(path: List<CatchPathPosition>, stopMeters: List<Double>, max: Int = MAX_WAYPOINTS): List<CatchPoint> {
    if (path.size < 2 || max <= 0) return emptyList()
    val total = path.last().meters
    val usableStops = stopMeters.filter { it > ENDPOINT_MARGIN_METERS && it < total - ENDPOINT_MARGIN_METERS }.sorted()
    val chosen = mutableListOf<Double>()
    if (usableStops.size > max) {
        for (i in 0 until max) chosen += usableStops[Math.round(i.toDouble() * (usableStops.size - 1) / (max - 1)).toInt()]
    } else {
        chosen += usableStops
    }
    if (chosen.size < max) {
        for (index in douglasPeuckerIndices(path, max * 3)) {
            if (chosen.size >= max) break
            val meters = path[index].meters
            if (meters <= ENDPOINT_MARGIN_METERS || meters >= total - ENDPOINT_MARGIN_METERS) continue
            if (chosen.none { abs(it - meters) < WAYPOINT_SPACING_METERS }) chosen += meters
        }
    }
    return chosen.distinct().sorted().map { catchPathPointAt(path, it) }
}

/** A `maps/dir` walking link. Coordinates are formatted in [Locale.US]: a decimal comma breaks the URL. */
internal fun googleMapsWalkingUrl(origin: CatchPoint, destination: CatchPoint, waypoints: List<CatchPoint>): String {
    fun coordinate(point: CatchPoint) = String.format(Locale.US, "%.6f,%.6f", point.latitude, point.longitude)
    return buildString {
        append("https://www.google.com/maps/dir/?api=1")
        append("&origin=").append(coordinate(origin))
        append("&destination=").append(coordinate(destination))
        append("&travelmode=walking")
        if (waypoints.isNotEmpty()) append("&waypoints=").append(waypoints.joinToString("%7C", transform = ::coordinate))
    }
}

/** The whole itinerary as one Google Maps link, or null when there is no path to walk. */
internal fun catchRouteMapsUrl(itinerary: CatchItinerary): String? {
    val path = itinerary.path
    if (path.size < 2) return null
    return googleMapsWalkingUrl(path.first().point, path.last().point,
        catchRouteWaypoints(path, catchStopMeters(itinerary.encounters)))
}
