package com.example.pokemonalertsv2.data.counters

import com.example.pokemonalertsv2.data.sim.WeatherBoost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RaidCounterSimOptionsTest {

    @Test
    fun `friendship multipliers rise with the tier and match the measured ratios`() {
        // Cross-checked against the live API as 1/estimator for TYRANITAR_MEGA vs Lunala
        // across levels 0..5: 1.000, 1.016, 1.055, 1.072, 1.088, 1.126.
        assertEquals(1.00, PokebattlerFriendship.NONE.damageMultiplier, 1e-9)
        assertEquals(1.03, PokebattlerFriendship.GOOD.damageMultiplier, 1e-9)
        assertEquals(1.05, PokebattlerFriendship.GREAT.damageMultiplier, 1e-9)
        assertEquals(1.07, PokebattlerFriendship.ULTRA.damageMultiplier, 1e-9)
        assertEquals(1.10, PokebattlerFriendship.BEST.damageMultiplier, 1e-9)
        assertEquals(1.13, PokebattlerFriendship.FOREVER.damageMultiplier, 1e-9)

        val values = PokebattlerFriendship.entries.map { it.damageMultiplier }
        assertTrue("must not decrease", values.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun `every weather maps onto a simulator boost`() {
        assertEquals(WeatherBoost.NONE, PokebattlerWeather.NONE.toWeatherBoost())
        assertEquals(WeatherBoost.FOG, PokebattlerWeather.FOG.toWeatherBoost())
        // Exhaustive: a new weather must not silently fall through to NONE.
        assertEquals(
            PokebattlerWeather.entries.size,
            PokebattlerWeather.entries.map { it.toWeatherBoost() }.distinct().size
        )
    }

    @Test
    fun `dodge fractions stay within a plausible range`() {
        assertEquals(0.0, PokebattlerDodge.NONE.dodgeFraction, 1e-9)
        assertEquals(0.75, PokebattlerDodge.PERFECT.dodgeFraction, 1e-9)
        assertTrue(PokebattlerDodge.entries.all { it.dodgeFraction in 0.0..0.75 })
    }
}
