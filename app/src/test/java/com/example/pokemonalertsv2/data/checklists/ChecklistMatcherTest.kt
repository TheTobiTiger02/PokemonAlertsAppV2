package com.example.pokemonalertsv2.data.checklists

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
    private fun checklist(id: String, kind: ChecklistKind, alerts: Boolean = true, evolutions: Boolean = false) =
        ChecklistEntity(id = id, source = ChecklistEntity.SOURCE_GODEX, title = id, kind = kind.name, useForAlerts = alerts, includeEvolutions = evolutions)

    private fun entry(dex: Int, needed: Boolean) =
        GoDexEntryEntity(entryKey = "%04d-none".format(dex), pokedexId = dex, formSlug = null, gender = "none", displayName = "#$dex", needed = needed)

    private val graph = GoDexEvolutionGraph.forTests(listOf(GoDexEvolutionEdge(1, 2)))

    private val entries = mapOf(
        "shiny" to listOf(entry(1, needed = false), entry(2, needed = true)),
        "shadow" to listOf(entry(2, needed = true)),
        "dmax" to listOf(entry(2, needed = true))
    )

    @Test
    fun kindsOnlyMatchTheAlertsTheyCanCollect() {
        val spawn = PokemonAlert(name = "Ivysaur", pokedexId = 2)
        val shadowRaid = PokemonAlert(name = "Shadow Ivysaur", pokedexId = 2, type = listOf("Raid"))
        val maxBattle = PokemonAlert(name = "Ivysaur", pokedexId = 2, type = listOf("MaxBattle"))
        val hundo = PokemonAlert(name = "Ivysaur", pokedexId = 2, type = listOf("Hundo"))

        assertTrue(ChecklistMatcher.kindFits(ChecklistKind.SHINY, spawn))
        assertFalse(ChecklistMatcher.kindFits(ChecklistKind.SHADOW, spawn))
        assertTrue(ChecklistMatcher.kindFits(ChecklistKind.SHADOW, shadowRaid))
        assertFalse(ChecklistMatcher.kindFits(ChecklistKind.DYNAMAX, spawn))
        assertTrue(ChecklistMatcher.kindFits(ChecklistKind.DYNAMAX, maxBattle))
        assertFalse(ChecklistMatcher.kindFits(ChecklistKind.HUNDO, spawn))
        assertTrue(ChecklistMatcher.kindFits(ChecklistKind.HUNDO, hundo))

        val all = listOf(checklist("shiny", ChecklistKind.SHINY), checklist("shadow", ChecklistKind.SHADOW), checklist("dmax", ChecklistKind.DYNAMAX))
        assertEquals(listOf("shiny"), ChecklistMatcher.badges(spawn, all, entries, graph).map { it.checklistId })
        assertEquals(listOf("shiny", "shadow"), ChecklistMatcher.badges(shadowRaid, all, entries, graph).map { it.checklistId })
        assertEquals(listOf("shiny", "dmax"), ChecklistMatcher.badges(maxBattle, all, entries, graph).map { it.checklistId })
    }

    @Test
    fun evolutionsBadgeOnlyWhenSwitchedOn() {
        val bulbasaur = PokemonAlert(name = "Bulbasaur", pokedexId = 1)
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
