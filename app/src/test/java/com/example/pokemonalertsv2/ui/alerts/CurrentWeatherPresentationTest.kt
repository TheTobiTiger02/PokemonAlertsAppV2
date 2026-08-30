package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentWeatherPresentationTest {

    @Test
    fun stripsFeedDecorationFromKnownWeather() {
        val display = currentWeatherDisplay("Partly Cloudy 🌤", confirmed = true)

        assertEquals("Partly cloudy", display?.label)
        assertEquals("⛅", display?.glyph)
        assertEquals("⛅ Partly cloudy", display?.labelWithGlyph)
    }

    @Test
    fun keepsUnrecognisedTextWithNeutralGlyph() {
        val display = currentWeatherDisplay("Hurricane", confirmed = true)

        assertEquals("Hurricane", display?.label)
        assertEquals("🌡️", display?.glyph)
    }

    @Test
    fun missingConfirmationCountsAsConfirmed() {
        // Older payloads say nothing about confirmation; they must not sprout a caveat.
        val display = currentWeatherDisplay("Rain", confirmed = null)

        assertTrue(display!!.confirmed)
        assertEquals("🌧️ Rain", display.compactLabel)
    }

    @Test
    fun unconfirmedWeatherIsMarkedInTheCompactLabel() {
        val display = currentWeatherDisplay("Snow", confirmed = false)

        assertFalse(display!!.confirmed)
        assertEquals("❄️ Snow?", display.compactLabel)
    }

    @Test
    fun blankWeatherHasNothingToShow() {
        assertNull(currentWeatherDisplay("   ", confirmed = true))
        assertNull(currentWeatherDisplay(null, confirmed = false))
    }

    @Test
    fun readsBothWeatherFieldsOffTheAlert() {
        val alert = PokemonAlert(
            name = "Larvitar",
            currentWeather = "Windy",
            currentWeatherConfirmed = false,
            isWeatherBoosted = true
        )

        val display = currentWeatherDisplay(alert)

        assertEquals("Windy", display?.label)
        assertFalse(display!!.confirmed)
        assertTrue(display.boosted)
    }
}
