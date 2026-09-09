package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.data.FilterCatalogQuest
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.QuestFilterRules
import com.example.pokemonalertsv2.data.QuestPairRule
import org.junit.Assert.assertEquals
import org.junit.Test

class HuntNameTest {

    private val catalog = FilterCatalog(
        rocketTypes = listOf("Dragon", "Water"),
        raidTiers = listOf("Tier 5"),
        quests = listOf(
            FilterCatalogQuest(
                key = "spin-spinda",
                taskKey = "spin-5-stops",
                rewardKey = "spinda",
                task = "Spin 5 PokéStops",
                reward = "Spinda"
            )
        )
    )

    @Test
    fun `a single grunt type names the hunt`() {
        val definition = FilterDefinition(
            alertTypes = FilterSelection.only(listOf(FilterAlertType.ROCKET.name)),
            rocketTypes = FilterSelection.only(listOf("Dragon"))
        )

        assertEquals("Dragon grunts", huntName(definition, catalog))
    }

    @Test
    fun `all grunts falls back to the category`() {
        val definition = FilterDefinition(
            alertTypes = FilterSelection.only(listOf(FilterAlertType.ROCKET.name))
        )

        assertEquals("Rocket grunts", huntName(definition, catalog))
    }

    @Test
    fun `an exact quest pair is named by its reward`() {
        val definition = FilterDefinition(
            alertTypes = FilterSelection.only(listOf(FilterAlertType.QUEST.name)),
            quests = QuestFilterRules(
                exactPairs = setOf(QuestPairRule("spin-5-stops", "spinda"))
            )
        )

        assertEquals("Spinda quests", huntName(definition, catalog))
    }

    @Test
    fun `a quest pair the catalog has never heard of still names something sane`() {
        val definition = FilterDefinition(
            alertTypes = FilterSelection.only(listOf(FilterAlertType.QUEST.name)),
            quests = QuestFilterRules(exactPairs = setOf(QuestPairRule("unknown", "mystery")))
        )

        assertEquals("Quests", huntName(definition, catalog))
    }

    @Test
    fun `raids prefer the boss, then the tier`() {
        val byTier = FilterDefinition(
            alertTypes = FilterSelection.only(listOf(FilterAlertType.RAID.name)),
            raidTiers = FilterSelection.only(listOf("Tier 5"))
        )
        assertEquals("Tier 5 raids", huntName(byTier, catalog))

        val byBoss = byTier.copy(raidSpecies = FilterSelection.only(listOf("Rayquaza")))
        assertEquals("Rayquaza raids", huntName(byBoss, catalog))
    }

    @Test
    fun `a single spawn species is just the species`() {
        val definition = FilterDefinition(
            alertTypes = FilterSelection.only(listOf(FilterAlertType.SPAWN.name)),
            spawnSpecies = FilterSelection.only(listOf("Larvitar"))
        )

        assertEquals("Larvitar", huntName(definition, catalog))
    }

    @Test
    fun `several species stop being a name and become a count`() {
        val definition = FilterDefinition(
            alertTypes = FilterSelection.only(listOf(FilterAlertType.SPAWN.name)),
            spawnSpecies = FilterSelection.only(listOf("Larvitar", "Bagon"))
        )

        assertEquals("Spawns", huntName(definition, catalog))
    }

    @Test
    fun `two types are both named rather than one silently winning`() {
        val definition = FilterDefinition(
            alertTypes = FilterSelection.only(
                listOf(FilterAlertType.HUNDO.name, FilterAlertType.PVP.name)
            )
        )

        assertEquals("Hundos & PvP", huntName(definition, catalog))
    }

    @Test
    fun `many types collapse to a count`() {
        val definition = FilterDefinition(
            alertTypes = FilterSelection.only(
                listOf(
                    FilterAlertType.HUNDO.name,
                    FilterAlertType.PVP.name,
                    FilterAlertType.RARE.name
                )
            )
        )

        assertEquals("3 alert types", huntName(definition, catalog))
    }

    @Test
    fun `an unnarrowed definition is named Everything`() {
        assertEquals("Everything", huntName(FilterDefinition(), catalog))
    }
}
