package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.data.HundoCP
import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AlertGlanceMetadataTest {
    @Test
    fun exactCpIsPlacedBeforeDistanceAndCategory() {
        val alert = PokemonAlert(name = "Perfect Trubbish", cp = 457, type = listOf("Hundo"))

        assertEquals(
            "CP 457 • 15.5 km • 192 min walk • Hundo",
            buildAlertGlanceMetadata(alert, "15.5 km", "192 min walk")
        )
    }

    @Test
    fun weatherChangeUsesNewCp() {
        val alert = PokemonAlert(
            name = "Weather update",
            cp = 300,
            newCp = 412,
            type = listOf("WeatherChange")
        )

        assertEquals(412, alert.displayCp)
    }

    @Test
    fun missingAndInvalidCpAreOmitted() {
        assertNull(PokemonAlert(name = "Unknown", cp = 0).displayCp)
        assertNull(PokemonAlert(name = "Unknown", cp = -1).displayCp)
        assertEquals("3.2 km • Spawn", buildAlertGlanceMetadata(
            PokemonAlert(name = "Unknown", type = listOf("Spawn")),
            distanceText = "3.2 km"
        ))
    }

    @Test
    fun raidHundoRangeIsNotPresentedAsExactCp() {
        val alert = PokemonAlert(
            name = "Raid",
            type = listOf("Raid"),
            hundoCP = HundoCP(level20 = 2113, level25 = 2641)
        )

        assertNull(alert.displayCp)
        assertFalse(buildAlertGlanceMetadata(alert).contains("2113"))
    }
}
