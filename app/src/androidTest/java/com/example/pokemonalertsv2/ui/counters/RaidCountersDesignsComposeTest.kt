package com.example.pokemonalertsv2.ui.counters

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.pokemonalertsv2.data.counters.CounterSourceId
import com.example.pokemonalertsv2.data.counters.PokebattlerSort
import com.example.pokemonalertsv2.data.counters.PokebattlerWeather
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Each redesign of the counters screen renders, and its controls reach RaidCountersActions. */
class RaidCountersDesignsComposeTest {
    @get:Rule val compose = createComposeRule()

    private val state = mutableStateOf(RaidCountersUiState(visible = true, bossDisplayName = "Mewtwo"))
    private val actions = RaidCountersActions(
        onOptionsChanged = { state.value = state.value.copy(options = it) },
        onSourceChanged = { state.value = state.value.copy(source = it) }
    )

    @Test fun chipRowEditsWeatherInPlace() {
        compose.setContent {
            PokemonAlertsV2Theme { RaidCountersChipRow(state.value, actions, onBack = {}) }
        }
        compose.onNodeWithText("Mewtwo").assertIsDisplayed()
        // Said once, not twice.
        compose.onNodeWithTag("personal_source_hint").assertIsDisplayed()
        compose.onNodeWithTag("quick_chip_weather").performClick()
        compose.onNodeWithTag("weather_tile_rainy").performClick()
        compose.runOnIdle { assertEquals(PokebattlerWeather.RAINY, state.value.options.weather) }
        // The editor folds once a value is picked.
        compose.onNodeWithTag("weather_tile_rainy").assertDoesNotExist()
    }

    @Test fun collapsingOpensSetupSheetAndSwitchesPartyPower() {
        compose.setContent {
            PokemonAlertsV2Theme { RaidCountersCollapsing(state.value, actions, onBack = {}) }
        }
        compose.onNodeWithTag("open_setup").performClick()
        compose.onNodeWithText("Party Power").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(state.value.options.partyPower) }
        // The change shows as a removable chip on the screen; removing it turns it off again.
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNodeWithTag("rule_party").performClick()
        compose.runOnIdle { assertEquals(false, state.value.options.partyPower) }
    }

    @Test fun tabsEditSortInSetupTab() {
        state.value = state.value.copy(pokeGenieCount = 12)
        compose.setContent {
            PokemonAlertsV2Theme { RaidCountersTabs(state.value, actions, onBack = {}) }
        }
        compose.onNodeWithTag("counters_tab_team").performClick()
        compose.runOnIdle { assertEquals(CounterSourceId.POKE_GENIE, state.value.source) }
        compose.onNodeWithTag("counters_tab_setup").performClick()
        compose.onNodeWithTag("counters_tab_setup").assertIsSelected()
        compose.onNodeWithTag("setup_row_sort").performScrollTo().performClick()
        compose.onNodeWithText("Total damage").performClick()
        compose.runOnIdle { assertEquals(PokebattlerSort.TDO, state.value.options.sort) }
        // Back on the list page, the row states the new value.
        compose.onNodeWithText("Total damage").assertIsDisplayed()
    }
}
