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

    @Test
    fun pipWildSpawnShowsExactCp() {
        val alert = PokemonAlert(
            name = "Wild Electrike",
            type = listOf("Spawn"),
            cp = 800
        )

        assertEquals("CP 800", buildPipCpText(alert))
    }

    @Test
    fun pipHundoWithoutSpawnTypeStillShowsExactCp() {
        val alert = PokemonAlert(
            name = "Perfect Electrike",
            type = listOf("Hundo"),
            cp = 800
        )

        assertEquals("CP 800", buildPipCpText(alert))
    }

    @Test
    fun pipRaidShowsBothPerfectCatchValues() {
        val alert = PokemonAlert(
            name = "Terrakion Raid",
            type = listOf("Raid"),
            hundoCP = HundoCP(level20 = 2113, level25 = 2641)
        )

        assertEquals("100% CP \u00B7 L20 2113 \u00B7 L25 2641", buildPipCpText(alert))
    }

    @Test
    fun pipRaidShowsSingleAvailablePerfectCatchValue() {
        val alert = PokemonAlert(
            name = "Terrakion Raid",
            type = listOf("Raid"),
            hundoCP = HundoCP(level20 = 2113)
        )

        assertEquals("100% CP \u00B7 L20 2113", buildPipCpText(alert))
    }

    @Test
    fun pipRaidDoesNotPresentBossCpAsPerfectCatchCp() {
        val alert = PokemonAlert(
            name = "Terrakion Raid",
            type = listOf("Raid"),
            cp = 48457
        )

        assertNull(buildPipCpText(alert))
    }

    @Test
    fun pipAlertWithoutCpOmitsCpLine() {
        assertNull(buildPipCpText(PokemonAlert(name = "Unknown alert")))
    }
}
