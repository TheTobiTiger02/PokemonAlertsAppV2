package com.example.pokemonalertsv2.data.checklists

import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.database.ChecklistEntity
import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import com.example.pokemonalertsv2.data.godex.GoDexEvolutionEdge
import com.example.pokemonalertsv2.data.godex.GoDexEvolutionGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistMatcherTest {
    private fun checklist(id: String, kind: ChecklistKind, alerts: Boolean = true, evolutions: Boolean = false, alertTypes: String? = null) =
        ChecklistEntity(id = id, source = ChecklistEntity.SOURCE_GODEX, title = id, kind = kind.name, useForAlerts = alerts, includeEvolutions = evolutions, alertTypes = alertTypes)

    private fun entry(dex: Int, needed: Boolean) =
        GoDexEntryEntity(entryKey = "%04d-none".format(dex), pokedexId = dex, formSlug = null, gender = "none", displayName = "#$dex", needed = needed)

    private val graph = GoDexEvolutionGraph.forTests(listOf(GoDexEvolutionEdge(1, 2)))

    private val entries = mapOf(
        "shiny" to listOf(entry(1, needed = false), entry(2, needed = true)),
        "shadow" to listOf(entry(2, needed = true)),
        "dmax" to listOf(entry(2, needed = true)),
        "xxl" to listOf(entry(2, needed = true))
    )

    @Test
    fun kindsOnlyMatchTheAlertsTheyCanCollect() {
        val spawn = PokemonAlert(name = "Ivysaur", pokedexId = 2, type = listOf("PvP"))
        val shadowRaid = PokemonAlert(name = "Shadow Ivysaur", pokedexId = 2, type = listOf("Raid"))
        val maxBattle = PokemonAlert(name = "Ivysaur", pokedexId = 2, type = listOf("MaxBattle"))
        val hundo = PokemonAlert(name = "Ivysaur", pokedexId = 2, type = listOf("Hundo"))

        assertTrue(ChecklistMatcher.fits(checklist("s", ChecklistKind.SHINY), spawn))
        assertFalse(ChecklistMatcher.fits(checklist("s", ChecklistKind.SHADOW), spawn))
        assertTrue(ChecklistMatcher.fits(checklist("s", ChecklistKind.SHADOW), shadowRaid))
        assertFalse(ChecklistMatcher.fits(checklist("s", ChecklistKind.DYNAMAX), spawn))
        assertTrue(ChecklistMatcher.fits(checklist("s", ChecklistKind.DYNAMAX), maxBattle))
        assertFalse(ChecklistMatcher.fits(checklist("s", ChecklistKind.HUNDO), spawn))
        assertTrue(ChecklistMatcher.fits(checklist("s", ChecklistKind.HUNDO), hundo))

        val all = listOf(checklist("shiny", ChecklistKind.SHINY), checklist("shadow", ChecklistKind.SHADOW), checklist("dmax", ChecklistKind.DYNAMAX))
        assertEquals(listOf("shiny"), ChecklistMatcher.badges(spawn, all, entries, graph).map { it.checklistId })
        assertEquals(listOf("shiny", "shadow"), ChecklistMatcher.badges(shadowRaid, all, entries, graph).map { it.checklistId })
        assertEquals(listOf("shiny", "dmax"), ChecklistMatcher.badges(maxBattle, all, entries, graph).map { it.checklistId })
    }

    @Test
    fun sizeChecklistsNeedTheSizeOnTheAlert() {
        val xxl = checklist("xxl", ChecklistKind.XXL)
        val big = PokemonAlert(name = "Ivysaur", pokedexId = 2, type = listOf("PvP"), size = "XXL")
        val plain = PokemonAlert(name = "Ivysaur", pokedexId = 2, type = listOf("PvP"))
        val tiny = PokemonAlert(name = "Ivysaur", pokedexId = 2, type = listOf("PvP"), size = "XXS")
        assertEquals(listOf("xxl"), ChecklistMatcher.badges(big, listOf(xxl), entries, graph).map { it.checklistId })
        assertEquals(emptyList<ChecklistBadge>(), ChecklistMatcher.badges(plain, listOf(xxl), entries, graph))
        assertEquals(emptyList<ChecklistBadge>(), ChecklistMatcher.badges(tiny, listOf(xxl), entries, graph))
        assertEquals(ChecklistKind.XXL, ChecklistKind.fromGoDexCollection("/images/collections/normal.svg", "XXL Dex"))
        assertEquals(ChecklistKind.XXS, ChecklistKind.fromGoDexCollection("/images/collections/normal.svg", "XXS Dex"))
        assertEquals(ChecklistKind.ANY, ChecklistKind.fromGoDexCollection("/images/collections/normal.svg", "LivingNationalDex"))
    }

    @Test
    fun theUserPicksWhichAlertTypesBadge() {
        val quest = PokemonAlert(name = "Ivysaur", pokedexId = 2, type = listOf("Quest"), isShiny = true)
        val raid = PokemonAlert(name = "Ivysaur", pokedexId = 2, type = listOf("Raid"))
        val defaults = checklist("shiny", ChecklistKind.SHINY)
        assertTrue(ChecklistMatcher.badges(quest, listOf(defaults), entries, graph).single().shinySeen)

        val raidsOnly = checklist("shiny", ChecklistKind.SHINY, alertTypes = ChecklistMatcher.storeAlertTypes(ChecklistKind.SHINY, setOf(FilterAlertType.RAID)))
        assertEquals("RAID", raidsOnly.alertTypes)
        assertEquals(emptyList<ChecklistBadge>(), ChecklistMatcher.badges(quest, listOf(raidsOnly), entries, graph))
        assertFalse(ChecklistMatcher.badges(raid, listOf(raidsOnly), entries, graph).single().shinySeen)

        assertEquals("the defaults are stored as no choice", null, ChecklistMatcher.storeAlertTypes(ChecklistKind.SHINY, ChecklistMatcher.defaultAlertTypes(ChecklistKind.SHINY)))
        assertEquals(emptySet<FilterAlertType>(), ChecklistMatcher.parseAlertTypes(""))
        assertEquals(null, ChecklistMatcher.parseAlertTypes(null))
    }

    @Test
    fun evolutionsBadgeOnlyWhenSwitchedOn() {
        val bulbasaur = PokemonAlert(name = "Bulbasaur", pokedexId = 1, type = listOf("PvP"))
        val plain = listOf(checklist("shiny", ChecklistKind.SHINY))
        assertEquals("Bulbasaur itself is caught", emptyList<ChecklistBadge>(), ChecklistMatcher.badges(bulbasaur, plain, entries, graph))

        val withEvolutions = listOf(checklist("shiny", ChecklistKind.SHINY, evolutions = true))
        val badges = ChecklistMatcher.badges(bulbasaur, withEvolutions, entries, graph)
        assertEquals(1, badges.size)
        assertTrue("Ivysaur is still needed", badges.single().viaEvolution)
    }

    @Test
    fun checklistsSwitchedOffOrWithoutTheSpeciesGiveNothing() {
        val spawn = PokemonAlert(name = "Ivysaur", pokedexId = 2)
        assertEquals(emptyList<ChecklistBadge>(), ChecklistMatcher.badges(spawn, listOf(checklist("shiny", ChecklistKind.SHINY, alerts = false)), entries, graph))
        assertEquals(emptyList<ChecklistBadge>(), ChecklistMatcher.badges(PokemonAlert(name = "Mew", pokedexId = 151), listOf(checklist("shiny", ChecklistKind.SHINY)), entries, graph))
    }
}
