package com.example.pokemonalertsv2.data.counters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RaidCounterOptionsTest {

    @Test
    fun `maps feed weather text with emoji`() {
        assertEquals(PokebattlerWeather.PARTLY_CLOUDY, PokebattlerWeather.fromAlertWeather("Partly Cloudy \uD83C\uDF24"))
        assertEquals(PokebattlerWeather.OVERCAST, PokebattlerWeather.fromAlertWeather("Cloudy \u2601"))
        assertEquals(PokebattlerWeather.CLEAR, PokebattlerWeather.fromAlertWeather("sunny"))
        assertEquals(PokebattlerWeather.CLEAR, PokebattlerWeather.fromAlertWeather("Clear"))
        assertEquals(PokebattlerWeather.RAINY, PokebattlerWeather.fromAlertWeather("Rain"))
        assertEquals(PokebattlerWeather.WINDY, PokebattlerWeather.fromAlertWeather("Windy"))
        assertEquals(PokebattlerWeather.SNOW, PokebattlerWeather.fromAlertWeather("Snow"))
        assertEquals(PokebattlerWeather.FOG, PokebattlerWeather.fromAlertWeather("Fog"))
    }

    @Test
    fun `partly cloudy is checked before plain cloudy`() {
        assertEquals(PokebattlerWeather.PARTLY_CLOUDY, PokebattlerWeather.fromAlertWeather("Partly cloudy"))
        assertEquals(PokebattlerWeather.OVERCAST, PokebattlerWeather.fromAlertWeather("Overcast"))
    }

    @Test
    fun `unknown weather yields null so the user default is kept`() {
        assertNull(PokebattlerWeather.fromAlertWeather(null))
        assertNull(PokebattlerWeather.fromAlertWeather(""))
        assertNull(PokebattlerWeather.fromAlertWeather("Blizzard"))
    }

    @Test
    fun `friendship has six levels including lucky`() {
        assertEquals(6, PokebattlerFriendship.entries.size)
        assertEquals("FRIENDSHIP_LEVEL_0", PokebattlerFriendship.NONE.apiValue)
        assertEquals("FRIENDSHIP_LEVEL_4", PokebattlerFriendship.BEST.apiValue)
        assertEquals("FRIENDSHIP_LEVEL_5", PokebattlerFriendship.FOREVER.apiValue)
        assertEquals("Forever Friend", PokebattlerFriendship.FOREVER.label)
    }

    @Test
    fun `only tokens the API accepts are modelled`() {
        // Each of these was rejected with HTTP 404 by the live service, so none may reappear.
        val weatherTokens = PokebattlerWeather.entries.map { it.apiValue }
        assertEquals("NO_WEATHER", PokebattlerWeather.NONE.apiValue)
        assertFalse(weatherTokens.contains("EXTREME"))
        assertFalse(weatherTokens.contains("SUNNY"))

        val dodgeTokens = PokebattlerDodge.entries.map { it.apiValue }
        assertEquals("DODGE_0", PokebattlerDodge.NONE.apiValue)
        assertFalse(dodgeTokens.contains("DODGE_NONE"))
        // DODGE_SPECIALS is an attack strategy, not a dodge strategy.
        assertFalse(dodgeTokens.contains("DODGE_SPECIALS"))

        assertFalse(PokebattlerSort.entries.map { it.apiValue }.contains("WIN_RATE"))
    }
}
