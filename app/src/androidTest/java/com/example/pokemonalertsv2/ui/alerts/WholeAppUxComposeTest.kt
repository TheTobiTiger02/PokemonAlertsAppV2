package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.NotificationPreset
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
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
    fun alertCardExposesSecondaryActionsFromOverflowAndInvokesCallbacks() {
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

        composeRule.onNodeWithText("Snooze").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("More alert actions").performClick()
        composeRule.onNodeWithText("Snooze")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertEquals(AlertSecondaryAction.SNOOZE, selectedAction)

        composeRule.onNodeWithContentDescription("More alert actions").performClick()
        composeRule.onNodeWithText("Open in picture-in-picture")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertEquals(AlertSecondaryAction.PICTURE_IN_PICTURE, selectedAction)

        composeRule.onNodeWithContentDescription("More alert actions").performClick()
        composeRule.onNodeWithText("Share").assertIsDisplayed().assertHasClickAction()
            .performClick()
        assertEquals(AlertSecondaryAction.SHARE, selectedAction)
    }

    @Test
    fun historyCardShowsOnlyRelevantVisibleActions() {
        composeRule.setContent {
            PokemonAlertsV2Theme {
                AlertCard(
                    alert = PokemonAlert(
                        name = "Expired Pikachu",
                        type = listOf("Spawn"),
                        endTime = "2020-01-01T00:00:00Z"
                    ),
                    distanceInfo = AlertDistanceInfo(null, null, null),
                    onOpenMaps = {},
                    onShowDetails = {},
                    onSecondaryAction = {},
                    cardContext = AlertCardContext.HISTORY
                )
            }
        }

        composeRule.onNodeWithText("Navigate").assertDoesNotExist()
        composeRule.onNodeWithText("Share").assertDoesNotExist()
        composeRule.onNodeWithText("Snooze").assertDoesNotExist()
        composeRule.onNodeWithText("I\u2019m going").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("More alert actions").performClick()
        composeRule.onNodeWithText("Share").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Open in picture-in-picture").assertDoesNotExist()
        composeRule.onNodeWithText("Snooze").assertDoesNotExist()
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

        composeRule.onNodeWithContentDescription("More alert actions")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithText("Snooze").assertHasClickAction()
        composeRule.onNodeWithText("Share").assertHasClickAction()
        composeRule.onNodeWithText("Open in picture-in-picture").assertHasClickAction()
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
        composeRule.onNodeWithText("Snooze").assertDoesNotExist()
        composeRule.onNodeWithText("Share").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("More alert actions")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun mapFilterRailKeepsEveryFilterAvailableAtNarrowPhoneWidth() {
        composeRule.setContent {
            PokemonAlertsV2Theme(darkTheme = true) {
                Box(modifier = androidx.compose.ui.Modifier.width(360.dp)) {
                    MapCategoryRail(
                        mutedCategories = emptySet(),
                        categoryCounts = mapOf(AlertCategory.RAID to 7),
                        advancedRuleCount = 0,
                        onMutedCategoriesChange = {},
                        onOpenFilters = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("map_filter_rail").assertIsDisplayed()
        composeRule.onNodeWithText("All").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Raids").assertExists()
        // Every filterable category is reachable now, not just the six that used to fit.
        composeRule.onNodeWithText("Spawns").assertExists()
        // Live counts ride on the chip they belong to.
        composeRule.onNodeWithText("7").assertExists()
    }

    @Test
    fun mapAlertDetailsPinsOpenDetailsAndMovesSecondaryActionsToOverflow() {
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
                            type = listOf("Quest"),
                            questTask = "Make 3 Great Throws in a row",
                            questReward = "Rare Candy",
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
        composeRule.onNodeWithText("Task: Make 3 Great Throws in a row").assertIsDisplayed()
        composeRule.onNodeWithText("Reward: Rare Candy").assertIsDisplayed()
        composeRule.onNodeWithText("Snooze").assertDoesNotExist()
        composeRule.onNodeWithText("Share").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("More map alert actions")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("Snooze").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Open in picture-in-picture")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithText("Share").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun mapHundoDetailsKeepTitleReadableWithGoDexAndUtilityActions() {
        composeRule.setContent {
            PokemonAlertsV2Theme {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .height(640.dp)
                ) {
                    MapAlertDetailContent(
                        alert = PokemonAlert(
                            name = "100% Außergewöhnlich langes Hatenna",
                            pokemon = "Hatenna",
                            pokemonLocation = "Sehr lange Straßenbezeichnung in Darmstadt",
                            type = listOf("Hundo"),
                            iv = "100%",
                            cp = 664,
                            endTime = "2099-01-01T00:00:00Z",
                            latitude = 49.87,
                            longitude = 8.65
                        ),
                        goDexStatus = GoDexMatchResult(GoDexMatchStatus.COLLECTED),
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
                        onOpenFullDetail = {},
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        goDexAction = {
                            FilledIconButton(
                                onClick = {},
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("test_godex_action")
                            ) {
                                Text("✓")
                            }
                        }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("map_alert_title_block")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(120.dp)
        composeRule.onNodeWithText("Hundo").assertIsDisplayed()
        composeRule.onNodeWithText("Already collected").assertIsDisplayed()
        composeRule.onNodeWithTag("test_godex_action")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithTag("map_open_details")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription("More map alert actions")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithText("I’m going").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Directions").assertIsDisplayed().assertHasClickAction()
    }
}
