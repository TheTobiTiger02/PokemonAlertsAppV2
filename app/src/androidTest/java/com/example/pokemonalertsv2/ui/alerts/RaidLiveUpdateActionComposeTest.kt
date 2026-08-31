package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RaidLiveUpdateActionComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun labeledActionCardOpensBossPicker() {
        var launches = 0
        composeRule.setContent {
            PokemonAlertsV2Theme {
                ManualRaidQuickAction(onClick = { launches++ })
            }
        }

        composeRule.onNodeWithText("Raid Live Update").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Choose a raid boss for hundo CP and recommended counters."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Choose boss").assertIsDisplayed()
        composeRule.onNodeWithTag("raid_live_update_action").performClick()

        composeRule.runOnIdle { assertEquals(1, launches) }
    }

    @Test
    @OptIn(ExperimentalMaterial3Api::class)
    fun alertsToolbarDoesNotContainManualRaidBell() {
        composeRule.setContent {
            PokemonAlertsV2Theme {
                AlertsToolbar(
                    onRefresh = {},
                    refreshing = false,
                    scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
                        rememberTopAppBarState()
                    )
                )
            }
        }

        composeRule.onNodeWithContentDescription("Start Raid Live Update").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Refresh alerts").assertIsDisplayed()
    }
}
