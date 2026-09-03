package com.example.pokemonalertsv2.util

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Just enough of Google's S2 geometry to draw one weather cell.
 *
 * Pokémon GO reads weather per S2 level-10 cell, so a cell outline is the honest shape for
 * "the weather here" — a circle or a bounding box would imply a precision the game does not
 * have. Only the projection is implemented: a point to the cell containing it, and a cell back
 * to its outline. Cell ids, neighbours, coverings and the Hilbert curve are all absent because
 * nothing here needs them, and the full library is a dependency this app would use 1% of.
 *
 * The maths is the standard S2 pipeline: lat/lng to a vector on the unit sphere, that vector to
 * one of the cube's six faces plus (u, v) coordinates on it, and (u, v) to (s, t) in [0,1]
 * through the *quadratic* projection — the same one S2 itself uses, which trades a little
 * arithmetic for cells of far more even area than the naive linear or tangent projections.
 */

/** Pokémon GO resolves weather at level 10 — cells roughly 10km across. */
const val WEATHER_CELL_LEVEL = 10

data class S2LatLng(val latitude: Double, val longitude: Double)

/**
 * One cell, as the face it lives on plus its integer position at [level].
 *
 * Deliberately not an S2 cell id: the 64-bit Hilbert-interleaved form buys ordering and
 * containment tricks that nothing here uses, and (face, i, j) is directly what the boundary
 * walk needs. It still equals/hashes correctly, so it works as a map key.
 */
data class S2CellRef(val face: Int, val i: Int, val j: Int, val level: Int) {
    internal val cellsPerSide: Int get() = 1 shl level
}

/** The cell containing the given point. */
fun s2CellAt(latitude: Double, longitude: Double, level: Int = WEATHER_CELL_LEVEL): S2CellRef {
    require(level in 0..30) { "S2 level must be 0..30, was $level" }
    val (x, y, z) = latLngToXyz(latitude, longitude)
    val face = faceOf(x, y, z)
    val (u, v) = faceXyzToUv(face, x, y, z)
    val cellsPerSide = 1 shl level
    return S2CellRef(
        face = face,
        i = stToIndex(uvToSt(u), cellsPerSide),
        j = stToIndex(uvToSt(v), cellsPerSide),
        level = level
    )
}

/**
 * The cell's outline, closed (last point equals the first) and wound consistently.
 *
 * [pointsPerEdge] subdivides each side. A level-10 cell spans ~10km, and its edges are geodesics
 * rather than straight lines in any map projection, so joining the four corners directly leaves
 * visible bowing against the tiles. Eight segments a side is indistinguishable from the true arc
 * at any zoom this is drawn at.
 */
fun S2CellRef.boundary(pointsPerEdge: Int = 8): List<S2LatLng> {
    val steps = pointsPerEdge.coerceAtLeast(1)
    val lowS = i.toDouble() / cellsPerSide
    val highS = (i + 1).toDouble() / cellsPerSide
    val lowT = j.toDouble() / cellsPerSide
    val highT = (j + 1).toDouble() / cellsPerSide

    val points = ArrayList<S2LatLng>(steps * 4 + 1)
    fun walk(fromS: Double, fromT: Double, toS: Double, toT: Double) {
        for (step in 0 until steps) {
            val fraction = step.toDouble() / steps
            points += stToLatLng(
                face,
                fromS + (toS - fromS) * fraction,
                fromT + (toT - fromT) * fraction
            )
        }
    }
    walk(lowS, lowT, highS, lowT)
    walk(highS, lowT, highS, highT)
    walk(highS, highT, lowS, highT)
    walk(lowS, highT, lowS, lowT)
    points += points.first()
    return points
}

/** The cell's centre, for placing a label inside it. */
fun S2CellRef.centre(): S2LatLng = stToLatLng(
    face,
    (i + 0.5) / cellsPerSide,
    (j + 0.5) / cellsPerSide
)

// ------------------------------------------------------------------ projection internals

private fun latLngToXyz(latitude: Double, longitude: Double): Triple<Double, Double, Double> {
    val phi = Math.toRadians(latitude)
    val theta = Math.toRadians(longitude)
    val cosPhi = cos(phi)
    return Triple(cosPhi * cos(theta), cosPhi * sin(theta), sin(phi))
}

/**
 * Vector to lat/lng. Uses atan2 against the horizontal magnitude rather than asin(z), so it
 * stays accurate near the poles and does not require the vector to be normalised — which
 * matters because [faceUvToXyz] hands back un-normalised cube-face vectors.
 */
private fun xyzToLatLng(x: Double, y: Double, z: Double): S2LatLng = S2LatLng(
    latitude = Math.toDegrees(atan2(z, sqrt(x * x + y * y))),
    longitude = Math.toDegrees(atan2(y, x))
)

/** The cube face a vector points at: the largest-magnitude axis, offset by 3 when negative. */
private fun faceOf(x: Double, y: Double, z: Double): Int {
    val absX = abs(x)
    val absY = abs(y)
    val absZ = abs(z)
    return when {
        absX >= absY && absX >= absZ -> if (x < 0) 3 else 0
        absY >= absZ -> if (y < 0) 4 else 1
        else -> if (z < 0) 5 else 2
    }
}

private fun faceXyzToUv(face: Int, x: Double, y: Double, z: Double): Pair<Double, Double> =
    when (face) {
        0 -> (y / x) to (z / x)
        1 -> (-x / y) to (z / y)
        2 -> (-x / z) to (-y / z)
        3 -> (z / x) to (y / x)
        4 -> (z / y) to (-x / y)
        else -> (-y / z) to (-x / z)
    }

private fun faceUvToXyz(face: Int, u: Double, v: Double): Triple<Double, Double, Double> =
    when (face) {
        0 -> Triple(1.0, u, v)
        1 -> Triple(-u, 1.0, v)
        2 -> Triple(-u, -v, 1.0)
        3 -> Triple(-1.0, -v, -u)
        4 -> Triple(v, -1.0, -u)
        else -> Triple(v, u, -1.0)
    }

/** Quadratic projection, [-1,1] -> [0,1]. */
private fun uvToSt(u: Double): Double =
    if (u >= 0) 0.5 * sqrt(1.0 + 3.0 * u) else 1.0 - 0.5 * sqrt(1.0 - 3.0 * u)

/** Quadratic projection, [0,1] -> [-1,1]. Inverse of [uvToSt]. */
private fun stToUv(s: Double): Double =
    if (s >= 0.5) (4.0 * s * s - 1.0) / 3.0 else (1.0 - 4.0 * (1.0 - s) * (1.0 - s)) / 3.0

private fun stToIndex(s: Double, cellsPerSide: Int): Int =
    floor(s * cellsPerSide).toInt().coerceIn(0, cellsPerSide - 1)

private fun stToLatLng(face: Int, s: Double, t: Double): S2LatLng {
    val (x, y, z) = faceUvToXyz(face, stToUv(s), stToUv(t))
    return xyzToLatLng(x, y, z)
}
