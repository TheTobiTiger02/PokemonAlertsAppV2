package com.example.pokemonalertsv2.catchroutes

import kotlin.math.*

/**
 * A walking area drawn as a polygon by tapping its corners, shared by catch routes and hunt mode.
 * Fewer than three corners means "no area": everything is allowed.
 */
internal fun List<CatchPoint>.isArea(): Boolean = size >= 3

/** Whether [point] lies inside the polygon [area] (ray casting in a local metric projection). */
internal fun pointInArea(point: CatchPoint, area: List<CatchPoint>): Boolean {
    if (!area.isArea()) return true
    val scale = cos(Math.toRadians(point.latitude))
    var inside = false
    var j = area.lastIndex
    for (i in area.indices) {
        val xi = area[i].longitude * scale; val yi = area[i].latitude
        val xj = area[j].longitude * scale; val yj = area[j].latitude
        val x = point.longitude * scale; val y = point.latitude
        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
        j = i
    }
    return inside
}

/** Metres from [point] to the nearest edge of [area]. */
internal fun distanceToAreaEdge(point: CatchPoint, area: List<CatchPoint>): Double {
    if (!area.isArea()) return 0.0
    val sx = 111_195.0 * cos(Math.toRadians(point.latitude))
    return area.indices.minOf { i ->
        val a = area[i]; val b = area[(i + 1) % area.size]
        val ax = (a.longitude - point.longitude) * sx; val ay = (a.latitude - point.latitude) * 111_195.0
        val bx = (b.longitude - point.longitude) * sx; val by = (b.latitude - point.latitude) * 111_195.0
        val dx = bx - ax; val dy = by - ay
        val t = if (dx * dx + dy * dy < 1e-9) 0.0 else (-(ax * dx + ay * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        hypot(ax + dx * t, ay + dy * t)
    }
}

/** Inside, or outside by at most [toleranceMeters]: street snapping puts paths a few metres off. */
internal fun nearArea(point: CatchPoint, area: List<CatchPoint>, toleranceMeters: Double): Boolean =
    pointInArea(point, area) || distanceToAreaEdge(point, area) <= toleranceMeters

/** Whether a walking path stays within [area], allowing [toleranceMeters] beyond its edge. */
internal fun pathInsideArea(path: List<CatchPathPosition>, area: List<CatchPoint>, toleranceMeters: Double = 20.0): Boolean =
    !area.isArea() || path.all { nearArea(it.point, area, toleranceMeters) }

/** South-west and north-east corners of the area's bounding box. */
internal fun areaBounds(area: List<CatchPoint>): Pair<CatchPoint, CatchPoint> =
    CatchPoint(area.minOf { it.latitude }, area.minOf { it.longitude }) to CatchPoint(area.maxOf { it.latitude }, area.maxOf { it.longitude })


/**
 * A finger-drawn outline turned into an area: Douglas–Peucker in a local metric projection, the tolerance
 * growing until at most [maxCorners] are left. A simplified ring that crosses itself (a figure eight, a
 * scribble) becomes its convex hull, which is always a usable area.
 */
internal fun simplifyDrawnArea(stroke: List<CatchPoint>, maxCorners: Int = CATCH_AREA_MAX_DRAWN_CORNERS, toleranceMeters: Double = 12.0): List<CatchPoint> {
    val points = stroke.filterIndexed { i, p -> i == 0 || catchDistance(stroke[i - 1], p) > 0.5 }
    if (points.size < 3) return emptyList()
    // A closed ring repeats its start; the polygon closes itself.
    val open = if (points.size > 3 && catchDistance(points.first(), points.last()) < toleranceMeters) points.dropLast(1) else points
    // A few corners a finger can work with: detail finer than a fiftieth of the outline is a wobble, not a shape.
    val (southWest, northEast) = areaBounds(open)
    var tolerance = maxOf(toleranceMeters, catchDistance(southWest, northEast) * 0.025)
    var simplified = douglasPeucker(open, tolerance)
    while (simplified.size > maxCorners && tolerance < 2_000) { tolerance *= 1.5; simplified = douglasPeucker(open, tolerance) }
    if (simplified.size < 3) simplified = convexHull(open)
    return if (areaSelfIntersects(simplified)) convexHull(simplified) else simplified
}

internal const val CATCH_AREA_MAX_DRAWN_CORNERS = 20

private fun douglasPeucker(points: List<CatchPoint>, tolerance: Double): List<CatchPoint> {
    if (points.size < 3) return points
    val origin = points.first()
    val sx = 111_195.0 * cos(Math.toRadians(origin.latitude))
    fun xy(p: CatchPoint) = (p.longitude - origin.longitude) * sx to (p.latitude - origin.latitude) * 111_195.0
    val keep = BooleanArray(points.size).also { it[0] = true; it[points.lastIndex] = true }
    fun simplify(from: Int, to: Int) {
        if (to - from < 2) return
        val (ax, ay) = xy(points[from]); val (bx, by) = xy(points[to])
        val dx = bx - ax; val dy = by - ay
        val length2 = dx * dx + dy * dy
        var worst = -1.0; var at = -1
        for (i in from + 1 until to) {
            val (px, py) = xy(points[i])
            val t = if (length2 < 1e-9) 0.0 else (((px - ax) * dx + (py - ay) * dy) / length2).coerceIn(0.0, 1.0)
            val d = hypot(px - (ax + dx * t), py - (ay + dy * t))
            if (d > worst) { worst = d; at = i }
        }
        if (worst > tolerance) { keep[at] = true; simplify(from, at); simplify(at, to) }
    }
    simplify(0, points.lastIndex)
    return points.filterIndexed { i, _ -> keep[i] }
}

/** Whether any two non-adjacent edges of the closed ring cross. */
internal fun areaSelfIntersects(area: List<CatchPoint>): Boolean {
    if (area.size < 4) return false
    val origin = area.first()
    val sx = cos(Math.toRadians(origin.latitude))
    fun x(p: CatchPoint) = p.longitude * sx
    fun y(p: CatchPoint) = p.latitude
    fun cross(o: CatchPoint, a: CatchPoint, b: CatchPoint) = (x(a) - x(o)) * (y(b) - y(o)) - (y(a) - y(o)) * (x(b) - x(o))
    fun crosses(a: CatchPoint, b: CatchPoint, c: CatchPoint, d: CatchPoint): Boolean {
        val d1 = cross(c, d, a); val d2 = cross(c, d, b); val d3 = cross(a, b, c); val d4 = cross(a, b, d)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }
    val n = area.size
    for (i in 0 until n) for (j in i + 1 until n) {
        if (j == i + 1 || (i == 0 && j == n - 1)) continue
        if (crosses(area[i], area[(i + 1) % n], area[j], area[(j + 1) % n])) return true
    }
    return false
}

/** The middle of each edge of the ring, where a new corner can be pulled out; index i sits between corners i and i+1. */
internal fun areaEdgeMidpoints(area: List<CatchPoint>): List<CatchPoint> = when {
    area.size < 2 -> emptyList()
    area.size == 2 -> listOf(midpoint(area[0], area[1]))
    else -> area.indices.map { midpoint(area[it], area[(it + 1) % area.size]) }
}

private fun midpoint(a: CatchPoint, b: CatchPoint) = CatchPoint((a.latitude + b.latitude) / 2, (a.longitude + b.longitude) / 2)

/**
 * Adds [point] to [area]. Until there is a polygon it goes on the end; after that it goes into the edge
 * it lengthens least, so a tap beside the outline bends that edge instead of crossing the shape.
 */
internal fun insertAreaCorner(area: List<CatchPoint>, point: CatchPoint): List<CatchPoint> {
    if (area.size < 3) return area + point
    val at = area.indices.minBy { i ->
        val a = area[i]; val b = area[(i + 1) % area.size]
        catchDistance(a, point) + catchDistance(point, b) - catchDistance(a, b)
    } + 1
    return area.take(at) + point + area.drop(at)
}
