package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.catchroutes.CatchPathGeometry
import com.example.pokemonalertsv2.catchroutes.CatchPathResponse
import com.example.pokemonalertsv2.catchroutes.CatchPoint
import com.example.pokemonalertsv2.data.RouteMatrixPoint

/** As many stops as the hunt draws a street path for; well inside the path endpoint's 30-point limit. */
internal const val HUNT_PATH_MAX_LEGS = 8

/**
 * The walk the hunt is suggesting, as streets rather than straight lines.
 *
 * [Resolved.next] is the leg being walked right now, drawn on top in its own colour, and [Resolved.rest]
 * is everything after it. Splitting here keeps the map renderer free of filtering.
 */
internal sealed interface HuntPath {
    data object None : HuntPath

    data class Resolved(
        val next: List<CatchPoint>,
        val rest: List<CatchPoint>,
        val calculatedAtMillis: Long,
        /** The stops this line walks through, in order; a line for any other order must not be drawn. */
        val stopIds: List<String> = emptyList()
    ) : HuntPath
}

/** The chain a path covers: the trainer, then the numbered stops, capped at [HUNT_PATH_MAX_LEGS]. */
internal fun huntPathChain(origin: HuntRoutePoint, stops: List<HuntRoutePoint>): List<HuntRoutePoint> {
    val seen = mutableSetOf(huntLegNode(origin.latitude, origin.longitude))
    val usable = stops.asSequence()
        .filter { it.latitude.isFinite() && it.longitude.isFinite() }
        .filter { it.latitude != 0.0 || it.longitude != 0.0 }
        .filter { seen.add(huntLegNode(it.latitude, it.longitude)) }
        .take(HUNT_PATH_MAX_LEGS)
        .toList()
    return if (usable.isEmpty()) emptyList() else listOf(origin) + usable
}

/** The legs of [chain], as cache keys, in walking order. */
internal fun huntPathLegKeys(chain: List<HuntRoutePoint>): List<HuntLegKey> =
    chain.map { huntLegNode(it.latitude, it.longitude) }
        .zipWithNext { from, to -> HuntLegKey(from, to) }

/**
 * The shortest stretch of [chain] that has to be asked for, as an index range into the chain's points, or
 * null when every leg is already known.
 *
 * One request per refresh, and the usual refresh — the trainer walked on, so only the leg from them to the
 * next stop changed — asks for two points. The rest of the route is stitched from legs already held.
 */
internal fun huntPathRequestSpan(chain: List<HuntRoutePoint>, known: Set<HuntLegKey>): IntRange? {
    if (chain.size < 2) return null
    val legs = huntPathLegKeys(chain)
    val first = legs.indexOfFirst { it !in known }
    if (first < 0) return null
    val last = legs.indexOfLast { it !in known }
    return first..(last + 1)
}

/**
 * The published path for [chain], stitched from [legs], or [HuntPath.None] when a leg is missing.
 *
 * A half-drawn route would read as "the walk ends here", which is worse than no line at all.
 */
internal fun huntPathFrom(chain: List<HuntRoutePoint>, legs: Map<HuntLegKey, List<CatchPoint>>, nowMillis: Long): HuntPath {
    val keys = huntPathLegKeys(chain)
    if (keys.isEmpty()) return HuntPath.None
    val resolved = keys.map { legs[it] ?: return HuntPath.None }
    val rest = mutableListOf<CatchPoint>()
    for (leg in resolved.drop(1)) {
        if (rest.isEmpty()) rest += leg else rest += leg.drop(1)
    }
    return HuntPath.Resolved(next = resolved.first(), rest = rest, calculatedAtMillis = nowMillis, stopIds = chain.drop(1).map { it.id })
}

/**
 * The response's legs as geometry per leg key, or null when it does not describe the points that were
 * asked for. Coordinates arrive as GeoJSON `[longitude, latitude]`; everything else in the app is
 * latitude-first, so this is the only place that order is untangled.
 */
internal fun parseHuntPath(response: CatchPathResponse, points: List<RouteMatrixPoint>): Map<HuntLegKey, List<CatchPoint>>? {
    if (response.status != "ok" || response.legs.size != points.size - 1) return null
    if (response.snappedPoints.map { it.id } != points.map { it.id }) return null
    val nodes = points.map { huntLegNode(it.latitude, it.longitude) }
    val legs = HashMap<HuntLegKey, List<CatchPoint>>(response.legs.size)
    response.legs.forEachIndexed { index, leg ->
        if (leg.fromId != points[index].id || leg.toId != points[index + 1].id) return null
        val geometry = leg.geometry.takeIf { it.type == "LineString" } ?: return null
        val line = geometry.points() ?: return null
        legs[HuntLegKey(nodes[index], nodes[index + 1])] = line
    }
    return legs
}

/** A LineString's coordinates as points, or null when a coordinate is malformed. */
private fun CatchPathGeometry.points(): List<CatchPoint>? {
    if (coordinates.size < 2) return null
    return coordinates.map { pair ->
        if (pair.size != 2) return null
        CatchPoint(pair[1], pair[0]).takeIf { it.valid } ?: return null
    }
}
