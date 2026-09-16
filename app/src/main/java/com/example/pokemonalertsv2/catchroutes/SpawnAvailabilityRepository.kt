package com.example.pokemonalertsv2.catchroutes

import com.example.pokemonalertsv2.data.RouteMatrixRequest
import com.example.pokemonalertsv2.data.RouteMatrixResponse
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import retrofit2.Response
import retrofit2.http.*
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.coroutineContext
import kotlin.math.*

interface CatchRoutesService {
    @GET("api/spawnpoints/windows") suspend fun windows(@QueryMap query: Map<String, String>): Response<JsonObject>
    @GET("api/spawnpoints") suspend fun catalogue(@QueryMap query: Map<String, String>, @Header("If-None-Match") etag: String?): Response<JsonObject>
    @POST("api/routes/matrix") suspend fun matrix(@Body request: RouteMatrixRequest): Response<RouteMatrixResponse>
    @POST("api/routes/path") suspend fun path(@Body request: RouteMatrixRequest): Response<CatchPathResponse>
    @GET("api/spawnpoints/{id}") suspend fun spawnpoint(@Path("id") id: String): Response<JsonObject>
}

/** One retained sighting at a spawnpoint: which Pokémon, and the cycle it belonged to. */
data class SpawnpointSighting(val pokemonId: Int?, val source: String?, val firstSeenAt: Long?, val despawnAt: Long?, val verified: Boolean)

/** Everything the details sheet shows beyond the planning window itself. */
data class SpawnpointDetail(
    val id: String,
    val point: CatchPoint,
    val schedule: SpawnSchedule?,
    val sightings: List<SpawnpointSighting>,
    val upcoming: List<SpawnOpportunity>,
    val liveLastSeenAt: String?,
    val catalogueSeenAt: String?,
    val encounterCount: Int?,
    val verifiedEncounterCount: Int?,
    val timingConflict: Boolean,
    val requiresLiveConfirmation: Boolean,
    val activityPattern: String,
    val uncertainty: List<String>,
    val source: String?,
    /** Event types this point spawns during, for event-only points. */
    val eventTypes: List<String> = emptyList(),
    val nextEvent: SpawnpointEvent? = null,
    val lastActiveAt: String? = null,
    /** Why the backend planned no window here, e.g. `event_only_inactive`, `dormant_spawnpoint`, `catalogue_only`. */
    val reason: String? = null,
)

/** The next game event an event spawnpoint is expected to wake up for. */
data class SpawnpointEvent(val name: String, val eventType: String, val startAt: Long, val endAt: Long,
    /** The Pokémon a Spotlight Hour or Community Day features, when the calendar knows them. */
    val featured: List<String> = emptyList())

@Serializable data class CatchPathGeometry(val type: String, val coordinates: List<List<Double>>)
@Serializable data class CatchPathLeg(val fromId: String, val toId: String, val distanceMeters: Double, val durationSeconds: Double, val geometry: CatchPathGeometry)
@Serializable data class CatchSnappedPoint(val id: String, val latitude: Double, val longitude: Double)
@Serializable data class CatchPathResponse(val status: String, val provider: String, val calculatedAt: String,
    val snappedPoints: List<CatchSnappedPoint>, val legs: List<CatchPathLeg>)

class CatchApiException(message: String, val retryAtMillis: Long = 0) : Exception(message)

fun <T> Response<T>.catchBody(now: Long = System.currentTimeMillis()): T {
    if (!isSuccessful) {
        val raw = headers()["Retry-After"]
        val retryAt = raw?.toLongOrNull()?.let { now + it.coerceIn(1, 86400) * 1000 }
            ?: runCatching { ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull() ?: now + 60_000
        throw CatchApiException(when (code()) {
            404 -> "The backend does not yet support Catch routes. Deploy the path and spawn windows endpoints."
            429 -> "Routing is busy. Retry after the countdown."
            else -> "Route service unavailable (HTTP ${code()}). Please retry."
        }, if (code() == 429 || raw != null) retryAt else 0)
    }
    return body() ?: throw CatchApiException("Empty backend response.")
}

class SpawnAvailabilityRepository(private val service: CatchRoutesService) {
    suspend fun detail(id: String): SpawnpointDetail {
        val body = service.spawnpoint(id).catchBody()
        return parseSpawnpointDetail(body) ?: throw CatchApiException("Backend returned an unreadable spawnpoint.")
    }

    // Catalogue ETags are query-specific. Window responses are never placed in this cache.
    private val catalogues = linkedMapOf<Map<String, String>, Pair<String?, JsonObject>>()
    suspend fun catalogue(query: Map<String, String>): JsonObject {
        val key = query.toMap()
        val response = service.catalogue(key, catalogues[key]?.first)
        if (response.code() == 304) return catalogues[key]?.second ?: throw CatchApiException("Missing cached catalogue. Retry.")
        val body = response.catchBody()
        validateEnvelope(body)
        catalogues[key] = response.headers()["ETag"] to body
        while (catalogues.size > 8) catalogues.remove(catalogues.keys.first())
        return body
    }

    /**
     * Spawn windows a route with [settings] can use. [searchRadiusMeters] widens the box and the reach
     * check for a start still to be chosen around `settings.start`; [untilMillis] extends the time range
     * for a departure still to be chosen after `settings.startAtMillis` (the backend allows six hours).
     */
    suspend fun load(settings: CatchRouteSettings, progress: (String) -> Unit = {}, searchRadiusMeters: Double = 0.0,
        untilMillis: Long = settings.endAtMillis): SpawnAvailability {
        settings.validate()
        val distance = settings.walkingBudgetMeters + settings.radius + searchRadiusMeters
        val latPad = distance / 111_195.0
        val lonPad = latPad / cos(Math.toRadians(settings.start.latitude)).coerceAtLeast(0.01)
        // Only as far as walking reaches, and never beyond the drawn area.
        val (areaSouthWest, areaNorthEast) = if (settings.area.isArea()) areaBounds(settings.area)
            else CatchPoint(-90.0, -180.0) to CatchPoint(90.0, 180.0)
        val query = mutableMapOf(
            "south" to max(max(-90.0, settings.start.latitude - latPad), areaSouthWest.latitude).toString(),
            "north" to min(min(90.0, settings.start.latitude + latPad), areaNorthEast.latitude).toString(),
            "west" to max(max(-180.0, settings.start.longitude - lonPad), areaSouthWest.longitude).toString(),
            "east" to min(min(180.0, settings.start.longitude + lonPad), areaNorthEast.longitude).toString(),
            "from" to Instant.ofEpochMilli(settings.startAtMillis).toString(),
            "to" to Instant.ofEpochMilli(untilMillis).toString(), "limit" to "2000",
            "includePredictions" to (settings.prediction != CatchPrediction.SUPPORTED_ONLY).toString(),
        )
        if (query.getValue("south").toDouble() > query.getValue("north").toDouble() || query.getValue("west").toDouble() > query.getValue("east").toDouble())
            throw CatchApiException("The area is out of walking reach from the start.")
        if (settings.prediction in listOf(CatchPrediction.THIRTY_MINUTES, CatchPrediction.SIXTY_MINUTES)) query["assumedDurationSeconds"] =
            if (settings.prediction == CatchPrediction.SIXTY_MINUTES) "3600" else "1800"
        repeat(3) { attempt ->
            val rows = linkedMapOf<String, SpawnOpportunity>()
            val warnings = linkedSetOf<String>()
            val sourceMetadata = linkedMapOf<String, SpawnSourceMetadata>()
            val cursors = mutableSetOf<String>()
            var cursor: String? = null
            var version: String? = null
            var restart = false
            var restrictedPointCount = 0
            var page = 0
            do {
                coroutineContext.ensureActive()
                val response = service.windows(query + listOfNotNull(cursor?.let { "cursor" to it }).toMap())
                if (response.code() == 409) { restart = true; break }
                val body = response.catchBody()
                validateEnvelope(body)
                val revision = body.getValue("dataVersion").jsonPrimitive.content
                if (version != null && version != revision) { restart = true; break }
                version = revision
                val sources = body["sources"] as? JsonArray ?: throw CatchApiException("Backend omitted source metadata.")
                if (sources.isEmpty()) warnings += "Source freshness and coverage are unknown."
                for (source in sources) {
                    val s = source.jsonObject
                    val name = s["source"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
                    sourceMetadata[name] = SpawnSourceMetadata(name, s["refreshedAt"]?.jsonPrimitive?.contentOrNull,
                        s["complete"]?.jsonPrimitive?.booleanOrNull, s["coverageKind"]?.jsonPrimitive?.contentOrNull,
                        s["returned"]?.jsonPrimitive?.intOrNull, s["dropped"]?.jsonPrimitive?.intOrNull,
                        (s["liveSnapshot"] as? JsonObject)?.let { live ->
                            SpawnLiveSnapshot(live["refreshedAt"]?.jsonPrimitive?.contentOrNull, live["complete"]?.jsonPrimitive?.booleanOrNull,
                                live["coverageKind"]?.jsonPrimitive?.contentOrNull, live["returned"]?.jsonPrimitive?.intOrNull, live["dropped"]?.jsonPrimitive?.intOrNull)
                        })
                    if (sourceMetadata[name]?.liveSnapshot?.complete == false) warnings += "$name: incomplete live coverage."
                    if (s["complete"]?.jsonPrimitive?.booleanOrNull != true) warnings += "${s["source"]?.jsonPrimitive?.content ?: "Source"}: incomplete coverage."
                }
                for (raw in body.getValue("data").jsonArray) {
                    val point = raw as? JsonObject
                    val sourceName = point?.get("source")?.jsonPrimitive?.contentOrNull
                    if (sourceName != null && sources.none { it.jsonObject["source"]?.jsonPrimitive?.contentOrNull == sourceName }) {
                        warnings += "$sourceName: freshness and coverage are unknown."
                    }
                    if (point?.get("requiresLiveConfirmation")?.jsonPrimitive?.booleanOrNull == true) restrictedPointCount++
                    val parsed = point?.let { parseSpawnWindows(it) { warning -> warnings += warning } }
                    if (parsed == null) { warnings += "Invalid spawnpoint records were excluded."; continue }
                    for (o in parsed) {
                        if (usableFor(settings, o, searchRadiusMeters, untilMillis)) rows[o.id] = o
                    }
                }
                progress("Loaded ${++page} pages · ${rows.size} opportunities")
                cursor = body["nextCursor"]?.jsonPrimitive?.contentOrNull
                if (cursor != null && !cursors.add(cursor)) throw CatchApiException("Backend repeated a pagination cursor.")
            } while (cursor != null)
            if (!restart) return SpawnAvailability(rows.values.toList(), version.orEmpty(), warnings.toList(), sourceMetadata.values.toList(), restrictedPointCount)
            if (attempt == 2) throw CatchApiException("Spawn data changed repeatedly. Please retry.")
            progress("Spawn data changed; restarting download")
        }
        error("Unreachable")
    }
}

internal fun validateEnvelope(body: JsonObject) {
    if (!body.containsKey("nextCursor") || body["dataVersion"]?.jsonPrimitive?.contentOrNull.isNullOrEmpty() ||
        body["data"] !is JsonArray || body["truncated"]?.jsonPrimitive?.booleanOrNull == null ||
        (body["truncated"]?.jsonPrimitive?.boolean == true && body["nextCursor"]?.jsonPrimitive?.contentOrNull == null)) {
        throw CatchApiException("Backend returned an unsupported or incomplete spawnpoint catalogue.")
    }
}

internal fun parseSpawnWindows(row: JsonObject, warning: (String) -> Unit = {}): List<SpawnOpportunity>? = runCatching {
    val point = CatchPoint(row.getValue("latitude").jsonPrimitive.double, row.getValue("longitude").jsonPrimitive.double)
    require(point.valid)
    val id = row.getValue("id").jsonPrimitive.content
    require(id.isNotBlank())
    if (row["associationAmbiguous"]?.jsonPrimitive?.booleanOrNull == true) return@runCatching emptyList()
    val uncertainty = (row["uncertainty"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
    val restricted = row["requiresLiveConfirmation"]?.jsonPrimitive?.booleanOrNull == true
    row.getValue("windows").jsonArray.mapNotNull { raw ->
        runCatching {
            val w = raw.jsonObject
            val from = Instant.parse(w.getValue("availableFrom").jsonPrimitive.content).toEpochMilli()
            val until = Instant.parse(w.getValue("despawnAt").jsonPrimitive.content).toEpochMilli()
            val basis = w.getValue("basis").jsonPrimitive.content
            require(until > from && basis in listOf("observed_encounter", "recurring_schedule", "inferred_lifetime", "assumed_duration", "unverified_encounter"))
            if (restricted && basis != "observed_encounter") return@mapNotNull null
            require(w.getValue("opportunityId").jsonPrimitive.content.isNotBlank())
            SpawnOpportunity(w.getValue("opportunityId").jsonPrimitive.content, id, point, from, until, basis,
                (uncertainty + (w["uncertainty"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()).distinct(),
                row["observedDurationLowerBoundSeconds"]?.jsonPrimitive?.intOrNull,
                row["source"]?.jsonPrimitive?.contentOrNull, row["catalogueSeenAt"]?.jsonPrimitive?.contentOrNull,
                row["liveLastSeenAt"]?.jsonPrimitive?.contentOrNull, row["timingConflict"]?.jsonPrimitive?.booleanOrNull == true,
                w["despawnBasis"]?.jsonPrimitive?.contentOrNull ?: row["despawnBasis"]?.jsonPrimitive?.contentOrNull,
                restricted, row["activityPattern"]?.jsonPrimitive?.contentOrNull ?: "unknown",
                row["activityPatternBasis"]?.jsonPrimitive?.contentOrNull,
                w["probability"]?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0),
                (row["schedule"] as? JsonObject)?.let(::parseSpawnSchedule))
        }.getOrElse { warning("Invalid or unsupported spawn windows were excluded."); null }
    }
}.getOrNull()

internal fun parseSpawnSchedule(o: JsonObject): SpawnSchedule = SpawnSchedule(
    despawnSecondOfHour = o["despawnSecondOfHour"]?.jsonPrimitive?.intOrNull?.takeIf { it in 0..3599 },
    spawnSecondOfHour = o["spawnSecondOfHour"]?.jsonPrimitive?.intOrNull?.takeIf { it in 0..3599 },
    durationSeconds = o["durationSeconds"]?.jsonPrimitive?.intOrNull?.takeIf { it in 1..3600 },
    durationBasis = o["durationBasis"]?.jsonPrimitive?.contentOrNull ?: "unknown",
    confidence = o["confidence"]?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0),
    supportCycles = o["supportCycles"]?.jsonPrimitive?.intOrNull,
    totalCycles = o["totalCycles"]?.jsonPrimitive?.intOrNull,
    lastVerifiedAt = o["lastVerifiedAt"]?.jsonPrimitive?.contentOrNull,
)

internal fun parseSpawnpointDetail(body: JsonObject): SpawnpointDetail? = runCatching {
    val id = body.getValue("id").jsonPrimitive.content
    val point = CatchPoint(body.getValue("latitude").jsonPrimitive.double, body.getValue("longitude").jsonPrimitive.double)
    require(id.isNotBlank() && point.valid)
    val instant = { value: String? -> value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } }
    val sightings = (body["recentEncounters"] as? JsonArray).orEmpty().mapNotNull { raw ->
        val e = raw as? JsonObject ?: return@mapNotNull null
        val verified = e["verifiedExpiry"]?.jsonPrimitive?.longOrNull
        val expiry = verified ?: e["unverifiedExpiry"]?.jsonPrimitive?.longOrNull
        val firstSeen = instant(e["upstreamFirstSeenAt"]?.jsonPrimitive?.contentOrNull)
            ?.takeIf { upstream -> instant(e["firstSeenAt"]?.jsonPrimitive?.contentOrNull)?.let { upstream <= it } != false }
            ?: instant(e["firstSeenAt"]?.jsonPrimitive?.contentOrNull)
        SpawnpointSighting(e["pokemonId"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }, e["source"]?.jsonPrimitive?.contentOrNull,
            firstSeen, expiry?.times(1000), verified != null)
    }
    // Upcoming windows reuse the planner's parser, so the sheet and the route agree.
    val upcoming = parseSpawnWindows(buildJsonObject {
        body.forEach { (key, value) -> if (key != "windows") put(key, value) }
        put("windows", body["upcomingWindows"] as? JsonArray ?: JsonArray(emptyList()))
    }).orEmpty()
    SpawnpointDetail(id, point, (body["schedule"] as? JsonObject)?.let(::parseSpawnSchedule), sightings, upcoming,
        body["liveLastSeenAt"]?.jsonPrimitive?.contentOrNull, body["catalogueSeenAt"]?.jsonPrimitive?.contentOrNull,
        body["encounterCount"]?.jsonPrimitive?.intOrNull, body["verifiedEncounterCount"]?.jsonPrimitive?.intOrNull,
        body["timingConflict"]?.jsonPrimitive?.booleanOrNull == true, body["requiresLiveConfirmation"]?.jsonPrimitive?.booleanOrNull == true,
        body["activityPattern"]?.jsonPrimitive?.contentOrNull ?: "unknown",
        (body["uncertainty"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
        body["source"]?.jsonPrimitive?.contentOrNull,
        (body["eventTypes"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
        (body["nextEvent"] as? JsonObject)?.let { e ->
            val start = instant(e["startAt"]?.jsonPrimitive?.contentOrNull)
            val end = instant(e["endAt"]?.jsonPrimitive?.contentOrNull)
            if (start == null || end == null) null
            else SpawnpointEvent(e["name"]?.jsonPrimitive?.contentOrNull ?: "Event", e["eventType"]?.jsonPrimitive?.contentOrNull ?: "event", start, end,
                (e["featured"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull }.orEmpty())
        },
        body["lastActiveAt"]?.jsonPrimitive?.contentOrNull,
        body["reason"]?.jsonPrimitive?.contentOrNull)
}.getOrNull()

/**
 * Whether a route with [settings] could catch [o]: inside the area, not an event window unless those are
 * wanted, and reachable before it despawns. [slackMeters] relaxes reach for a start not chosen yet.
 */
internal fun usableFor(settings: CatchRouteSettings, o: SpawnOpportunity, slackMeters: Double = 0.0,
    untilMillis: Long = settings.endAtMillis): Boolean {
    if (!pointInArea(o.point, settings.area)) return false
    if (!settings.includeEventSpawns && o.eventOnly) return false
    val earliest = max(0.0, catchDistance(settings.start, o.point) - settings.radius - slackMeters) / settings.speedMps * 1000
    return settings.startAtMillis + earliest < o.despawnAt && o.availableFrom < untilMillis
}

/** The part of [this] a route with [settings] can use; for recommendations that load once and try many starts. */
internal fun SpawnAvailability.forRoute(settings: CatchRouteSettings): SpawnAvailability =
    copy(opportunities = opportunities.filter { usableFor(settings, it) })
