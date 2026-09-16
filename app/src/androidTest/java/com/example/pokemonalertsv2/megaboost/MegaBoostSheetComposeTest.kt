package com.example.pokemonalertsv2.megaboost

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MegaBoostSheetComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val live = LiveSpeciesResponse(
        generatedAt = "2026-09-16T12:00:00.000Z",
        areas = listOf(
            LiveArea("Alsbach", 4, listOf(LiveSpeciesCount(4, count = 4))),
            LiveArea("Darmstadt-North", 5, listOf(LiveSpeciesCount(41, count = 3), LiveSpeciesCount(16, count = 2))),
        ),
    )
    private val types = mapOf(
        LiveSpecies(41) to listOf("POKEMON_TYPE_POISON", "POKEMON_TYPE_FLYING"),
        LiveSpecies(16) to listOf("POKEMON_TYPE_NORMAL", "POKEMON_TYPE_FLYING"),
        LiveSpecies(4) to listOf("POKEMON_TYPE_FIRE"),
    )
    private val megas = listOf(
        MegaCandidate("GENGAR_MEGA", "Mega Gengar", listOf("POKEMON_TYPE_GHOST", "POKEMON_TYPE_POISON"), owned = false),
        MegaCandidate("CHARIZARD_MEGA_Y", "Mega Charizard Y", listOf("POKEMON_TYPE_FIRE", "POKEMON_TYPE_FLYING"), owned = false),
    )

    @Test
    fun areasSwitchTotalsAndRankingAndATapSelects() {
        var state by mutableStateOf(MegaBoostUiState(live = live, area = "Darmstadt-North", types = types, megas = megas))
        var selected: String? = null
        composeRule.setContent {
            PokemonAlertsV2Theme {
                MegaBoostContent(state, onArea = { state = state.copy(area = it) }, onRefresh = {}, onSelect = { selected = it.pokemonId })
            }
        }
        composeRule.onNodeWithText("5 live Pokémon", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Flying 5").assertIsDisplayed()
        composeRule.onNodeWithText("Boosts 5 of 5 (100%)").assertIsDisplayed()
        composeRule.onNodeWithText("Boosts 3 of 5 (60%)").assertIsDisplayed()

        composeRule.onNodeWithTag("mega_boost_area_Alsbach").performClick()
        composeRule.onNodeWithText("4 live Pokémon", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Fire 4").assertIsDisplayed()
        composeRule.onNodeWithText("Boosts 0 of 4 (0%)").assertIsDisplayed()

        composeRule.onNodeWithTag("mega_boost_row_CHARIZARD_MEGA_Y").performClick()
        assertEquals("CHARIZARD_MEGA_Y", selected)
    }
}
