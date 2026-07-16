package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.data.AffectedAlert
import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherAlertPresentationTest {

    @Test
    fun structuredWeatherTransitionReplacesLegacyGenericTitle() {
        val alert = PokemonAlert(
            name = "Weather change",
            type = listOf("WeatherChange"),
            weatherFrom = "Partly Cloudy 🌤",
            weatherTo = "Cloudy ☁"
        )

        assertEquals("Partly Cloudy 🌤 → Cloudy ☁", weatherTransitionLabel(alert))
        assertEquals("Partly Cloudy 🌤 → Cloudy ☁", formatAlertTitle(alert))
    }

    @Test
    fun affectedSummaryIncludesPokemonFormCpAndTypes() {
        val summary = affectedAlertSummary(
            AffectedAlert(
                pokemon = "Zubat",
                pokemonForm = "Shadow",
                cp = 239,
                type = listOf("PvP", "Rare")
            )
        )

        assertEquals("Zubat Shadow • CP 239 • PvP, Rare", summary)
    }

    @Test
    fun cardSummaryShowsThreeRowsAndOverflow() {
        val alert = PokemonAlert(
            name = "Weather change",
            affectedAlerts = (1..5).map {
                AffectedAlert(pokemon = "Zubat $it", cp = 200 + it)
            }
        )

        assertEquals(3, affectedAlertCardLines(alert).size)
        assertEquals(2, affectedAlertOverflowCount(alert))
    }

    @Test
    fun invalidationBadgeOnlyAppearsForInvalidatedHistoryAlert() {
        assertNull(invalidationBadgeText(PokemonAlert(name = "Active")))
        assertEquals(
            "Invalidated by weather",
            invalidationBadgeText(
                PokemonAlert(
                    name = "History",
                    invalidatedByAlertId = 10587
                )
            )
        )
    }

    @Test
    fun searchMatchesAffectedPokemonDetails() {
        val alert = PokemonAlert(
            name = "Weather change",
            affectedAlerts = listOf(
                AffectedAlert(
                    name = "PvP Zubat",
                    pokemon = "Zubat",
                    pokemonForm = "Shadow",
                    area = "Alsbach",
                    type = listOf("PvP")
                )
            )
        )

        assertEquals(true, alert.matchesAlertSearch("shadow"))
        assertEquals(true, alert.matchesAlertSearch("alsbach"))
        assertEquals(false, alert.matchesAlertSearch("pikachu"))
    }
}
