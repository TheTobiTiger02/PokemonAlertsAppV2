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
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelectionMode
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertEquals
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



}
