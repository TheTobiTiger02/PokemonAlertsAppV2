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
}

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

    suspend fun load(settings: CatchRouteSettings, progress: (String) -> Unit = {}): SpawnAvailability {
        settings.validate()
        val distance = settings.walkingBudgetMeters + settings.radius
        val latPad = distance / 111_195.0
        val lonPad = latPad / cos(Math.toRadians(settings.start.latitude)).coerceAtLeast(0.01)
        val query = mutableMapOf(
            "south" to max(-90.0, settings.start.latitude - latPad).toString(),
            "north" to min(90.0, settings.start.latitude + latPad).toString(),
            "west" to max(-180.0, settings.start.longitude - lonPad).toString(),
            "east" to min(180.0, settings.start.longitude + lonPad).toString(),
            "from" to Instant.ofEpochMilli(settings.startAtMillis).toString(),
            "to" to Instant.ofEpochMilli(settings.endAtMillis).toString(), "limit" to "2000",
            "includePredictions" to (settings.prediction != CatchPrediction.SUPPORTED_ONLY).toString(),
        )
        if (settings.prediction != CatchPrediction.SUPPORTED_ONLY) query["assumedDurationSeconds"] =
            if (settings.prediction == CatchPrediction.SIXTY_MINUTES) "3600" else "1800"
        repeat(3) { attempt ->
            val rows = linkedMapOf<String, SpawnOpportunity>()
            val warnings = linkedSetOf<String>()
            val sourceMetadata = linkedMapOf<String, SpawnSourceMetadata>()
            val cursors = mutableSetOf<String>()
            var cursor: String? = null
            var version: String? = null
            var restart = false
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
                        s["returned"]?.jsonPrimitive?.intOrNull, s["dropped"]?.jsonPrimitive?.intOrNull)
                    if (s["complete"]?.jsonPrimitive?.booleanOrNull != true) warnings += "${s["source"]?.jsonPrimitive?.content ?: "Source"}: incomplete coverage."
                }
                for (raw in body.getValue("data").jsonArray) {
                    val point = raw as? JsonObject
                    val sourceName = point?.get("source")?.jsonPrimitive?.contentOrNull
                    if (sourceName != null && sources.none { it.jsonObject["source"]?.jsonPrimitive?.contentOrNull == sourceName }) {
                        warnings += "$sourceName: freshness and coverage are unknown."
                    }
                    val parsed = point?.let { parseSpawnWindows(it) }
                    if (parsed == null) { warnings += "Invalid spawnpoint records were excluded."; continue }
                    for (o in parsed) {
                        val earliest = catchDistance(settings.start, o.point).let { max(0.0, it - settings.radius) } / settings.speedMps * 1000
                        if (settings.startAtMillis + earliest < o.despawnAt && o.availableFrom < settings.endAtMillis) rows[o.id] = o
                    }
                }
                progress("Loaded ${++page} pages · ${rows.size} opportunities")
                cursor = body["nextCursor"]?.jsonPrimitive?.contentOrNull
                if (cursor != null && !cursors.add(cursor)) throw CatchApiException("Backend repeated a pagination cursor.")
            } while (cursor != null)
            if (!restart) return SpawnAvailability(rows.values.toList(), version.orEmpty(), warnings.toList(), sourceMetadata.values.toList())
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

internal fun parseSpawnWindows(row: JsonObject): List<SpawnOpportunity>? = runCatching {
    val point = CatchPoint(row.getValue("latitude").jsonPrimitive.double, row.getValue("longitude").jsonPrimitive.double)
    require(point.valid)
    val id = row.getValue("id").jsonPrimitive.content
    require(id.isNotBlank())
    if (row["associationAmbiguous"]?.jsonPrimitive?.booleanOrNull == true) return@runCatching emptyList()
    val uncertainty = (row["uncertainty"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
    row.getValue("windows").jsonArray.map { raw ->
        val w = raw.jsonObject
        val from = Instant.parse(w.getValue("availableFrom").jsonPrimitive.content).toEpochMilli()
        val until = Instant.parse(w.getValue("despawnAt").jsonPrimitive.content).toEpochMilli()
        val basis = w.getValue("basis").jsonPrimitive.content
        require(until > from && basis in listOf("observed_encounter", "recurring_schedule", "assumed_duration", "unverified_encounter"))
        SpawnOpportunity(w.getValue("opportunityId").jsonPrimitive.content, id, point, from, until, basis,
            (uncertainty + (w["uncertainty"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()).distinct(),
            row["observedDurationLowerBoundSeconds"]?.jsonPrimitive?.intOrNull,
            row["source"]?.jsonPrimitive?.contentOrNull, row["catalogueSeenAt"]?.jsonPrimitive?.contentOrNull,
            row["liveLastSeenAt"]?.jsonPrimitive?.contentOrNull, row["timingConflict"]?.jsonPrimitive?.booleanOrNull == true,
            row["despawnBasis"]?.jsonPrimitive?.contentOrNull)
    }
}.getOrNull()
