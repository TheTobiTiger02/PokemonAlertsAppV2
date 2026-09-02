package com.example.pokemonalertsv2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestRewardKindTest {
    private val dex = setOf("spinda", "chimchar", "blaziken", "charizard", "ponyta galarian")

    @Test
    fun energyAndCandyWinOverTheSpeciesNameTheyContain() {
        assertEquals(QuestRewardKind.MEGA_ENERGY, classifyQuestReward("Blaziken Mega Energy x10", dex))
        assertEquals(QuestRewardKind.CANDY, classifyQuestReward("Rare Candy", dex))
        assertEquals(QuestRewardKind.STARDUST, classifyQuestReward("500 Stardust", dex))
    }

    @Test
    fun plainSpeciesRewardsResolveToArtwork() {
        assertEquals(QuestRewardKind.POKEMON, classifyQuestReward("Spinda", dex))
        assertEquals("spinda", questRewardSpeciesKey("Spinda", dex))
        assertEquals(null, questRewardSpeciesKey("Razz Berry x3", dex))
    }

    @Test
    fun rewardThumbnailsComeFromTheAlertsThemselves() {
        val alerts = listOf(
            PokemonAlert(
                name = "Quest", type = listOf("Quest"),
                questTask = "Catch 5", questReward = "500 Stardust",
                thumbnailUrl = "https://icons/reward/stardust/500.png"
            ),
            PokemonAlert(
                name = "Quest", type = listOf("Quest"),
                questTask = "Catch 5", questReward = "Spinda",
                thumbnailUrl = "https://icons/pokemon/327.png"
            ),
            // A spawn must not contribute a reward icon.
            PokemonAlert(name = "Pikachu", type = listOf("Spawn"), pokemon = "Pikachu", thumbnailUrl = "https://icons/pokemon/25.png")
        )
        val thumbnails = questRewardThumbnails(alerts)
        assertEquals("https://icons/reward/stardust/500.png", thumbnails["500 stardust"])
        assertEquals("https://icons/pokemon/327.png", thumbnails["spinda"])
        assertEquals(2, thumbnails.size)
    }

    @Test
    fun speciesLimitCandidatesIncludeQuestOnlySpecies() {
        val catalog = FilterCatalog(
            spawnSpecies = listOf("Chimchar"),
            raidSpecies = listOf("Registeel"),
            quests = listOf(
                FilterCatalogQuest("a::b", "a", "b", "Catch 5", "Spinda", true),
                FilterCatalogQuest("a::c", "a", "c", "Catch 5", "500 Stardust", true)
            )
        )
        val candidates = catalog.speciesLimitCandidates(dex)
        // Spinda never spawns here but is a reward, so it must still be limitable.
        assertTrue("Spinda" in candidates)
        assertTrue("Chimchar" in candidates)
        assertTrue("Registeel" in candidates)
        assertTrue(candidates.none { it.contains("Stardust") })
    }
}
