package com.example.pokemonalertsv2.ui.checklists

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import com.example.pokemonalertsv2.data.database.GoDexMarkUndo
import com.example.pokemonalertsv2.data.godex.GoDexSessionState
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GoDexChecklistEditorComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val raichu = GoDexEntryEntity("0026_alola-female", 26, "alola", "female", "Alolan Raichu", needed = true)
    private val pikachu = GoDexEntryEntity("0025-none", 25, null, "none", "Pikachu", needed = true)

    private fun show(
        canEdit: Boolean = true,
        onSetCaught: suspend (Set<String>, Boolean) -> List<GoDexMarkUndo> = { _, _ -> emptyList() },
        onUndo: suspend (List<GoDexMarkUndo>) -> Unit = {}
    ) {
        composeRule.setContent {
            PokemonAlertsV2Theme {
                GoDexChecklistEditor(
                    entries = listOf(pikachu, raichu),
                    pendingEntryKeys = emptySet(),
                    canEdit = canEdit,
                    sessionState = GoDexSessionState.AUTHENTICATED,
                    isSyncing = false,
                    syncError = null,
                    pendingCount = 0,
                    families = mapOf(25 to 25, 26 to 25),
                    typesByDexAndRegion = emptyMap(),
                    onSetCaught = onSetCaught,
                    onUndo = onUndo,
                    onSignIn = {}
                )
            }
        }
    }

    @Test
    fun aTapMarksAtOnceAndUndoPutsItBack() {
        val marks = mutableListOf<Pair<Set<String>, Boolean>>()
        val undone = mutableListOf<List<GoDexMarkUndo>>()
        val change = GoDexMarkUndo(raichu.entryKey, neededBefore = true, previous = null)
        show(onSetCaught = { keys, caught -> marks += keys to caught; listOf(change) }, onUndo = { undone += it })

        composeRule.onNodeWithContentDescription("Alolan Raichu, Pok", substring = true).performClick()
        composeRule.runOnIdle { assertEquals(listOf(setOf(raichu.entryKey) to true), marks) }
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.runOnIdle { assertEquals(listOf(listOf(change)), undone) }
    }

    @Test
    fun aLongPressSelectsAndTheFamilyIsMarkedTogether() {
        val marks = mutableListOf<Pair<Set<String>, Boolean>>()
        show(onSetCaught = { keys, caught -> marks += keys to caught; emptyList() })

        composeRule.onNodeWithContentDescription("Pikachu, Pok", substring = true).performTouchInput { longClick() }
        composeRule.runOnIdle { assertEquals("a long press only selects", emptyList<Pair<Set<String>, Boolean>>(), marks) }
        composeRule.onNodeWithText("Family").performClick()
        composeRule.onNodeWithText("Mark caught").performClick()
        composeRule.runOnIdle { assertEquals(listOf(setOf(pikachu.entryKey, raichu.entryKey) to true), marks) }
    }

    @Test
    fun aReadOnlyChecklistCannotBeMarked() {
        show(canEdit = false, onSetCaught = { _, _ -> error("read-only checklist marked") })
        composeRule.onNodeWithText("Sign in for two-way sync").assertExists()
        composeRule.onNodeWithContentDescription("Pikachu, Pok", substring = true).assertHasNoClickAction()
    }
}
