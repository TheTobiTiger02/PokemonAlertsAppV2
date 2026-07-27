package com.example.pokemonalertsv2.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import com.example.pokemonalertsv2.data.godex.GoDexEvolutionTarget
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import com.example.pokemonalertsv2.data.godex.GoDexSessionState
import com.example.pokemonalertsv2.ui.alerts.GoDexCatchTargetDialog
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GoDexCollectionComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun collectionDoesNotMutateUntilSelectedEntryIsConfirmed() {
        val changes = mutableListOf<Pair<String, Boolean>>()
        val entry = GoDexEntryEntity(
            entryKey = "0026_alola-female",
            pokedexId = 26,
            formSlug = "alola",
            gender = "female",
            displayName = "Alolan Raichu",
            needed = true
        )

        composeRule.setContent {
            PokemonAlertsV2Theme {
                GoDexCollectionContent(
                    entries = listOf(entry),
                    pendingEntryKeys = emptySet(),
                    canEdit = true,
                    sessionState = GoDexSessionState.AUTHENTICATED,
                    isSyncing = false,
                    syncError = null,
                    pendingCount = 0,
                    onSetCaught = { key, caught -> changes += key to caught },
                    onSignIn = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            "Alolan Raichu, Pok\u00e9dex 26, alola \u2022 female, still needed. " +
                "Select to Mark Alolan Raichu as caught"
        )
            .performClick()
        composeRule.runOnIdle { assertEquals(emptyList<Pair<String, Boolean>>(), changes) }
        composeRule.onNodeWithContentDescription("Confirm Alolan Raichu caught")
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("0026_alola-female" to true), changes)
        }
    }

    @Test
    fun readOnlyCollectionOffersSignInInsteadOfMutation() {
        composeRule.setContent {
            PokemonAlertsV2Theme {
                GoDexCollectionContent(
                    entries = listOf(
                        GoDexEntryEntity("0025-none", 25, null, "none", "Pikachu", true)
                    ),
                    pendingEntryKeys = emptySet(),
                    canEdit = false,
                    sessionState = GoDexSessionState.NONE,
                    isSyncing = false,
                    syncError = null,
                    pendingCount = 0,
                    onSetCaught = { _, _ -> error("Read-only collection mutated") },
                    onSignIn = {}
                )
            }
        }

        composeRule.onNodeWithText(
            "Read-only collection. Sign in from GoDex settings to mark entries caught here."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Sign in for two-way sync").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Pikachu, Pok\u00e9dex 25, Base form, still needed. Read-only"
        ).assertHasNoClickAction()
    }

    @Test
    fun gridTileAccessibilityPreservesExactIdentityAndPendingState() {
        composeRule.setContent {
            PokemonAlertsV2Theme {
                GoDexCollectionContent(
                    entries = listOf(
                        GoDexEntryEntity(
                            entryKey = "0026_alola-female",
                            pokedexId = 26,
                            formSlug = "alola",
                            gender = "female",
                            displayName = "Alolan Raichu",
                            needed = true
                        )
                    ),
                    pendingEntryKeys = setOf("0026_alola-female"),
                    canEdit = true,
                    sessionState = GoDexSessionState.REAUTH_REQUIRED,
                    isSyncing = false,
                    syncError = null,
                    pendingCount = 1,
                    onSetCaught = { _, _ -> },
                    onSignIn = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            "Alolan Raichu, Pok\u00e9dex 26, alola \u2022 female, still needed, " +
                "sync queued until sign-in. Select to Mark Alolan Raichu as caught"
        ).performScrollTo().assertIsDisplayed()
    }

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
