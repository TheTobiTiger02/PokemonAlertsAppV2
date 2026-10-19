package com.example.pokemonalertsv2.ui.alerts.mappanel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.MapStylePreference
import com.example.pokemonalertsv2.ui.alerts.MapCameraSnapshot
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The map's two sheets, laid out as side panels so they render in the test window without a
 * bottom sheet's window of their own.
 */
@RunWith(AndroidJUnit4::class)
class MapPanelsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun activeChipRemovesOnlyItsRule() {
        var definition by mutableStateOf(FilterDefinition(maxDistanceMeters = 1_000, raidTiers = FilterSelection.None))
        composeRule.setContent {
            PokemonAlertsV2Theme {
                Filters(definition) { definition = it }
            }
        }

        composeRule.onNodeWithTag("map_active_filters").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remove Raid tiers: none").performClick()

        composeRule.runOnIdle {
            assertEquals(FilterSelection.All, definition.raidTiers)
            assertEquals(1_000, definition.maxDistanceMeters)
        }
    }

    @Test
    fun spacialRendOnlyAppearsWithTheSpawnRadius() {
        var radius by mutableStateOf(false)
        var toggled = 0
        composeRule.setContent {
            PokemonAlertsV2Theme {
                MapLayersSheet(
                    userLocation = null,
                    weatherCells = emptyList(),
                    mapCentre = MapCameraSnapshot(49.87, 8.65, 15.0),
                    mapStyle = MapStylePreference.GOOGLE_STANDARD,
                    onMapStyleChanged = {},
                    showTimeLabels = false,
                    onToggleTimeLabels = { toggled++ },
                    showWeatherCells = true,
                    onToggleWeatherCells = {},
                    showHuntPath = true,
                    onToggleHuntPath = {},
                    showSpawnRadius = radius,
                    onToggleSpawnRadius = { radius = !radius },
                    spacialRendEnabled = false,
                    onToggleSpacialRend = {},
                    showDismissed = false,
                    onToggleDismissed = {},
                    autoEnterPictureInPicture = false,
                    onToggleAutoEnterPictureInPicture = {},
                    onDismiss = {},
                    useSidePanel = true
                )
            }
        }

        composeRule.onNodeWithTag("map_panel_list").performScrollToNode(hasText("Countdown labels"))
        composeRule.onNodeWithText("Countdown labels").performClick()
        composeRule.runOnIdle { assertEquals(1, toggled) }

        composeRule.onNodeWithTag("map_spacial_rend").assertDoesNotExist()
        composeRule.onNodeWithTag("map_panel_list").performScrollToNode(hasText("Spawn radius"))
        composeRule.onNodeWithText("Spawn radius").performClick()
        composeRule.onNodeWithTag("map_spacial_rend").assertExists()
    }

    @androidx.compose.runtime.Composable
    private fun Filters(definition: FilterDefinition, onChange: (FilterDefinition) -> Unit) {
        MapFiltersSheet(
            definition = definition,
            catalog = FilterCatalog(raidTiers = listOf("1", "5", "Mega")),
            artwork = emptyMap(),
            rewardThumbnails = emptyMap(),
            visibleCount = 12,
            totalCount = 40,
            categoryCounts = emptyMap(),
            onDefinitionChange = onChange,
            onOpenFilterStudio = {},
            onDismiss = {},
            useSidePanel = true
        )
    }
}
