package com.example.pokemonalertsv2.ui.alerts.mapchrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pokemonalertsv2.hunt.HuntMapFocus
import com.example.pokemonalertsv2.ui.alerts.SyncStatus
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The map's controls: every one is visible, labelled and fires the action it is tagged for. */
@RunWith(AndroidJUnit4::class)
class MapChromeComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val state = MapChromeState(
        visibleCount = 12,
        totalCount = 40,
        categoryCounts = emptyMap(),
        mutedCategories = emptySet(),
        syncStatus = SyncStatus.Live(null),
        advancedFilterRuleCount = 2,
        hunt = null,
        locate = MapLocateState.IDLE,
        mapLoaded = true,
        alertSelected = false,
        useSidePanel = false,
        showBackButton = false
    )

    @Test
    fun everyControlFiresItsAction() {
        val fired = mutableListOf<String>()
        composeRule.setContent { Chrome(state, fired) }

        listOf("map_open_filters", "map_open_layers", "open_settings", "open_map_tools", "map_locate")
            .forEach { tag -> composeRule.onNodeWithTag(tag).assertIsDisplayed().performClick() }
        composeRule.onNodeWithTag("map_filter_rail").assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals(listOf("filters", "layers", "settings", "tools", "locate"), fired)
        }
    }

    @Test
    fun topBarCountsAlertsAndHiddenFilterRules() {
        composeRule.setContent { Chrome(state, mutableListOf()) }

        composeRule.onNodeWithText("12 of 40 on map").assertIsDisplayed()
        // The badge counts rules the chip rail cannot show.
        composeRule.onNodeWithText("2").assertIsDisplayed()
    }

    @Test
    fun offlineStatusRetriesFromTheBar() {
        val fired = mutableListOf<String>()
        composeRule.setContent { Chrome(state.copy(syncStatus = SyncStatus.Cached(null)), fired) }

        composeRule.onNodeWithText("Offline · tap to retry").performClick()

        composeRule.runOnIdle { assertEquals(listOf("refresh"), fired) }
    }

    @Test
    fun toolsButtonNamesTheHuntTarget() {
        val hunting = state.copy(
            hunt = MapChromeHunt(name = "Kecleon", target = null, distanceMeters = null, focus = HuntMapFocus.READY)
        )
        composeRule.setContent { Chrome(hunting, mutableListOf()) }

        composeRule.onNodeWithText("Hunting Kecleon").assertIsDisplayed()
        composeRule.onNodeWithText("Tools").assertDoesNotExist()
    }

    @Test
    fun floatingControlsMakeWayForAnAlertSheet() {
        composeRule.setContent { Chrome(state.copy(alertSelected = true), mutableListOf()) }

        composeRule.onNodeWithTag("map_top_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("open_map_tools").assertDoesNotExist()
        composeRule.onNodeWithTag("map_locate").assertDoesNotExist()
    }

    @Composable
    private fun Chrome(state: MapChromeState, fired: MutableList<String>) {
        PokemonAlertsV2Theme {
            Box(modifier = Modifier.fillMaxSize()) {
                MapChrome(
                    state = state,
                    actions = MapChromeActions(
                        onCategoriesChange = {},
                        onBack = {},
                        onOpenSettings = { fired += "settings" },
                        onOpenFilters = { fired += "filters" },
                        onOpenLayers = { fired += "layers" },
                        onOpenTools = { fired += "tools" },
                        onFit = { fired += "fit" },
                        onLocate = { fired += "locate" },
                        onRefresh = { fired += "refresh" }
                    )
                )
            }
        }
    }
}
