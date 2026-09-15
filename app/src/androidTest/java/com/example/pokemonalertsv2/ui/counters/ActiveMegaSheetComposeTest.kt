package com.example.pokemonalertsv2.ui.counters

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.pokemonalertsv2.data.counters.PersonalCounter
import com.example.pokemonalertsv2.data.counters.PersonalRanking
import com.example.pokemonalertsv2.data.counters.syntheticChargedMove
import com.example.pokemonalertsv2.data.counters.syntheticFastMove
import com.example.pokemonalertsv2.data.gamemaster.MegaSpecies
import com.example.pokemonalertsv2.data.pokegenie.OwnedPokemon
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ActiveMegaSheetComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun counter(pokemonId: String, dps: Double) = PersonalCounter(
        owned = OwnedPokemon(displayName = pokemonId, form = null, level = 40.0, atkIv = 15, defIv = 15, staIv = 15, cp = 3000,
            quickMove = "Counter", chargeMove = "Aura Sphere", shadow = false, lucky = false, matchKeys = listOf(pokemonId)),
        pokemonId = pokemonId, displayName = pokemonId, fastMove = syntheticFastMove("COUNTER"),
        chargedMove = syntheticChargedMove("AURA_SPHERE"), movesetAssumed = false, dps = dps, tdo = 500.0, rating = 50.0,
        estimatedAttackers = 6.0
    )

    @Test
    fun bestMegasFromTheCollectionComeFirstAndSelectOnTap() {
        var selected: String? = "none"
        val megas = listOf(
            MegaSpecies("ABSOL_MEGA", "Mega Absol", "ABSOL"),
            MegaSpecies("GENGAR_MEGA", "Mega Gengar", "GENGAR"),
            MegaSpecies("LUCARIO_MEGA", "Mega Lucario", "LUCARIO"),
        )
        val ranked = listOf(counter("MACHAMP_SHADOW_FORM", 21.0), counter("LUCARIO_MEGA", 19.4), counter("GENGAR_MEGA", 15.2))
        composeRule.setContent {
            PokemonAlertsV2Theme {
                ActiveMegaSheet(
                    state = RaidCountersUiState(
                        megaOptions = megas,
                        ownedBaseSpeciesIds = setOf("LUCARIO", "GENGAR"),
                        personal = PersonalRanking(ranked = ranked, team = emptyList(), combinedTdo = 0.0, bossHp = 15000)
                    ),
                    onSelect = { selected = it }
                )
            }
        }
        composeRule.onNodeWithText("Best megas for this raid").assertIsDisplayed()
        composeRule.onNodeWithText("#2 · 19.4 DPS").assertIsDisplayed()
        composeRule.onNodeWithText("#3 · 15.2 DPS").assertIsDisplayed()
        // Lucario shows twice (shortlist and grid); Absol, not in the ranking, only in the grid.
        assertEquals(2, composeRule.onAllNodesWithText("Mega Lucario").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("#2 · 19.4 DPS").performClick()
        assertEquals("LUCARIO_MEGA", selected)
    }
}
