package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.catchroutes.CatchRoutesService
import com.example.pokemonalertsv2.catchroutes.SpawnAvailabilityRepository
import com.example.pokemonalertsv2.catchroutes.validateEnvelope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.floor

/**
 * One spawnpoint from the backend catalogue (`GET /api/spawnpoints`), as the map layer draws it.
 *
 * Only the hourly timing is kept here; the details sheet loads the rest (`/api/spawnpoints/:id`)
 * when a point is tapped.
 */
internal data class MapSpawnpoint(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    /** Second of the hour the spawn despawns, 0..3599, or null when the timing is unknown. */
    val despawnSecondOfHour: Int?,
    /** How long it stays up, when the backend has evidence for it. */
    val durationSeconds: Int?,
    val lastSeenAtMillis: Long?
)

internal enum class SpawnpointPhase { ACTIVE, SOON, INACTIVE, UNKNOWN }

/** Without a learned duration, a spawn is assumed to be up for the shortest common lifetime. */
internal const val ASSUMED_SPAWN_DURATION_SECONDS = 1800
/** "Soon" means it spawns within this many seconds. */
internal const val SPAWNPOINT_SOON_SECONDS = 600
/** Below this zoom the catalogue would be thousands of dots of visual noise. */
internal const val SPAWNPOINT_MIN_ZOOM = 15.0

/**
 * Where [point] is in its hourly cycle at [nowMillis]: up now, spawning within
 * [SPAWNPOINT_SOON_SECONDS], or idle. The cycle repeats every hour, so the window may wrap
 * around the top of the hour.
 */
internal fun spawnpointPhase(point: MapSpawnpoint, nowMillis: Long): SpawnpointPhase {
    val despawn = point.despawnSecondOfHour ?: return SpawnpointPhase.UNKNOWN
    val duration = (point.durationSeconds ?: ASSUMED_SPAWN_DURATION_SECONDS).coerceIn(1, 3600)
    val nowSecond = Math.floorMod(nowMillis / 1000, 3600L).toInt()
    // Seconds until the next despawn, 0 meaning it just despawned.
    val untilDespawn = Math.floorMod(despawn - nowSecond, 3600)
    return when {
        untilDespawn in 1..duration -> SpawnpointPhase.ACTIVE
        // Spawns at despawn - duration; it is soon when that is less than SOON ahead.
        untilDespawn - duration in 1..SPAWNPOINT_SOON_SECONDS -> SpawnpointPhase.SOON
        else -> SpawnpointPhase.INACTIVE
    }
}

/** Parses one catalogue page. Rows without a valid position or id are skipped. */
internal fun parseMapSpawnpoints(body: JsonObject): List<MapSpawnpoint> {
    val rows = body["data"] as? JsonArray ?: return emptyList()
    return rows.mapNotNull { raw ->
        val row = raw as? JsonObject ?: return@mapNotNull null
        val id = row["id"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val latitude = row["latitude"]?.jsonPrimitive?.doubleOrNull?.takeIf { it in -90.0..90.0 } ?: return@mapNotNull null
        val longitude = row["longitude"]?.jsonPrimitive?.doubleOrNull?.takeIf { it in -180.0..180.0 } ?: return@mapNotNull null
        if (row["associationAmbiguous"]?.jsonPrimitive?.booleanOrNull == true) return@mapNotNull null
        fun int(key: String) = row[key]?.jsonPrimitive?.intOrNull
        fun instant(key: String) = row[key]?.jsonPrimitive?.contentOrNull
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        // A still-valid estimate beats the observed lower bound, which only says "at least".
        val estimateValid = instant("estimateValidUntil")?.let { it > System.currentTimeMillis() } == true
        val duration = (if (estimateValid) int("estimatedDurationSeconds") else null)
            ?: int("observedDurationLowerBoundSeconds")
        MapSpawnpoint(
            id = id,
            latitude = latitude,
            longitude = longitude,
            despawnSecondOfHour = int("despawnSeconds")?.takeIf { it in 0..3599 },
            durationSeconds = duration?.takeIf { it in 1..3600 },
            lastSeenAtMillis = instant("liveLastSeenAt")
        )
    }
}

/** A lat/lon box, snapped outward to [SPAWNPOINT_GRID_DEGREES] so small pans repeat a query. */
internal data class SpawnpointViewport(val south: Double, val west: Double, val north: Double, val east: Double) {
    fun snapped(): SpawnpointViewport = SpawnpointViewport(
        south = floor(south / SPAWNPOINT_GRID_DEGREES) * SPAWNPOINT_GRID_DEGREES,
        west = floor(west / SPAWNPOINT_GRID_DEGREES) * SPAWNPOINT_GRID_DEGREES,
        north = ceil(north / SPAWNPOINT_GRID_DEGREES) * SPAWNPOINT_GRID_DEGREES,
        east = ceil(east / SPAWNPOINT_GRID_DEGREES) * SPAWNPOINT_GRID_DEGREES
    )

    fun query(): Map<String, String> = mapOf(
        "south" to "%.4f".format(java.util.Locale.ROOT, south),
        "west" to "%.4f".format(java.util.Locale.ROOT, west),
        "north" to "%.4f".format(java.util.Locale.ROOT, north),
        "east" to "%.4f".format(java.util.Locale.ROOT, east),
        "limit" to SPAWNPOINT_PAGE_LIMIT.toString()
    )
}

internal const val SPAWNPOINT_GRID_DEGREES = 0.01
private const val SPAWNPOINT_PAGE_LIMIT = 2000

/**
 * Loads catalogue rows for the visible map. The repository revalidates each snapped query with
 * its ETag, so panning back and forth costs a 304 rather than a re-download. One page is enough:
 * at [SPAWNPOINT_MIN_ZOOM] a screen holds a few hundred points, far below the page limit.
 */
internal class MapSpawnpointSource(service: CatchRoutesService) {
    private val repository = SpawnAvailabilityRepository(service)

    suspend fun load(viewport: SpawnpointViewport): List<MapSpawnpoint> {
        val body = repository.catalogue(viewport.snapped().query())
        validateEnvelope(body)
        return parseMapSpawnpoints(body)
    }
}
