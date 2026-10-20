package com.example.pokemonalertsv2.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.FilterSelectionMode
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Each Filter Studio design edits the draft it is given. */
@RunWith(AndroidJUnit4::class)
class FilterStudioOptionsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var draft by mutableStateOf(FilterDefinition())
    private var profilesOpened = 0

    @Composable
    private fun Body(content: @Composable (StudioBodyState) -> Unit) {
        PokemonAlertsV2Theme {
            content(
                StudioBodyState(
                    draft = draft,
                    onDraftChange = { draft = it },
                    catalog = FilterCatalog(raidTiers = listOf("1", "5", "Mega"), rocketTypes = listOf("Fire", "Water")),
                    artwork = emptyMap(),
                    rewardThumbnails = emptyMap(),
                    matchCount = 12,
                    totalCount = 40,
                    areas = listOf("Alsbach", "Darmstadt"),
                    profileSummary = "Custom rules",
                    openProfiles = { profilesOpened++ },
                    saveProfile = {},
                    openQuestRules = {},
                    openDistanceOverrides = {}
                )
            )
        }
    }

    @Test
    fun tabsPickAnAreaInTheNearbyTab() {
        composeRule.setContent { Body { StudioTabsBody(it) } }
        composeRule.onNodeWithTag("map_filter_tabs").assertIsDisplayed()
        composeRule.onNodeWithText("Alsbach").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(FilterSelectionMode.ONLY, draft.areas.mode)
            assertEquals(1, draft.areas.selectedCount)
            org.junit.Assert.assertTrue(draft.areas.contains("Alsbach"))
        }
    }

    @Test
    fun overviewOpensATypePageWhoseSwitchHidesTheType() {
        composeRule.setContent { Body { StudioOverviewBody(it) } }
        composeRule.onNodeWithTag("studio_row_raid").performScrollTo().performClick()
        composeRule.onNodeWithTag("type_editor_raids").assertIsDisplayed()
        composeRule.onNodeWithTag("type_switch_raids").performClick()
        composeRule.runOnIdle { assertFalse(draft.alertTypes.contains(FilterAlertType.RAID.name)) }
        composeRule.onNodeWithTag("token_tile_5").performClick()
        composeRule.runOnIdle { assertEquals(FilterSelection.only(listOf("1", "Mega")), draft.raidTiers) }
    }

    @Test
    fun overviewSwitchesTypesWithoutRules() {
        composeRule.setContent { Body { StudioOverviewBody(it) } }
        composeRule.onNodeWithTag("studio_switch_kecleon").performScrollTo().performClick()
        composeRule.runOnIdle { assertFalse(draft.alertTypes.contains(FilterAlertType.KECLEON.name)) }
        composeRule.onNodeWithTag("studio_profiles").performClick()
        composeRule.runOnIdle { assertEquals(1, profilesOpened) }
    }

    @Test
    fun tilesToggleOnTapAndOpenRulesFromTheTuneButton() {
        composeRule.setContent { Body { StudioTilesBody(it) } }
        composeRule.onNodeWithTag("studio_tile_rocket").performScrollTo().performClick()
        composeRule.runOnIdle { assertFalse(draft.alertTypes.contains(FilterAlertType.ROCKET.name)) }
        composeRule.onNodeWithTag("studio_tune_rocket").performClick()
        composeRule.onNodeWithTag("type_editor_rocket").assertIsDisplayed()
        composeRule.onNodeWithTag("token_tile_fire").performClick()
        composeRule.runOnIdle { assertEquals(FilterSelection.only(listOf("Water")), draft.rocketTypes) }
    }
}
