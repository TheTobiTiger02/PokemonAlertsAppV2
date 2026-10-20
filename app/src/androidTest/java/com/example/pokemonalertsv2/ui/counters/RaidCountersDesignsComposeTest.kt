package com.example.pokemonalertsv2.ui.counters

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.pokemonalertsv2.data.counters.PokebattlerWeather
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertEquals
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


}
