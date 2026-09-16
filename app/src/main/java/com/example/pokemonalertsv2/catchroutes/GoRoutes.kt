package com.example.pokemonalertsv2.catchroutes

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.QueryMap
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** The backend's Pokémon GO route catalogue (see ROUTES.md in the backend). */
interface GoRoutesService {
    @GET("api/routes") suspend fun routes(@QueryMap query: Map<String, String>): Response<GoRouteList>
    @GET("api/routes/{id}") suspend fun route(@Path("id") id: String): Response<GoRouteRecord>
}

@Serializable data class GoRouteList(val count: Int = 0, val data: List<GoRouteSummary> = emptyList())

@Serializable data class GoRouteSummary(
    val id: String,
    val name: String = "Route",
    val distanceMeters: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val reversible: Int = 0,
    val startLatitude: Double,
    val startLongitude: Double,
    val endLatitude: Double? = null,
    val endLongitude: Double? = null,
    val imageUrl: String? = null,
    val area: String? = null,
) {
    val start: CatchPoint get() = CatchPoint(startLatitude, startLongitude)
}

@Serializable data class GoRouteRecord(
    val id: String,
    val name: String = "Route",
    val description: String? = null,
    val distanceMeters: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val reversible: Int = 0,
    val imageUrl: String? = null,
    val area: String? = null,
    /** Ordered `[latitude, longitude]` pairs: the opposite order to the routing endpoints' GeoJSON. */
    val geometry: List<List<Double>> = emptyList(),
) {
    val canReverse: Boolean get() = reversible == 1

    /** The polyline as points, or empty when any pair is malformed; a partial route is not a route. */
    fun points(reverse: Boolean = false): List<CatchPoint> {
        val parsed = geometry.map { pair ->
            if (pair.size != 2) return emptyList()
            CatchPoint(pair[0], pair[1]).takeIf { it.valid } ?: return emptyList()
        }
        val deduped = parsed.filterIndexed { i, p -> i == 0 || catchDistance(parsed[i - 1], p) > 0.2 }
        return if (reverse) deduped.reversed() else deduped
    }
}

/** Nearby routes and route details, with the backend's 404 turned into a message people can act on. */
class GoRouteRepository(private val service: GoRoutesService) {
    private val details = object : LinkedHashMap<String, GoRouteRecord>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GoRouteRecord>?) = size > 20
    }

    /** Routes starting within [radiusMeters] of [around], nearest first. */
    suspend fun nearby(around: CatchPoint, radiusMeters: Double = 3_000.0): List<GoRouteSummary> {
        val latPad = radiusMeters / 111_195.0
        val lonPad = latPad / cos(Math.toRadians(around.latitude)).coerceAtLeast(0.01)
        val query = mapOf(
            "south" to (around.latitude - latPad).toString(), "north" to (around.latitude + latPad).toString(),
            "west" to (around.longitude - lonPad).toString(), "east" to (around.longitude + lonPad).toString(),
        )
        return service.routes(query).catchBody().data
            .filter { it.start.valid }
            .sortedBy { catchDistance(it.start, around) }
    }

    suspend fun detail(id: String): GoRouteRecord {
        synchronized(details) { details[id] }?.let { return it }
        val response = service.route(id)
        if (response.code() == 404) throw CatchApiException("That route is no longer published.")
        val record = response.catchBody()
        if (record.points().size < 2) throw CatchApiException("That route has no usable path.")
        synchronized(details) { details[id] = record }
        return record
    }
}

/** Settings that walk [record] exactly as drawn, for as long as it takes at the chosen pace. */
fun CatchRouteSettings.walking(record: GoRouteRecord, reverse: Boolean = false): CatchRouteSettings {
    val points = record.points(reverse)
    val length = fixedCatchPath(points).lastOrNull()?.meters ?: 0.0
    return copy(
        name = record.name.take(80), start = points.first(), finish = CatchFinish.ANYWHERE, end = null, area = emptyList(),
        durationMinutes = ceil(length / speedMps / 60.0).toInt().coerceIn(10, 360),
        fixedPath = points, sourceRouteId = record.id, sourceRouteName = record.name,
    )
}

/** Settings that let the optimizer plan freely, but only near [record]. */
fun CatchRouteSettings.guidedBy(record: GoRouteRecord): CatchRouteSettings {
    val points = record.points()
    return copy(start = points.first(), area = bufferedRouteArea(points), fixedPath = emptyList(),
        sourceRouteId = record.id, sourceRouteName = record.name)
}

/** Back to ordinary planning; a guide's area goes with it. */
fun CatchRouteSettings.withoutImport(): CatchRouteSettings = if (sourceRouteId == null) this else
    copy(fixedPath = emptyList(), sourceRouteId = null, sourceRouteName = null, area = if (fixed) area else emptyList())

/** Cumulative metres along [points]. */
internal fun fixedCatchPath(points: List<CatchPoint>): List<CatchPathPosition> {
    var meters = 0.0
    return points.mapIndexed { i, point ->
        if (i > 0) meters += catchDistance(points[i - 1], point)
        CatchPathPosition(point, meters)
    }
}

/** [path] cut at [maxMeters], ending on an interpolated point rather than overshooting. */
internal fun truncateCatchPath(path: List<CatchPathPosition>, maxMeters: Double): List<CatchPathPosition> {
    if (path.isEmpty() || path.last().meters <= maxMeters) return path
    val kept = path.takeWhile { it.meters <= maxMeters }
    val next = path[kept.size]
    val previous = kept.last()
    if (maxMeters - previous.meters < 0.01 || next.meters <= previous.meters) return kept
    return kept + CatchPathPosition(catchPathPointAt(path, maxMeters), maxMeters)
}

/**
 * What remains of a fixed route for a trainer standing at [at]: from the nearest point on the route
 * onward, starting at the trainer. Only the part of the route after [walkedMeters] is searched, so a
 * loop that passes the same street twice does not jump the trainer back to its start.
 */
internal fun remainingFixedPath(points: List<CatchPoint>, at: CatchPoint, walkedMeters: Double = 0.0): List<CatchPoint> {
    val path = fixedCatchPath(points)
    if (path.size < 2) return points
    var bestIndex = 0
    var bestDistance = Double.MAX_VALUE
    var bestPoint = path.first().point
    for (i in 0 until path.lastIndex) {
        val a = path[i]; val b = path[i + 1]
        if (b.meters < walkedMeters - 50) continue
        val t = projectOnSegment(a.point, b.point, at)
        val point = CatchPoint(a.point.latitude + (b.point.latitude - a.point.latitude) * t, a.point.longitude + (b.point.longitude - a.point.longitude) * t)
        val distance = catchDistance(point, at)
        if (distance < bestDistance) { bestDistance = distance; bestIndex = i; bestPoint = point }
    }
    val remaining = listOf(at, bestPoint) + points.drop(bestIndex + 1)
    return remaining.fold(mutableListOf<CatchPoint>()) { kept, p ->
        if (kept.isEmpty() || catchDistance(kept.last(), p) > 0.2) kept += p
        kept
    }
}

private fun projectOnSegment(a: CatchPoint, b: CatchPoint, p: CatchPoint): Double {
    val scale = cos(Math.toRadians(a.latitude))
    val dx = (b.longitude - a.longitude) * scale; val dy = b.latitude - a.latitude
    val length = dx * dx + dy * dy
    if (length < 1e-18) return 0.0
    return (((p.longitude - a.longitude) * scale * dx + (p.latitude - a.latitude) * dy) / length).coerceIn(0.0, 1.0)
}

/** A route walked as drawn: no routing requests, every spawn along it scored the usual way. */
internal fun planFixedRoute(settings: CatchRouteSettings, data: SpawnAvailability): CatchItinerary {
    val path = truncateCatchPath(fixedCatchPath(settings.fixedPath), settings.walkingBudgetMeters)
    val encounters = scoreCatchPath(path, settings, data.opportunities)
    val warnings = data.warnings + buildList {
        if (path.last().meters < fixedCatchPath(settings.fixedPath).last().meters - 1) add("The session ends before the route does.")
        if (encounters.any { it.opportunity.uncertainty.isNotEmpty() }) add("Predictions include uncertain timing. Open a spawnpoint for evidence.")
    }
    return CatchItinerary(settings, path, encounters, listOf(path.first().point, path.last().point), warnings, data.version, data.sources)
}

/**
 * A polygon around a route for guide mode: the convex hull of the route with [bufferMeters] of room on
 * every side. A hull cannot follow a U-shaped route's inside, but it always contains the whole route,
 * which is what the planner's area check needs.
 */
internal fun bufferedRouteArea(points: List<CatchPoint>, bufferMeters: Double = 150.0): List<CatchPoint> {
    if (points.isEmpty()) return emptyList()
    val simplified = douglasPeuckerIndices(fixedCatchPath(points), ROUTE_AREA_SAMPLES).map { points[it] }
    val ring = simplified.flatMap { p ->
        val latStep = bufferMeters / 111_195.0
        val lonStep = latStep / cos(Math.toRadians(p.latitude)).coerceAtLeast(0.01)
        (0 until 12).map { k ->
            val angle = 2 * Math.PI * k / 12
            // Slightly outside the radius, so the chords between samples still clear the buffer.
            CatchPoint(p.latitude + latStep * 1.04 * sin(angle), p.longitude + lonStep * 1.04 * cos(angle))
        }
    }
    return convexHull(ring)
}

/** Monotone chain, counter-clockwise, without repeating the first corner. */
internal fun convexHull(points: List<CatchPoint>): List<CatchPoint> {
    val sorted = points.distinct().sortedWith(compareBy<CatchPoint> { it.longitude }.thenBy { it.latitude })
    if (sorted.size < 3) return sorted
    fun cross(o: CatchPoint, a: CatchPoint, b: CatchPoint) =
        (a.longitude - o.longitude) * (b.latitude - o.latitude) - (a.latitude - o.latitude) * (b.longitude - o.longitude)
    val lower = mutableListOf<CatchPoint>()
    for (p in sorted) { while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0) lower.removeAt(lower.lastIndex); lower += p }
    val upper = mutableListOf<CatchPoint>()
    for (p in sorted.asReversed()) { while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0) upper.removeAt(upper.lastIndex); upper += p }
    return lower.dropLast(1) + upper.dropLast(1)
}

/** Route points the guide area is built around; the hull only needs the shape's extremes. */
private const val ROUTE_AREA_SAMPLES = 40
