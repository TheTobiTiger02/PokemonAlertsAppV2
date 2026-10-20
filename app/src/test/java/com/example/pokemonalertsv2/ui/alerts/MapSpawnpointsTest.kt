package com.example.pokemonalertsv2.ui.alerts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapSpawnpointsTest {
    private fun point(despawn: Int?, duration: Int? = 1800) =
        MapSpawnpoint("sp", 49.7, 8.6, despawn, duration, null)

    /** Epoch millis at [second] past some full hour. */
    private fun at(second: Int) = 1_760_000_400_000L - Math.floorMod(1_760_000_400_000L / 1000, 3600L) * 1000 + second * 1000L

    @Test fun `active between spawn and despawn`() {
        assertEquals(SpawnpointPhase.ACTIVE, spawnpointPhase(point(despawn = 2400), at(1000)))
        assertEquals(SpawnpointPhase.ACTIVE, spawnpointPhase(point(despawn = 2400), at(2399)))
    }

    @Test fun `inactive at despawn and long before spawn`() {
        assertEquals(SpawnpointPhase.INACTIVE, spawnpointPhase(point(despawn = 2400), at(2400)))
        // Next spawn at :10 is 20 minutes away.
        assertEquals(SpawnpointPhase.INACTIVE, spawnpointPhase(point(despawn = 2400), at(3000)))
    }

    @Test fun `soon in the ten minutes before it spawns`() {
        // Spawns at 600.
        assertEquals(SpawnpointPhase.SOON, spawnpointPhase(point(despawn = 2400), at(300)))
        assertEquals(SpawnpointPhase.SOON, spawnpointPhase(point(despawn = 2400), at(0)))
        assertEquals(SpawnpointPhase.INACTIVE, spawnpointPhase(point(despawn = 2400), at(3500)))
    }

    @Test fun `window that wraps the top of the hour`() {
        // Despawns at :05, spawned at :35 the hour before.
        assertEquals(SpawnpointPhase.ACTIVE, spawnpointPhase(point(despawn = 300), at(3500)))
        assertEquals(SpawnpointPhase.ACTIVE, spawnpointPhase(point(despawn = 300), at(100)))
        assertEquals(SpawnpointPhase.SOON, spawnpointPhase(point(despawn = 300), at(1800)))
    }

    @Test fun `unknown timing`() {
        assertEquals(SpawnpointPhase.UNKNOWN, spawnpointPhase(point(despawn = null), at(0)))
    }

    @Test fun `assumes thirty minutes without a learned duration`() {
        assertEquals(SpawnpointPhase.ACTIVE, spawnpointPhase(point(despawn = 2400, duration = null), at(700)))
        assertEquals(SpawnpointPhase.SOON, spawnpointPhase(point(despawn = 2400, duration = null), at(500)))
    }

    @Test fun `parses catalogue rows and skips ambiguous or invalid ones`() {
        val body = Json.parseToJsonElement(
            """
            {"data": [
              {"id": "a", "latitude": 49.74, "longitude": 8.62, "despawnSeconds": 1200,
               "observedDurationLowerBoundSeconds": 1500, "liveLastSeenAt": "2026-09-12T12:00:00.000Z"},
              {"id": "b", "latitude": 49.75, "longitude": 8.63, "despawnSeconds": null, "associationAmbiguous": true},
              {"id": "c", "latitude": 99.0, "longitude": 8.63},
              {"id": "d", "latitude": 49.76, "longitude": 8.64, "despawnSeconds": 4000}
            ]}
            """
        ) as JsonObject
        val parsed = parseMapSpawnpoints(body)
        assertEquals(listOf("a", "d"), parsed.map { it.id })
        assertEquals(1200, parsed[0].despawnSecondOfHour)
        assertEquals(1500, parsed[0].durationSeconds)
        assertEquals(java.time.Instant.parse("2026-09-12T12:00:00Z").toEpochMilli(), parsed[0].lastSeenAtMillis)
        assertNull(parsed[1].despawnSecondOfHour)
    }

    @Test fun `viewport snaps outward to the grid`() {
        val snapped = SpawnpointViewport(49.7412, 8.6033, 49.7488, 8.6151).snapped()
        assertEquals(49.74, snapped.south, 1e-9)
        assertEquals(8.60, snapped.west, 1e-9)
        assertEquals(49.75, snapped.north, 1e-9)
        assertEquals(8.62, snapped.east, 1e-9)
    }
}
