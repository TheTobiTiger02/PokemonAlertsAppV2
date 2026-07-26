package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Rule
import org.junit.Test

class ArrivalTrackingComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun alertCardShowsIndependentGoingAndNavigateActionsAndActiveState() {
        var isGoing by mutableStateOf(false)
        composeRule.setContent {
            PokemonAlertsV2Theme {
                AlertCard(
                    alert = PokemonAlert(
                        name = "Hundo Pikachu",
                        pokemon = "Pikachu",
                        latitude = 49.86,
                        longitude = 8.65
                    ),
                    distanceInfo = AlertDistanceInfo(null, null, null),
                    onOpenMaps = {},
                    onShowDetails = {},
                    onPipClick = {},
                    onShareClick = {},
                    isGoing = isGoing,
                    onGoingClick = { isGoing = !isGoing }
                )
            }
        }

        composeRule.onNodeWithText("I\u2019m going")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithText("Navigate")
            .assertIsDisplayed()
            .assertHasClickAction()

        composeRule.onNodeWithText("I\u2019m going").performClick()
        composeRule.onNodeWithText("Stop").assertIsDisplayed()
    }
}
