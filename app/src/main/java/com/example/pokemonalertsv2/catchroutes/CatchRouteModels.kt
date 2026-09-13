package com.example.pokemonalertsv2.catchroutes

import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable
data class CatchPoint(val latitude: Double, val longitude: Double) {
    val valid: Boolean get() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
}

@Serializable enum class CatchFinish { ROUND_TRIP, ANYWHERE, PIN }
@Serializable enum class CatchPrediction { THIRTY_MINUTES, SIXTY_MINUTES, SUPPORTED_ONLY }

@Serializable
data class CatchRouteSettings(
    val name: String = "Catch route",
    val start: CatchPoint = CatchPoint(49.7408, 8.6201),
    val startAtMillis: Long = 0,
    val durationMinutes: Int = 60,
    val finish: CatchFinish = CatchFinish.ROUND_TRIP,
    val end: CatchPoint? = null,
    val speedMps: Double = 1.36,
    val spacialRend: Boolean = false,
    val prediction: CatchPrediction = CatchPrediction.THIRTY_MINUTES,
    /** Exact original deadline for internal replans, including the final stretch. */
    val deadlineMillis: Long? = null,
) {
    val radius: Double get() = if (spacialRend) 80.0 else 40.0
    val endAtMillis: Long get() = deadlineMillis ?: (startAtMillis + durationMinutes * 60_000L)
    val walkingBudgetMeters: Double get() = (endAtMillis - startAtMillis) / 1000.0 * speedMps
    val destination: CatchPoint? get() = when (finish) {
        CatchFinish.ROUND_TRIP -> start
        CatchFinish.PIN -> end
        CatchFinish.ANYWHERE -> null
    }
    fun validate() {
        require(start.valid && (end == null || end.valid)) { "Choose valid map coordinates." }
        require(durationMinutes in (if (deadlineMillis != null) 1 else 10)..360) { "Choose 10–360 minutes." }
        require(endAtMillis - startAtMillis in 1..21_600_000L) { "Route time must be within six hours." }
        require(speedMps.isFinite() && speedMps in 0.5..2.5) { "Walking pace must be 1.8–9 km/h." }
        require(finish != CatchFinish.PIN || end != null) { "Pick a finishing point." }
        require(startAtMillis > 0) { "Choose a starting time." }
    }
}

@Serializable
data class SpawnOpportunity(
    val id: String, val pointId: String, val point: CatchPoint,
    val availableFrom: Long, val despawnAt: Long, val basis: String,
    val uncertainty: List<String> = emptyList(), val lowerBoundSeconds: Int? = null,
    val source: String? = null,
    val catalogueSeenAt: String? = null,
    val liveLastSeenAt: String? = null,
    val timingConflict: Boolean = false,
    val despawnBasis: String? = null,
) {
    val observed: Boolean get() = basis == "observed_encounter"
    val evidenceRank: Int get() = when (basis) { "observed_encounter" -> 3; "recurring_schedule" -> 2; "assumed_duration" -> 1; else -> 0 }
}

@Serializable data class SpawnSourceMetadata(val source: String, val refreshedAt: String? = null, val complete: Boolean? = null,
    val coverageKind: String? = null, val returned: Int? = null, val dropped: Int? = null)

data class SpawnAvailability(val opportunities: List<SpawnOpportunity>, val version: String, val warnings: List<String>,
    val sources: List<SpawnSourceMetadata> = emptyList())

@Serializable data class CatchPathPosition(val point: CatchPoint, val meters: Double)
@Serializable data class CatchEncounter(val opportunity: SpawnOpportunity, val arrivalMillis: Long, val meters: Double)
@Serializable data class CatchItinerary(
    val settings: CatchRouteSettings,
    val path: List<CatchPathPosition>,
    val encounters: List<CatchEncounter>,
    val anchors: List<CatchPoint>,
    val warnings: List<String> = emptyList(),
    val version: String = "",
    val sources: List<SpawnSourceMetadata> = emptyList(),
) {
    val distanceMeters: Double get() = path.lastOrNull()?.meters ?: 0.0
    val finishAtMillis: Long get() = settings.startAtMillis + ceil(distanceMeters / settings.speedMps * 1000).toLong()
    val observedCount: Int get() = encounters.count { it.opportunity.observed }
}

@Serializable data class CatchVisit(val opportunity: SpawnOpportunity, val visitedAt: Long, val skipped: Boolean = false)
@Serializable data class CatchSession(
    val itinerary: CatchItinerary,
    val visits: List<CatchVisit> = emptyList(),
    val caught: Int = 0,
    val paused: Boolean = false,
    val needsRefresh: Boolean = false,
    val progressMeters: Double = 0.0,
    val undoVisits: List<CatchVisit>? = null,
    val undoCaught: Int? = null,
    val finished: Boolean = false,
) {
    fun wasVisited(opportunity: SpawnOpportunity): Boolean = visits.any { sameCycle(it.opportunity, opportunity, it.visitedAt) }
    val remaining: List<CatchEncounter> get() = itinerary.encounters.filterNot { wasVisited(it.opportunity) }
}

// Use the actual visit instant to reconcile an expiry correction without consuming a later hourly cycle.
fun sameCycle(a: SpawnOpportunity, b: SpawnOpportunity, visitedAt: Long): Boolean =
    a.id == b.id || (a.pointId == b.pointId && abs(a.despawnAt - b.despawnAt) < 15 * 60_000 &&
        visitedAt >= b.availableFrom && visitedAt < b.despawnAt)

fun catchDistance(a: CatchPoint, b: CatchPoint): Double {
    val lat = Math.toRadians(b.latitude - a.latitude)
    val lon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
    return 12_742_000.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

/** Circle/segment intersection, including both entry and exit, in a local metric projection. */
fun catchCircleInterval(a: CatchPoint, b: CatchPoint, center: CatchPoint, radius: Double): ClosedFloatingPointRange<Double>? {
    val scaleX = 111_195.0 * cos(Math.toRadians(center.latitude))
    val ax = (a.longitude - center.longitude) * scaleX
    val ay = (a.latitude - center.latitude) * 111_195.0
    val dx = (b.longitude - a.longitude) * scaleX
    val dy = (b.latitude - a.latitude) * 111_195.0
    val aa = dx * dx + dy * dy
    if (aa < 1e-9) return if (ax * ax + ay * ay <= radius * radius) 0.0..1.0 else null
    val bb = 2 * (ax * dx + ay * dy)
    val cc = ax * ax + ay * ay - radius * radius
    val disc = bb * bb - 4 * aa * cc
    if (disc < 0) return null
    val enter = max(0.0, (-bb - sqrt(disc)) / (2 * aa))
    val leave = min(1.0, (-bb + sqrt(disc)) / (2 * aa))
    return if (enter <= leave) enter..leave else null
}

/** Fixed spatial buckets; querying a short path segment does not scan the entire catalogue. */
class CatchSpatialIndex<T>(items: List<T>, private val coordinate: (T) -> CatchPoint) {
    private val buckets = items.groupBy { cell(coordinate(it)) }
    private fun cell(p: CatchPoint) = floor(p.latitude / 0.001).toInt() to floor(p.longitude / 0.001).toInt()
    fun near(a: CatchPoint, b: CatchPoint = a, radius: Double): List<T> {
        val latPad = radius / 111_195.0
        val lonPad = latPad / cos(Math.toRadians(a.latitude)).coerceAtLeast(0.01)
        val lo = cell(CatchPoint(min(a.latitude, b.latitude) - latPad, min(a.longitude, b.longitude) - lonPad))
        val hi = cell(CatchPoint(max(a.latitude, b.latitude) + latPad, max(a.longitude, b.longitude) + lonPad))
        return buildList { for (y in lo.first..hi.first) for (x in lo.second..hi.second) buckets[y to x]?.let { addAll(it) } }
    }
}

fun scoreCatchPath(path: List<CatchPathPosition>, settings: CatchRouteSettings, opportunities: List<SpawnOpportunity>): List<CatchEncounter> {
    val index = CatchSpatialIndex(opportunities) { it.point }
    val found = mutableMapOf<String, CatchEncounter>()
    path.zipWithNext().forEach { (a, b) ->
        index.near(a.point, b.point, settings.radius).forEach inner@ { o ->
            if (o.id in found) return@inner
            val interval = catchCircleInterval(a.point, b.point, o.point, settings.radius) ?: return@inner
            val entryMeters = a.meters + (b.meters - a.meters) * interval.start
            val exitMeters = a.meters + (b.meters - a.meters) * interval.endInclusive
            val enter = settings.startAtMillis + ceil(entryMeters / settings.speedMps * 1000).toLong()
            val leave = settings.startAtMillis + floor(exitMeters / settings.speedMps * 1000).toLong()
            val at = max(enter, o.availableFrom)
            // No waiting: a future spawn counts only if it activates while walking inside its circle.
            if (at <= leave && at < o.despawnAt && at <= settings.endAtMillis) {
                found[o.id] = CatchEncounter(o, at, (at - settings.startAtMillis) / 1000.0 * settings.speedMps)
            }
        }
    }
    return found.values.sortedWith(compareBy<CatchEncounter> { it.arrivalMillis }.thenBy { it.opportunity.id })
}
