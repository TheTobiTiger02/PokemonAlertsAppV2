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
