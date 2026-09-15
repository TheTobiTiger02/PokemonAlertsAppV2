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
