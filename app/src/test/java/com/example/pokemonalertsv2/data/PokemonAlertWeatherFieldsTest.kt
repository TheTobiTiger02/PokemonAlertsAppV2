package com.example.pokemonalertsv2.data

import com.example.pokemonalertsv2.data.database.toDomain
import com.example.pokemonalertsv2.data.database.toEntity
import com.example.pokemonalertsv2.data.database.toHistoryEntity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PokemonAlertWeatherFieldsTest {

    @Test
    fun apiPayload_decodesWeatherAndInvalidationFields() {
        val alert = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }.decodeFromString<PokemonAlert>(
            """
            {
              "id": 10587,
              "name": "Weather change",
              "endTime": "2026-07-16 20:04:21",
              "type": ["WeatherChange"],
              "weatherFrom": "Partly Cloudy 🌤",
              "weatherTo": "Cloudy ☁",
              "affectedAlerts": [{
                "name": "Zubat",
                "pokemon": "Zubat",
                "pokemonForm": null,
                "cp": 239,
                "type": ["PvP"],
                "endTime": "2026-07-16 20:00:00",
                "area": "Alsbach"
              }],
              "invalidatedAt": "2026-07-16 19:04:21",
              "invalidationReason": "Weather changed",
              "invalidatedByAlertId": 10587
            }
            """.trimIndent()
        )

        assertEquals("Partly Cloudy 🌤", alert.weatherFrom)
        assertEquals("Cloudy ☁", alert.weatherTo)
        assertEquals(239, alert.affectedAlerts.single().cp)
        assertEquals(10587, alert.invalidatedByAlertId)
        assertTrue(alert.isInvalidated)
    }

    @Test
    fun roomEntities_roundTripWeatherAndInvalidationFields() {
        val alert = PokemonAlert(
            id = 10587,
            name = "Weather change",
            description = "Weather changed",
            latitude = 49.74,
            longitude = 8.62,
            endTime = "2026-07-16 20:04:21",
            type = listOf("WeatherChange"),
            weatherFrom = "Partly Cloudy 🌤",
            weatherTo = "Cloudy ☁",
            affectedAlerts = listOf(
                AffectedAlert(
                    id = 42,
                    name = "Zubat",
                    pokemon = "Zubat",
                    cp = 239,
                    type = listOf("PvP"),
                    endTime = "2026-07-16 20:00:00",
                    area = "Alsbach"
                )
            ),
            invalidatedAt = "2026-07-16 19:04:21",
            invalidationReason = "Weather changed",
            invalidatedByAlertId = 10587
        )

        assertEquals(alert, alert.toEntity().toDomain())
        assertEquals(alert, alert.toHistoryEntity().toDomain())
    }
}
