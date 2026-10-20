package com.example.pokemonalertsv2.ui.alerts

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonReward
import com.example.pokemonalertsv2.data.PvpRanking
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Rule
import org.junit.Test

/**
 * The three redesigned alert details (A One column, B Tabs, C Map-first), each on a narrow phone
 * at font scale 1.3: a PvP alert shows its league table, a Rocket alert its reward tiles, and
 * More details opens.
 */
class AlertDetailDesignsComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun oneColumnShowsPvpTableRocketTilesAndMoreDetails() = checkOption(1, "alert_detail_one_column")

    @Test
    fun tabsShowsPvpTableRocketTilesAndMoreDetails() = checkOption(2, "alert_detail_tabs")

    @Test
    fun mapFirstShowsPvpTableRocketTilesAndMoreDetails() = checkOption(3, "alert_detail_map_first")

    @Test
    fun redesignedMapSheetPutsInformationBeforeActions() {
        composeRule.setContent {
            PokemonAlertsV2Theme {
                NarrowPhone {
                    MapAlertDetailContent(
                        alert = pvpAlert(),
                        distanceInfo = AlertDistanceInfo(1_200f, "1.2 km", "15 min walk"),
                        onDismiss = {},
                        isGoing = false,
                        onGoing = {},
                        onOpenMaps = {},
                        onShare = {},
                        onOpenFullDetail = {},
                        designOption = 1
                    )
                }
            }
        }
        composeRule.onNodeWithTag("map_alert_sheet").assertExists()
        composeRule.onNodeWithTag("alert_numbers_line").assertExists()
        composeRule.onNodeWithText("IV 15/15/15 · CP 1498 · L28 · 1.2 km · 15 min walk").assertExists()
        composeRule.onNodeWithText("Navigate").assertHasClickAction()
        composeRule.onNodeWithText("I’m going").assertHasClickAction()
        composeRule.onNodeWithText("Directions").assertDoesNotExist()
        composeRule.onNodeWithTag("map_open_details").assertHasClickAction()
        composeRule.onNodeWithText("Share").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("More map alert actions").performClick()
        composeRule.onNodeWithText("Snooze").assertHasClickAction()
        composeRule.onNodeWithText("Open in picture-in-picture").assertHasClickAction()
        composeRule.onNodeWithText("Share").assertHasClickAction()
        composeRule.onNodeWithText("Dismiss").assertHasClickAction()
    }

    private fun checkOption(option: Int, layoutTag: String) {
        var alert by mutableStateOf(pvpAlert())
        composeRule.setContent {
            PokemonAlertsV2Theme {
                NarrowPhone { AlertDetailScreen(alert = alert, designOption = option) }
            }
        }
        composeRule.onNodeWithTag(layoutTag).assertExists()
        composeRule.onNodeWithTag("pvp_league_table").assertExists()
        composeRule.onNodeWithText("#1").assertExists()
        composeRule.onNodeWithText("Great").assertExists()
        composeRule.onNodeWithText("Ultra").assertExists()

        composeRule.onNodeWithTag("alert_more_details_content").assertDoesNotExist()
        composeRule.onNodeWithTag("alert_more_details").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("alert_more_details_content").assertExists()
        composeRule.onNodeWithText("#0001").assertExists()
        composeRule.onNodeWithText("Copy").assertHasClickAction()

        composeRule.runOnIdle { alert = rocketAlert() }
        composeRule.onNodeWithTag("pvp_league_table").assertDoesNotExist()
        composeRule.onAllNodesWithTag("rocket_reward_tile").assertCountEquals(3)
        composeRule.onNodeWithText("Bulbasaur").assertExists()
        composeRule.onNodeWithText("Water").assertExists()
        composeRule.onNodeWithTag("alert_more_details").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("alert_more_details_content").assertExists()
    }

    /** About a Galaxy S21 set to a larger display size, with large text. */
    @androidx.compose.runtime.Composable
    private fun NarrowPhone(content: @androidx.compose.runtime.Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
            Box(Modifier.size(348.dp, 775.dp)) { content() }
        }
    }

    private fun pvpAlert() = PokemonAlert(
        id = 501,
        name = "Great #1 Bulbasaur",
        pokemon = "Bulbasaur",
        pokedexId = 1,
        type = listOf("PvP"),
        iv = "15/15/15",
        ivAttack = 15,
        ivDefense = 15,
        ivStamina = 15,
        cp = 1498,
        level = 28.0,
        pokemonLocation = "Old Mill Field",
        endTime = "2099-01-01T00:00:00Z",
        latitude = 49.86,
        longitude = 8.65,
        pvpRankings = listOf(
            PvpRanking(league = "Great League", pokemon = "Venusaur", rank = 1, cp = 1498, level = 28.0, percentage = 100.0),
            PvpRanking(league = "Ultra League", pokemon = "Venusaur", rank = 152, cp = 2490, level = 50.0, percentage = 97.3)
        )
    )

    private fun rocketAlert() = PokemonAlert(
        id = 502,
        name = "Water Rocket",
        type = listOf("Rocket"),
        gruntType = "Water",
        pokestop = "Town Hall Fountain",
        endTime = "2099-01-01T00:00:00Z",
        latitude = 49.87,
        longitude = 8.65,
        pokemonRewards = listOf(
            PokemonReward(rarity = "Common", pokemon = "Bulbasaur", percentage = 60),
            PokemonReward(rarity = "Rare", pokemon = "Squirtle", percentage = 30),
            PokemonReward(rarity = "Rare", pokemon = "Psyduck", percentage = 10)
        )
    )
}
