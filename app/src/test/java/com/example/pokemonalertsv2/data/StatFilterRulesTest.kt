package com.example.pokemonalertsv2.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatFilterRulesTest {
    private fun spawn(atk: Int? = null, def: Int? = null, sta: Int? = null, cp: Int? = null, level: Double? = null, type: String = "Spawn") =
        PokemonAlert(name = "Dratini", pokemon = "Dratini", type = listOf(type), ivAttack = atk, ivDefense = def, ivStamina = sta, cp = cp, level = level)

    private fun matches(alert: PokemonAlert, stats: StatFilterRules) =
        AlertFilterMatcher.matches(alert, FilterDefinition(alertTypes = FilterSelection.All, stats = stats), FilterMatchContext())

    @Test
    fun ivPercentNeedsAllThreeValues() {
        assertEquals(100, spawn(15, 15, 15).ivPercent())
        assertEquals(91, spawn(15, 14, 12).ivPercent())
        assertEquals(null, spawn(15, null, 15).ivPercent())
    }

    @Test
    fun floorsHideOnlyAlertsBelowThem() {
        val rules = StatFilterRules(minIvPercent = 90, minCp = 1000, minLevel = 20)
        assertTrue(matches(spawn(15, 14, 12, cp = 1200, level = 25.0), rules))
        assertFalse(matches(spawn(10, 10, 10, cp = 1200, level = 25.0), rules))
        assertFalse(matches(spawn(15, 15, 15, cp = 900, level = 25.0), rules))
        assertFalse(matches(spawn(15, 15, 15, cp = 1200, level = 15.0), rules))
    }

    @Test
    fun missingValuesNeverHideAnAlert() {
        assertTrue(matches(spawn(), StatFilterRules(minIvPercent = 90, minCp = 1000, minLevel = 30)))
    }

    @Test
    fun nundosIgnoreTheIvFloorButNotCp() {
        val nundo = spawn(0, 0, 0, cp = 500, type = "Nundo")
        assertTrue(matches(nundo, StatFilterRules(minIvPercent = 90)))
        assertFalse(matches(nundo, StatFilterRules(minCp = 1000)))
    }

    @Test
    fun raidsAreNotFilteredByStats() {
        val raid = PokemonAlert(name = "Mewtwo Raid", pokemon = "Mewtwo", type = listOf("Raid"), cp = 300)
        assertTrue(matches(raid, StatFilterRules(minCp = 2500)))
    }

    @Test
    fun documentsWithoutStatsStillDecode() {
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString(FilterDefinition.serializer(), "{}")
        assertEquals(StatFilterRules.None, decoded.stats)
    }
}
