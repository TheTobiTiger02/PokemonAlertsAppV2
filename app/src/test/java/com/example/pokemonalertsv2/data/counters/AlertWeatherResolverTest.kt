package com.example.pokemonalertsv2.data.counters

import com.example.pokemonalertsv2.data.CurrentWeatherResponse
import com.example.pokemonalertsv2.data.PokemonAlert
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertWeatherResolverTest {

    private val lookedUpAreas = mutableListOf<String?>()
    private var areaResponse: CurrentWeatherResponse? = null

    private suspend fun resolve(alert: PokemonAlert): ResolvedWeather? =
        resolveAlertWeather(alert) { area ->
            lookedUpAreas += area
            areaResponse
        }

    @Test
    fun theAlertsOwnWeatherWinsOverEverything() = runTest {
        areaResponse = CurrentWeatherResponse(currentWeather = "Snow", isCurrentHour = true)
        val alert = raid(currentWeather = "Rain 🌧", weatherTo = "Windy", area = "Alsbach")

        val resolved = resolve(alert)

        assertEquals(PokebattlerWeather.RAINY, resolved?.weather)
        assertTrue(resolved!!.confirmed)
        // The endpoint is a fallback; an alert that already knows must not trigger a request.
        assertEquals(emptyList<String?>(), lookedUpAreas)
    }

    @Test
    fun unconfirmedAlertWeatherIsStillUsed() = runTest {
        val resolved = resolve(raid(currentWeather = "Fog", currentWeatherConfirmed = false))

        assertEquals(PokebattlerWeather.FOG, resolved?.weather)
        assertFalse(resolved!!.confirmed)
    }

    @Test
    fun absentConfirmationCountsAsConfirmed() = runTest {
        val resolved = resolve(raid(currentWeather = "Clear"))

        assertEquals(PokebattlerWeather.CLEAR, resolved?.weather)
        assertTrue(resolved!!.confirmed)
    }

    @Test
    fun weatherChangeAlertsFallBackToTheirDestinationWeather() = runTest {
        val resolved = resolve(raid(weatherFrom = "Clear", weatherTo = "Partly Cloudy 🌤"))

        assertEquals(PokebattlerWeather.PARTLY_CLOUDY, resolved?.weather)
        assertTrue(resolved!!.confirmed)
    }

    @Test
    fun theAreaEndpointFillsInWhenTheAlertKnowsNothing() = runTest {
        areaResponse = CurrentWeatherResponse(
            area = "Alsbach",
            currentWeather = "Cloudy ☁",
            currentWeatherConfirmed = false
        )

        val resolved = resolve(raid(area = "Alsbach"))

        assertEquals(PokebattlerWeather.OVERCAST, resolved?.weather)
        assertFalse(resolved!!.confirmed)
        assertEquals(listOf("Alsbach"), lookedUpAreas)
    }

    @Test
    fun nothingKnownLeavesTheChoiceToTheCaller() = runTest {
        assertNull(resolve(raid(area = "Alsbach")))
    }

    @Test
    fun unrecognisedWeatherTextIsNotGuessedAt() = runTest {
        assertNull(resolve(raid(currentWeather = "Hurricane")))
    }

    private fun raid(
        currentWeather: String? = null,
        currentWeatherConfirmed: Boolean? = null,
        weatherFrom: String? = null,
        weatherTo: String? = null,
        area: String? = null
    ) = PokemonAlert(
        name = "Groudon",
        type = listOf("Raid"),
        currentWeather = currentWeather,
        currentWeatherConfirmed = currentWeatherConfirmed,
        weatherFrom = weatherFrom,
        weatherTo = weatherTo,
        area = area
    )
}
