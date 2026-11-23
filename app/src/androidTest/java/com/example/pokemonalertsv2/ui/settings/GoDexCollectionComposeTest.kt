package com.example.pokemonalertsv2.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.pokemonalertsv2.data.godex.GoDexEvolutionTarget
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import com.example.pokemonalertsv2.ui.alerts.GoDexCatchTargetDialog
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GoDexCollectionComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun alertChooserRequiresConfirmationEvenForOneExactTarget() {
        val confirmed = mutableListOf<String>()
        composeRule.setContent {
            PokemonAlertsV2Theme {
                GoDexCatchTargetDialog(
                    pokemonName = "Pikachu",
                    matchResult = GoDexMatchResult(
                        status = GoDexMatchStatus.NEEDED,
                        matchedEntryKey = "0025-none"
                    ),
                    onDismiss = {},
                    onConfirm = confirmed::add
                )
            }
        }

        composeRule.runOnIdle { assertEquals(emptyList<String>(), confirmed) }
        composeRule.onNodeWithContentDescription("Confirm GoDex caught change")
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertEquals(listOf("0025-none"), confirmed) }
    }

    @Test
    fun alertChooserConfirmsOnlyTheSelectedRabootEntry() {
        val confirmed = mutableListOf<String>()
        composeRule.setContent {
            PokemonAlertsV2Theme {
                GoDexCatchTargetDialog(
                    pokemonName = "Scorbunny",
                    matchResult = scorbunnyAndRabootNeededResult(),
                    onDismiss = {},
                    onConfirm = confirmed::add
                )
            }
        }

        composeRule.onNodeWithContentDescription("Confirm GoDex caught change")
            .assertIsNotEnabled()
        composeRule.onNodeWithText("Raboot").performClick()
        composeRule.runOnIdle { assertEquals(emptyList<String>(), confirmed) }
        composeRule.onNodeWithContentDescription("Confirm GoDex caught change")
            .performClick()
        composeRule.runOnIdle { assertEquals(listOf("0814-none"), confirmed) }
    }

    @Test
    fun alertChooserConfirmsOnlyTheSelectedScorbunnyEntry() {
        val confirmed = mutableListOf<String>()
        composeRule.setContent {
            PokemonAlertsV2Theme {
                GoDexCatchTargetDialog(
                    pokemonName = "Scorbunny",
                    matchResult = scorbunnyAndRabootNeededResult(),
                    onDismiss = {},
                    onConfirm = confirmed::add
                )
            }
        }

        composeRule.onNodeWithText("Scorbunny").performClick()
        composeRule.runOnIdle { assertEquals(emptyList<String>(), confirmed) }
        composeRule.onNodeWithContentDescription("Confirm GoDex caught change")
            .performClick()
        composeRule.runOnIdle { assertEquals(listOf("0813-none"), confirmed) }
    }

    private fun scorbunnyAndRabootNeededResult() = GoDexMatchResult(
        status = GoDexMatchStatus.NEEDED,
        matchedEntryKey = "0813-none",
        evolutionTargets = listOf(
            GoDexEvolutionTarget("0814-none", 814, "Raboot", 1)
        )
    )
}
