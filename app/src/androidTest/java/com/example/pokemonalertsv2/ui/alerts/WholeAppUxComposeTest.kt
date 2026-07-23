package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.example.pokemonalertsv2.data.NotificationPreset
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.onboarding.OnboardingScreen
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WholeAppUxComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onboardingSavesSetupOnlyWhenFinished() {
        var savedArea = "All"
        var savedDistance = 0
        var savedPreset = NotificationPreset.EVERYTHING

        composeRule.setContent {
            PokemonAlertsV2Theme {
                OnboardingScreen(
                    initialArea = "All",
                    initialMaxDistance = 0,
                    onAreaChanged = { savedArea = it },
                    onMaxDistanceChanged = { savedDistance = it },
                    onPresetSelected = { savedPreset = it },
                    onFinish = {}
                )
            }
        }

        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Darmstadt").performClick()
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Quiet essentials").performClick()
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Enable & finish").performClick()

        assertEquals("Darmstadt", savedArea)
        assertEquals(0, savedDistance)
        assertEquals(NotificationPreset.QUIET_ESSENTIALS, savedPreset)
    }

    @Test
    fun alertCardKeepsShareAndPipVisibleWithoutOverflow() {
        composeRule.setContent {
            PokemonAlertsV2Theme {
                AlertCard(
                    alert = PokemonAlert(name = "Pikachu", type = listOf("Spawn")),
                    distanceInfo = AlertDistanceInfo(null, null, null),
                    onOpenMaps = {},
                    onShowDetails = {},
                    onPipClick = {},
                    onShareClick = {},
                    onSnoozeClick = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Share").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithContentDescription("Open alert in picture-in-picture")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription("More alert actions").assertDoesNotExist()
    }

    @Test
    fun alertCardActionsRemainAccessibleAtTwoHundredPercentFontScale() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 2f)
            ) {
                PokemonAlertsV2Theme {
                    AlertCard(
                        alert = PokemonAlert(name = "Pikachu", type = listOf("Spawn")),
                        distanceInfo = AlertDistanceInfo(null, null, null),
                        onOpenMaps = {},
                        onShowDetails = {},
                        onPipClick = {},
                        onShareClick = {},
                        onSnoozeClick = {}
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Snooze alert").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Share").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Open alert in picture-in-picture")
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription("More alert actions").assertDoesNotExist()
    }
}
