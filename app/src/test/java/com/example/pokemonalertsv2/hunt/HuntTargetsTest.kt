package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HuntTargetsTest {

    private val now = Instant.parse("2026-09-09T12:00:00Z").toEpochMilli()
    private val future = Instant.parse("2026-09-09T12:30:00Z").toString()
    private val past = Instant.parse("2026-09-09T11:30:00Z").toString()

    /** Only Team GO Rocket, only the Dragon grunts — the worked example. */
    private val dragonGrunts = FilterDefinition(
        alertTypes = FilterSelection.only(listOf("ROCKET")),
        rocketTypes = FilterSelection.only(listOf("Dragon"))
    )

    @Test
    fun `a hunt offers only its own matches`() {
        val alerts = listOf(
            grunt("Dragon", "Dragon", latitude = 50.0, longitude = 8.0),
            grunt("Water", "Water", latitude = 50.001, longitude = 8.0),
            PokemonAlert(
                name = "Larvitar",
                type = listOf("Spawn"),
                pokemon = "Larvitar",
                latitude = 50.002,
                longitude = 8.0,
                endTime = future
            )
        )

        val targets = huntTargets(
            alerts = alerts,
            definition = dragonGrunts,
            dismissedAlertIds = emptySet(),
            originLatitude = 50.0,
            originLongitude = 8.0,
            nowMillis = now
        )

        assertEquals(listOf("Dragon"), targets.map { it.gruntType })
    }

    @Test
    fun `targets come back nearest first`() {
        val alerts = listOf(
            grunt("far", "Dragon", latitude = 50.010, longitude = 8.0),
            grunt("near", "Dragon", latitude = 50.001, longitude = 8.0),
            grunt("middle", "Dragon", latitude = 50.005, longitude = 8.0)
        )

        val targets = huntTargets(
            alerts = alerts,
            definition = dragonGrunts,
            dismissedAlertIds = emptySet(),
            originLatitude = 50.0,
            originLongitude = 8.0,
            nowMillis = now
        )

        assertEquals(listOf("near", "middle", "far"), targets.map { it.name })
    }

    @Test
    fun `what you already got is not offered again`() {
        val caught = grunt("caught", "Dragon", latitude = 50.001, longitude = 8.0)
        val remaining = grunt("remaining", "Dragon", latitude = 50.005, longitude = 8.0)

        val targets = huntTargets(
            alerts = listOf(caught, remaining),
            definition = dragonGrunts,
            dismissedAlertIds = setOf(caught.uniqueId),
            originLatitude = 50.0,
            originLongitude = 8.0,
            nowMillis = now
        )

        assertEquals(listOf("remaining"), targets.map { it.name })
    }

    @Test
    fun `expired and unmappable alerts are never targets`() {
        val alerts = listOf(
            grunt("expired", "Dragon", latitude = 50.001, longitude = 8.0).copy(endTime = past),
            grunt("nowhere", "Dragon", latitude = null, longitude = null),
            grunt("nullisland", "Dragon", latitude = 0.0, longitude = 0.0),
            grunt("good", "Dragon", latitude = 50.002, longitude = 8.0)
        )

        val targets = huntTargets(
            alerts = alerts,
            definition = dragonGrunts,
            dismissedAlertIds = emptySet(),
            originLatitude = 50.0,
            originLongitude = 8.0,
            nowMillis = now
        )

        assertEquals(listOf("good"), targets.map { it.name })
    }

    @Test
    fun `a hunt with no matches yields an empty list rather than everything`() {
        val targets = huntTargets(
            alerts = listOf(grunt("Water", "Water", latitude = 50.001, longitude = 8.0)),
            definition = dragonGrunts,
            dismissedAlertIds = emptySet(),
            originLatitude = 50.0,
            originLongitude = 8.0,
            nowMillis = now
        )

        assertTrue(targets.isEmpty())
    }

    @Test
    fun `a destination left over from another hunt is not this hunt's target`() {
        // The journey store outlives a hunt, so a raid hunt booting up would find
        // whatever the last one was walking to and present it as a raid.
        val spawn = PokemonAlert(
            name = "Larvitar",
            type = listOf("Spawn"),
            pokemon = "Larvitar",
            latitude = 50.0,
            longitude = 8.0,
            endTime = future
        )
        assertEquals(false, isHuntTarget(spawn, dragonGrunts, now))
    }

    @Test
    fun `a match that has already ended is not a target either`() {
        val expired = grunt("Dragon", "Dragon", 50.0, 8.0).copy(endTime = past)
        assertEquals(false, isHuntTarget(expired, dragonGrunts, now))
    }

    @Test
    fun `a live match is`() {
        assertEquals(true, isHuntTarget(grunt("Dragon", "Dragon", 50.0, 8.0), dragonGrunts, now))
    }

    @Test
    fun `a target that ends before you could walk to it drops behind one that does not`() {
        // 200 m away but gone in a minute, against 900 m away and up for an hour.
        // Nearest-first sends you to the first and you arrive to nothing.
        val fleeting = grunt("Near", "Dragon", 50.0018, 8.0)
            .copy(endTime = Instant.parse("2026-09-09T12:01:00Z").toString())
        val reachable = grunt("Far", "Dragon", 50.0081, 8.0)

        val order = huntWalkOrder(listOf(fleeting, reachable), 50.0, 8.0, now)

        assertEquals(listOf("Far", "Near"), order.map { it.name })
    }

    @Test
    fun `an unreachable target is moved, never dropped`() {
        val fleeting = grunt("Near", "Dragon", 50.0018, 8.0)
            .copy(endTime = Instant.parse("2026-09-09T12:01:00Z").toString())
        assertEquals(1, huntWalkOrder(listOf(fleeting), 50.0, 8.0, now).size)
    }

    @Test
    fun `an alert with no end time has nothing to miss`() {
        val endless = grunt("Endless", "Dragon", 50.05, 8.0).copy(endTime = "")
        assertTrue(canArriveBeforeItEnds(endless, 50.0, 8.0, now))
    }

    @Test
    fun `the walk chains through a cluster instead of fanning out from you`() {
        // Two targets north, one south. Nearest-first from where you stand goes
        // north 300 m, back south past yourself 400 m, then north again 500 m.
        // Chaining takes the two northern ones together.
        val north = grunt("North", "Dragon", 50.0027, 8.0)
        val farNorth = grunt("FarNorth", "Dragon", 50.0045, 8.0)
        val south = grunt("South", "Dragon", 49.9964, 8.0)

        val order = huntWalkOrder(listOf(north, south, farNorth), 50.0, 8.0, now)

        assertEquals(listOf("North", "FarNorth", "South"), order.map { it.name })
    }

    @Test
    fun `the nearest target is still the one you are sent to first`() {
        // Chaining changes what comes after, never where you start.
        val order = huntWalkOrder(
            listOf(
                grunt("Far", "Dragon", 50.0045, 8.0),
                grunt("Near", "Dragon", 50.0009, 8.0),
                grunt("Middle", "Dragon", 50.0027, 8.0)
            ),
            50.0,
            8.0,
            now
        )
        assertEquals("Near", order.first().name)
    }

    private fun grunt(
        name: String,
        gruntType: String,
        latitude: Double?,
        longitude: Double?
    ) = PokemonAlert(
        name = name,
        type = listOf("Rocket"),
        gruntType = gruntType,
        latitude = latitude,
        longitude = longitude,
        endTime = future,
        pokestop = "Stop $name"
    )
}
