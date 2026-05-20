package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PvpRanking
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertShareCardTest {
    @Test
    fun buildShareCardContent_prefersWeatherChangedStats() {
        val alert = PokemonAlert(
            name = "Weather Changed Togetic",
            pokemon = "Togetic",
            endTime = "2000000000000",
            iv = "10/10/10",
            cp = 600,
            newIv = "15/15/15",
            newCp = 900,
            type = listOf("WeatherChange"),
            area = "Darmstadt"
        )

        val content = AlertShareCard.buildShareCardContent(alert, nowMillis = 1_000L)

        assertTrue(content.title.contains("Togetic Changed"))
        assertTrue(content.stats.any { it.label == "IV" && it.value == "15/15/15" })
        assertTrue(content.stats.any { it.label == "CP" && it.value == "900" })
        assertTrue(content.tags.contains("WEATHERCHANGE"))
    }

    @Test
    fun buildShareText_includesMapsLinkWhenCoordinatesExist() {
        val alert = PokemonAlert(
            name = "Togetic",
            endTime = "2000000000000",
            latitude = 49.8728,
            longitude = 8.6512,
            pvpRankings = listOf(PvpRanking(league = "great", rank = 1))
        )

        val content = AlertShareCard.buildShareCardContent(alert, nowMillis = 1_000L)
        val text = AlertShareCard.buildShareText(content)

        assertTrue(content.stats.any { it.label == "PvP" && it.value == "Great #1" })
        assertTrue(text.contains("https://www.google.com/maps/search/?api=1&query=49.8728,8.6512"))
    }
}
