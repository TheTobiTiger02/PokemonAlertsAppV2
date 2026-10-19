package com.example.pokemonalertsv2.ui.alerts.mappanel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.data.FilterCatalogQuest
import com.example.pokemonalertsv2.data.QuestPairRule
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Filters tabs edit the map definition in place. */
@RunWith(AndroidJUnit4::class)
class MapFiltersSheetComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var definition by mutableStateOf(FilterDefinition())
    private var studioOpened = 0

    @Test
    fun tabsEditInPlace() {
        composeRule.setContent { Sheet() }

        composeRule.onNodeWithText("Max distance").assertIsDisplayed()
        composeRule.onNodeWithTag("map_filter_studio").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, studioOpened) }

        // Raid bosses moved to the Raids tab.
        composeRule.onNodeWithTag("map_filter_tab_pokemon").performClick()
        composeRule.onNodeWithTag("species_picker_grid").assertExists()
        composeRule.onNodeWithText("Raid Bosses").assertDoesNotExist()

        composeRule.onNodeWithTag("map_filter_tab_raids").performClick()
        composeRule.onNodeWithTag("token_tile_mega").performClick()
        composeRule.runOnIdle { assertEquals(FilterSelection.only(listOf("1", "5")), definition.raidTiers) }
        composeRule.onNodeWithTag("map_raids_bosses").performClick()
        composeRule.onNodeWithTag("species_picker_grid").assertExists()

        composeRule.onNodeWithTag("map_filter_tab_rocket").performClick()
        composeRule.onNodeWithTag("token_tile_fire").performClick()
        composeRule.runOnIdle { assertEquals(FilterSelection.only(listOf("Water")), definition.rocketTypes) }
    }

    @Test
    fun questsTabTogglesARewardInPlace() {
        composeRule.setContent { Sheet() }

        composeRule.onNodeWithTag("map_filter_tabs").performScrollToNode(hasTestTag("map_filter_tab_quests"))
        composeRule.onNodeWithTag("map_filter_tab_quests").performClick()
        composeRule.onNodeWithTag("quest_reward_razz berry").performClick()

        composeRule.runOnIdle {
            assertEquals(
                setOf(QuestPairRule("catch 5", "500 stardust"), QuestPairRule("spin 3", "500 stardust")),
                definition.quests.exactPairs
            )
        }
    }

    @Test
    fun holdingARewardPicksSingleTasks() {
        composeRule.setContent { Sheet() }

        composeRule.onNodeWithTag("map_filter_tabs").performScrollToNode(hasTestTag("map_filter_tab_quests"))
        composeRule.onNodeWithTag("map_filter_tab_quests").performClick()
        composeRule.onNodeWithTag("quest_reward_500 stardust").performTouchInput { longClick() }
        composeRule.onNodeWithTag("quest_task_spin 3").performClick()

        composeRule.runOnIdle {
            assertEquals(
                setOf(
                    QuestPairRule("catch 5", "500 stardust"),
                    QuestPairRule("throw 3", "razz berry"),
                    QuestPairRule("catch 5", "razz berry")
                ),
                definition.quests.exactPairs
            )
        }
    }

    @Test
    fun byTaskViewTogglesATaskAndPicksItsRewards() {
        composeRule.setContent { Sheet() }

        composeRule.onNodeWithTag("map_filter_tabs").performScrollToNode(hasTestTag("map_filter_tab_quests"))
        composeRule.onNodeWithTag("map_filter_tab_quests").performClick()
        composeRule.onNodeWithTag("quest_view_tasks").performClick()
        composeRule.onNodeWithTag("quest_task_tile_catch 5").performClick()
        composeRule.runOnIdle {
            assertEquals(
                setOf(QuestPairRule("spin 3", "500 stardust"), QuestPairRule("throw 3", "razz berry")),
                definition.quests.exactPairs
            )
        }

        composeRule.onNodeWithTag("quest_rewards_catch 5").performClick()
        composeRule.onNodeWithTag("quest_pick_reward_razz berry").performClick()
        composeRule.runOnIdle {
            assertEquals(
                setOf(
                    QuestPairRule("spin 3", "500 stardust"),
                    QuestPairRule("throw 3", "razz berry"),
                    QuestPairRule("catch 5", "razz berry")
                ),
                definition.quests.exactPairs
            )
        }
    }

    @Composable
    private fun Sheet() {
        PokemonAlertsV2Theme {
            MapFiltersSheet(
                definition = definition,
                catalog = FilterCatalog(
                    raidTiers = listOf("1", "5", "Mega"),
                    rocketTypes = listOf("Fire", "Water"),
                    quests = listOf(
                        FilterCatalogQuest("a", "catch 5", "500 stardust", "Catch 5", "500 Stardust", active = true),
                        FilterCatalogQuest("b", "spin 3", "500 stardust", "Spin 3", "500 Stardust", active = true),
                        FilterCatalogQuest("c", "throw 3", "razz berry", "Throw 3", "Razz Berry", active = true),
                        FilterCatalogQuest("d", "catch 5", "razz berry", "Catch 5", "Razz Berry", active = true)
                    )
                ),
                artwork = emptyMap(),
                rewardThumbnails = emptyMap(),
                visibleCount = 12,
                totalCount = 40,
                categoryCounts = emptyMap(),
                onDefinitionChange = { definition = it },
                onOpenFilterStudio = { studioOpened++ },
                onDismiss = {},
                useSidePanel = true
            )
        }
    }
}
