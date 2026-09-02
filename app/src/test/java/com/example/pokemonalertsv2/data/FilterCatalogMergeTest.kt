package com.example.pokemonalertsv2.data

import org.junit.Assert.*
import org.junit.Test

class FilterCatalogMergeTest {
    @Test fun offlineCatalogCombinesCacheLocalSpeciesAndObservedQuests() {
        val old = FilterCatalogQuest("spin 3::dust", "spin 3", "dust", "Spin 3", "Dust", false)
        val cached = FilterCatalog(quests = listOf(old), spawnSpecies = listOf("Flabébé"), areas = listOf("Alsbach"))
        val observed = PokemonAlert(name = "Quest", type = listOf("Quest"), questTask = "Catch 5 Pokémon", questReward = "Pikachu", area = "Darmstadt")
        val merged = mergeFilterCatalog(cached, listOf("Flabebe", "Mewtwo"), listOf(observed))
        assertEquals(2, merged.spawnSpecies.size)
        assertEquals(setOf("Alsbach", "Darmstadt"), merged.areas.toSet())
        assertEquals(2, merged.quests.size)
        assertTrue(merged.quests.first().active)
        assertEquals("catch 5 pokemon::pikachu", merged.quests.first().key)
        assertTrue(merged.quests.contains(old))
        assertTrue(merged.raidTiers.containsAll(RaidTier.entries.map { it.displayLabel }))
        assertTrue(merged.rocketTypes.isNotEmpty())
    }
}
