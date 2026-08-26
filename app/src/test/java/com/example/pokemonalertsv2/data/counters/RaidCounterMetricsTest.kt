package com.example.pokemonalertsv2.data.counters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RaidCounterMetricsTest {

    @Test
    fun `headlines use the display direction and units for every metric`() {
        val metrics = CounterMetrics(
            estimator = 1.25,
            overallPercent = 48.0,
            powerPercent = 72.0,
            tdo = 1234.0,
            deaths = 0.5,
            timeToWinSeconds = 92.25
        )
        assertEquals("48% overall", metrics.headline(CounterMetric.OVERALL))
        assertEquals("1.25 trainers", metrics.headline(CounterMetric.ESTIMATOR))
        assertEquals("92.3s", metrics.headline(CounterMetric.TIME))
        assertEquals("72% power", metrics.headline(CounterMetric.POWER))
        assertEquals("1234 damage", metrics.headline(CounterMetric.TDO))
        assertTrue(metrics.valueFor(CounterMetric.ESTIMATOR)!! < 2.0)
    }

    @Test
    fun `fresh defaults match the current Pokebattler controls`() {
        val defaults = RaidCounterOptions()
        assertEquals(40, defaults.attackerLevel)
        assertEquals(PokebattlerWeather.NONE, defaults.weather)
        assertEquals(PokebattlerFriendship.NONE, defaults.friendship)
        // Dodging is no longer a control; Pokebattler ignores the parameter.
        assertEquals(PokebattlerDodge.NONE, defaults.dodge)
        assertEquals(PokebattlerSort.ESTIMATOR, defaults.sort)
    }
}
