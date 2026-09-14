package com.example.pokemonalertsv2.ui.counters

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.pokemonalertsv2.data.counters.RaidCounterOptions
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PartyPowerComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun battleSetupToggleAndSaveUseCurrentPartyPower() {
        val state = mutableStateOf(RaidCountersUiState())
        var saved: RaidCounterOptions? = null
        compose.setContent {
            PokemonAlertsV2Theme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    BattleSetupSheet(state.value, RaidCountersActions(
                        onOptionsChanged = { state.value = state.value.copy(options = it) },
                        onSaveAsDefault = { saved = state.value.options }
                    ), onDismiss = {})
                }
            }
        }
        compose.onNodeWithText("Party Power").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(state.value.options.partyPower) }
        compose.onNodeWithText("Save as default").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(saved!!.partyPower) }
        compose.onNodeWithText("Party Power").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(state.value.options.partyPower) }
    }
}
