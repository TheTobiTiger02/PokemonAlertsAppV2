package com.example.pokemonalertsv2.ui.alerts

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.settings.SwitchSetting
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Rule
import org.junit.Test

class Material3ComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun feedCardShowsMetricsCategoryAndQuickActions() {
        composeRule.setContent {
            PokemonAlertsV2Theme {
                AlertCard(
                    alert = sampleAlert(),
                    distanceInfo = AlertDistanceInfo(
                        distanceMeters = 420f,
                        distanceText = "420 m",
                        walkingText = null
                    ),
                    onOpenMaps = {},
                    onShowDetails = {},
                    onPipClick = {},
                    onShareClick = {},
                    onSnoozeClick = {},
                    nowMillis = 0L
                )
            }
        }

        composeRule.onNodeWithText("IV 15/15/15").assertExists()
        composeRule.onNodeWithText("CP 1234").assertExists()
        composeRule.onNodeWithText("Hundo").assertExists()
        composeRule.onNodeWithText("420 m").assertExists()
        composeRule.onNodeWithContentDescription("Snooze alert").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Share").assertHasClickAction()
        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.open_alert_in_pip))
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription("More alert actions").assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.open_in_maps))
            .assertHasClickAction()
    }

    @Test
    fun detailScreenKeepsPersistentActionsAccessible() {
        composeRule.setContent {
            PokemonAlertsV2Theme {
                AlertDetailScreen(alert = sampleAlert())
            }
        }

        composeRule.onNodeWithText("Snooze").assertHasClickAction()
        composeRule.onNodeWithText("Navigate").assertHasClickAction()
        composeRule.onNodeWithText("Share").assertHasClickAction()
    }

    @Test
    fun settingsSwitchKeepsLabelAndControlAccessible() {
        composeRule.setContent {
            PokemonAlertsV2Theme {
                SwitchSetting(
                    title = "Enable Notifications",
                    subtitle = "Receive alerts for new Pokemon nearby",
                    checked = true,
                    onCheckedChange = {}
                )
            }
        }

        composeRule.onNodeWithText("Enable Notifications").assertExists()
        composeRule.onNodeWithText("Receive alerts for new Pokemon nearby").assertExists()
    }

    private fun sampleAlert(): PokemonAlert = PokemonAlert(
        name = "100% Bulbasaur",
        description = "A Material 3 test alert",
        endTime = "2099-01-01T00:00:00Z",
        type = listOf("Hundo"),
        pokemon = "Bulbasaur",
        pokemonLocation = "Old Mill Field",
        iv = "15/15/15",
        ivAttack = 15,
        ivDefense = 15,
        ivStamina = 15,
        cp = 1234,
        level = 35.0
    )
}
