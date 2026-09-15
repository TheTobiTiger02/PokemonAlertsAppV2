package com.example.pokemonalertsv2.catchroutes

import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable
data class CatchPoint(val latitude: Double, val longitude: Double) {
    val valid: Boolean get() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
}

@Serializable enum class CatchFinish { ROUND_TRIP, ANYWHERE, PIN }
@Serializable enum class CatchPrediction { AUTOMATIC, THIRTY_MINUTES, SIXTY_MINUTES, SUPPORTED_ONLY }

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
    val prediction: CatchPrediction = CatchPrediction.AUTOMATIC,
    /** Exact original deadline for internal replans, including the final stretch. */
    val deadlineMillis: Long? = null,
    /**
     * Longest the planner may stand at a stop for a spawn that starts soon. 0 keeps the
     * original walking-only behaviour, where a future spawn counts only if it starts while
     * you are passing through its circle.
     */
    val maxWaitMinutes: Int = 0,
    /** Plan spawnpoints that only spawn during events (Spotlight Hours, Community Days). Off by default. */
    val includeEventSpawns: Boolean = false,
    /** Optional polygon the route must stay inside; fewer than three corners means no limit. */
    val area: List<CatchPoint> = emptyList(),
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
        require(maxWaitMinutes in 0..15) { "Waiting must be 0–15 minutes." }
        require(area.isEmpty() || (area.isArea() && area.all { it.valid })) { "Draw the area with at least three corners." }
        require(pointInArea(start, area)) { "The start must be inside the area." }
        require(finish != CatchFinish.PIN || end == null || pointInArea(end, area)) { "The finish must be inside the area." }
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
    val requiresLiveConfirmation: Boolean = false,
    val activityPattern: String = "unknown",
    val activityPatternBasis: String? = null,
    /** Backend's chance that this window really holds a Pokémon, 0–1. Null from older backends. */
    val probability: Double? = null,
    val schedule: SpawnSchedule? = null,
) {
    val observed: Boolean get() = basis == "observed_encounter"
    /** A window at a spawnpoint that only spawns during events; the backend sends these only while one runs. */
    val eventOnly: Boolean get() = activityPattern == "event_only" || "event_only_spawnpoint" in uncertainty
    val evidenceRank: Int get() = when (basis) { "observed_encounter" -> 4; "recurring_schedule" -> 3; "inferred_lifetime" -> 2; "assumed_duration" -> 1; else -> 0 }

    /**
     * What reaching this window is worth to a route: its probability, or for a backend that
     * does not send one, a fixed value per basis in the same order as [evidenceRank].
     */
    val expectedCatch: Double get() = probability?.coerceIn(0.0, 1.0) ?: when (basis) {
        "observed_encounter" -> 1.0; "recurring_schedule" -> 0.8; "inferred_lifetime" -> 0.7
        "assumed_duration" -> 0.5; else -> 0.4
    }
}

/** A spawnpoint's learned hourly schedule, as the backend voted it. */
@Serializable
data class SpawnSchedule(
    val despawnSecondOfHour: Int? = null,
    val spawnSecondOfHour: Int? = null,
    val durationSeconds: Int? = null,
    val durationBasis: String = "unknown",
    val confidence: Double? = null,
    val supportCycles: Int? = null,
    val totalCycles: Int? = null,
    val lastVerifiedAt: String? = null,
)

@Serializable data class SpawnSourceMetadata(val source: String, val refreshedAt: String? = null, val complete: Boolean? = null,
    val coverageKind: String? = null, val returned: Int? = null, val dropped: Int? = null, val liveSnapshot: SpawnLiveSnapshot? = null)

@Serializable data class SpawnLiveSnapshot(val refreshedAt: String? = null, val complete: Boolean? = null,
    val coverageKind: String? = null, val returned: Int? = null, val dropped: Int? = null)

data class SpawnAvailability(val opportunities: List<SpawnOpportunity>, val version: String, val warnings: List<String>,
    val sources: List<SpawnSourceMetadata> = emptyList(), val restrictedPointCount: Int = 0)

@Serializable data class CatchPathPosition(val point: CatchPoint, val meters: Double)
@Serializable data class CatchEncounter(val opportunity: SpawnOpportunity, val arrivalMillis: Long, val meters: Double)

/** A planned stop: stand still [millis] at [meters] along the path for a spawn to start. */
@Serializable data class CatchWait(val meters: Double, val millis: Long)

/** Clock time at [meters] along a path, counting every wait planned at or before that point. */
fun catchTimeAt(settings: CatchRouteSettings, meters: Double, waits: List<CatchWait>, includeWaitsAt: Boolean = true): Long =
    settings.startAtMillis + ceil(meters / settings.speedMps * 1000).toLong() +
        waits.filter { if (includeWaitsAt) it.meters <= meters + 0.01 else it.meters < meters - 0.01 }.sumOf { it.millis }
@Serializable data class CatchItinerary(
    val settings: CatchRouteSettings,
    val path: List<CatchPathPosition>,
    val encounters: List<CatchEncounter>,
    val anchors: List<CatchPoint>,
    val warnings: List<String> = emptyList(),
    val version: String = "",
    val sources: List<SpawnSourceMetadata> = emptyList(),
    val waits: List<CatchWait> = emptyList(),
) {
    val distanceMeters: Double get() = path.lastOrNull()?.meters ?: 0.0
    val waitMillis: Long get() = waits.sumOf { it.millis }
    val finishAtMillis: Long get() = settings.startAtMillis + ceil(distanceMeters / settings.speedMps * 1000).toLong() + waitMillis
    val observedCount: Int get() = encounters.count { it.opportunity.observed }
    val expectedCatches: Double get() = encounters.sumOf { it.opportunity.expectedCatch }
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
    val remaining: List<CatchEncounter> get() = if (needsRefresh) emptyList() else itinerary.encounters.filterNot { wasVisited(it.opportunity) }
    val displayItinerary: CatchItinerary by lazy { if (needsRefresh) itinerary.copy(encounters = emptyList()) else itinerary }
    val availabilityReadout: String get() = if (needsRefresh) "Timing needs refresh" else "${remaining.size} remaining"
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

fun scoreCatchPath(path: List<CatchPathPosition>, settings: CatchRouteSettings, opportunities: List<SpawnOpportunity>,
    waits: List<CatchWait> = emptyList()): List<CatchEncounter> {
    val index = CatchSpatialIndex(opportunities) { it.point }
    val found = mutableMapOf<String, CatchEncounter>()
    path.zipWithNext().forEach { (a, b) ->
        index.near(a.point, b.point, settings.radius).forEach inner@ { o ->
            if (o.id in found) return@inner
            val interval = catchCircleInterval(a.point, b.point, o.point, settings.radius) ?: return@inner
            val entryMeters = a.meters + (b.meters - a.meters) * interval.start
            val exitMeters = a.meters + (b.meters - a.meters) * interval.endInclusive
            // Waits planned inside the circle keep you in range longer; nothing else does.
            val enter = catchTimeAt(settings, entryMeters, waits, includeWaitsAt = false)
            val leave = settings.startAtMillis + floor(exitMeters / settings.speedMps * 1000).toLong() +
                waits.filter { it.meters <= exitMeters + 0.01 }.sumOf { it.millis }
            val at = max(enter, o.availableFrom)
            // A future spawn counts only if it activates while you are inside its circle.
            if (at <= leave && at < o.despawnAt && at <= settings.endAtMillis) {
                found[o.id] = CatchEncounter(o, at, min(exitMeters, entryMeters + (at - enter) / 1000.0 * settings.speedMps))
            }
        }
    }
    return found.values.sortedWith(compareBy<CatchEncounter> { it.arrivalMillis }.thenBy { it.opportunity.id })
}
