package com.example.pokemonalertsv2.ui.alerts.mappanel

import com.example.pokemonalertsv2.data.DistanceOverrides
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.QuestFilterRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveFiltersTest {
    private val everything = FilterDefinition(
        alertTypes = FilterSelection.only(listOf(FilterAlertType.RAID.name, FilterAlertType.QUEST.name)),
        areas = FilterSelection.only(listOf("Alsbach")),
        maxDistanceMeters = 1_000,
        maxWalkingMinutes = 15,
        distanceOverrides = DistanceOverrides().withType(FilterAlertType.RAID, 2_000),
        hundoSpecies = FilterSelection.only(listOf("pikachu", "eevee")),
        pvpSpecies = FilterSelection.None,
        raidTiers = FilterSelection.only(listOf("5", "Mega")),
        rocketTypes = FilterSelection.only(listOf("Fire")),
        quests = QuestFilterRules(facetEnabled = true)
    )

    @Test
    fun defaultsShowNoChips() {
        assertTrue(activeFilterChips(FilterDefinition()).isEmpty())
    }

    @Test
    fun everyNarrowingFieldGetsALabelledChip() {
        assertEquals(
            listOf(
                "Types: 2 of 11",
                "≤ 1 km",
                "≤ 15 min walk",
                "1 distance rule",
                "Hundo species: 2",
                "PvP species: none",
                "Raid tiers: 2",
                "Rocket: 1",
                "Quests: custom",
                "Areas: 1"
            ),
            activeFilterChips(everything).map { it.label }
        )
    }

    @Test
    fun catalogTotalsShowHowMuchIsLeft() {
        val catalog = FilterCatalog(raidTiers = listOf("1", "3", "5", "Mega"))
        assertEquals(
            "Raid tiers: 2 of 4",
            activeFilterChips(everything, catalog).single { it.id == "raid_tiers" }.label
        )
    }

    @Test
    fun eachChipClearsOnlyItsOwnRule() {
        val chips = activeFilterChips(everything)
        chips.forEach { chip ->
            val remaining = activeFilterChips(chip.clear(everything)).map { it.id }
            assertEquals(chips.map { it.id } - chip.id, remaining)
        }
    }

    @Test
    fun clearingEveryChipRestoresTheDefaults() {
        val cleared = activeFilterChips(everything).fold(everything) { definition, chip -> chip.clear(definition) }
        assertEquals(FilterDefinition(), cleared)
    }
}
