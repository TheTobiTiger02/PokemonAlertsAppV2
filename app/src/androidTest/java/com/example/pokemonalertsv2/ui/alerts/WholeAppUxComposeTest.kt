package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
    fun alertCardShowsSecondaryActionsAndInvokesTheirCallbacksDirectly() {
        var selectedAction: AlertSecondaryAction? = null
        composeRule.setContent {
            PokemonAlertsV2Theme {
                AlertCard(
                    alert = PokemonAlert(name = "Pikachu", type = listOf("Spawn")),
                    distanceInfo = AlertDistanceInfo(null, null, null),
                    onOpenMaps = {},
                    onShowDetails = {},
                    onSecondaryAction = { selectedAction = it }
                )
            }
        }

        composeRule.onNodeWithText("Snooze")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertEquals(AlertSecondaryAction.SNOOZE, selectedAction)

        composeRule.onNodeWithContentDescription("Open alert in picture-in-picture")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertEquals(AlertSecondaryAction.PICTURE_IN_PICTURE, selectedAction)

        composeRule.onNodeWithText("Share").assertIsDisplayed().assertHasClickAction()
            .performClick()
        assertEquals(AlertSecondaryAction.SHARE, selectedAction)
        composeRule.onNodeWithText("More").assertDoesNotExist()
    }

    @Test
    fun historyCardShowsOnlyRelevantVisibleActions() {
        composeRule.setContent {
            PokemonAlertsV2Theme {
                AlertCard(
                    alert = PokemonAlert(name = "Expired Pikachu", type = listOf("Spawn")),
                    distanceInfo = AlertDistanceInfo(null, null, null),
                    onOpenMaps = {},
                    onShowDetails = {},
                    onSecondaryAction = {},
                    cardContext = AlertCardContext.HISTORY
                )
            }
        }

        composeRule.onNodeWithText("Navigate").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithContentDescription("Open alert in picture-in-picture")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithText("Share").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Snooze").assertDoesNotExist()
        composeRule.onNodeWithText("I\u2019m going").assertDoesNotExist()
        composeRule.onNodeWithText("More").assertDoesNotExist()
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
                        onSecondaryAction = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Snooze").assertHasClickAction()
        composeRule.onNodeWithText("Share").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Open alert in picture-in-picture")
            .assertHasClickAction()
        composeRule.onNodeWithText("More").assertDoesNotExist()
    }

    @Test
    fun compactAlertCardKeepsPrimaryActionsVisibleAtNarrowPhoneWidth() {
        composeRule.setContent {
            PokemonAlertsV2Theme {
                Box(modifier = androidx.compose.ui.Modifier.width(360.dp)) {
                    AlertCard(
                        alert = PokemonAlert(
                            name = "100% Au\u00dfergew\u00f6hnlich langes Pok\u00e9mon",
                            pokemon = "Au\u00dfergew\u00f6hnlich langes Pok\u00e9mon",
                            pokemonLocation = "Sehr lange Stra\u00dfenbezeichnung in Darmstadt",
                            type = listOf("Hundo"),
                            latitude = 49.86,
                            longitude = 8.65
                        ),
                        distanceInfo = AlertDistanceInfo(
                            distanceMeters = 1_300f,
                            distanceText = "1.3 km",
                            walkingText = "16 min walk"
                        ),
                        onOpenMaps = {},
                        onShowDetails = {},
                        onSecondaryAction = {},
                        onGoingClick = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("I\u2019m going").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Navigate").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Snooze").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithContentDescription("Open alert in picture-in-picture")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithText("Share").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("More").assertDoesNotExist()
    }

    @Test
    fun mapFilterRailKeepsEveryFilterOnAnOpaqueControlSurface() {
        composeRule.setContent {
            PokemonAlertsV2Theme(darkTheme = true) {
                Box(modifier = androidx.compose.ui.Modifier.width(360.dp)) {
                    MapFilterRow(
                        filters = AlertFilter.values().toList(),
                        selectedFilter = AlertFilter.ALL,
                        visibleAlertCount = 4,
                        onFilterSelected = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("map_filter_rail").assertIsDisplayed()
        composeRule.onNodeWithText("All").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Raids").assertExists()
        composeRule.onNodeWithText("4 alerts").assertDoesNotExist()
    }

    @Test
    fun mapAlertDetailsPinsOpenDetailsWhileKeepingDirectActionsVisible() {
        composeRule.setContent {
            PokemonAlertsV2Theme {
                Box(
                    modifier = androidx.compose.ui.Modifier
                        .width(360.dp)
                        .height(640.dp)
                ) {
                    MapAlertDetailContent(
                        alert = PokemonAlert(
                            name = "Außergewöhnlich langes Pikachu",
                            pokemon = "Pikachu",
                            pokemonLocation = "Sehr lange Straßenbezeichnung in Darmstadt",
                            type = listOf("Spawn"),
                            endTime = "2099-01-01T00:00:00Z",
                            latitude = 49.87,
                            longitude = 8.65
                        ),
                        distanceInfo = AlertDistanceInfo(
                            distanceMeters = 1_200f,
                            distanceText = "1.2 km",
                            walkingText = "15 min"
                        ),
                        onDismiss = {},
                        isGoing = false,
                        onGoing = {},
                        onOpenMaps = {},
                        onShare = {},
                        onOpenFullDetail = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("map_open_details")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithText("I’m going").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Directions").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Snooze").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Share").assertIsDisplayed().assertHasClickAction()
    }
}
