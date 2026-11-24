package com.example.pokemonalertsv2.data.dextracker

import org.junit.Assert.assertEquals
import org.junit.Test

class DexTrackerAlertIndexTest {
    private val catalog = DexTrackerCatalog.forTests(
        listOf(
            DexTrackerPokemon(1, 1, "Bulbasaur", listOf(1, 2, 3), listOf("grass")),
            DexTrackerPokemon(2, 2, "Ivysaur", listOf(2, 3), listOf("grass")),
            DexTrackerPokemon(3, 3, "Venusaur", listOf(3), listOf("grass")),
            DexTrackerPokemon(10100, 26, "Raichu (Alola)", listOf(10100), listOf("electric"))
        )
    )

    private fun needed(entries: List<DexTrackerEntry>) =
        DexTrackerAlertIndex.entries(entries, catalog).associate { it.pokedexId to it.needed }

    @Test
    fun aSpeciesIsNeededWhileAStageOfItHasToBeCaught() {
        val line = DexTrackerEntry(id = "e", pokemonId = 1, name = "Bulbasaur", target = 3, targets = listOf(1, 2, 3), owned = mapOf("1" to 1))
        assertEquals(mapOf(1 to false, 2 to true, 3 to true), needed(listOf(line)))
        // An unassigned copy covers Ivysaur, a spare Venusaur: nothing left to catch.
        assertEquals(mapOf(1 to false, 2 to false, 3 to false), needed(listOf(line.copy(unassigned = 1, extras = 1))))
    }

    @Test
    fun formsCountForTheirSpeciesAndAnyEntryNeedingItWins() {
        val alolan = DexTrackerEntry(id = "a", pokemonId = 10100, name = "Raichu (Alola)", target = 1, targets = listOf(10100))
        val noStages = DexTrackerEntry(id = "b", pokemonId = 2, name = "Ivysaur", target = 2, unassigned = 1)
        val ivysaurDone = DexTrackerEntry(id = "c", pokemonId = 2, name = "Ivysaur", target = 1, targets = listOf(2), owned = mapOf("2" to 1))
        assertEquals(mapOf(26 to true, 2 to true), needed(listOf(alolan, ivysaurDone, noStages)))
    }
}
