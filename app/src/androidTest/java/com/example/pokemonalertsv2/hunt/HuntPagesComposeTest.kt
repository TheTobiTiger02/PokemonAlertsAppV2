package com.example.pokemonalertsv2.hunt

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.ui.components.DesignScreen
import com.example.pokemonalertsv2.ui.components.selectDesignOption
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The paged hunt picker: pick a type, narrow it on its page, start. */
@RunWith(AndroidJUnit4::class)
class HuntPagesComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun pickPagedDesign() = selectDesignOption(context, DesignScreen.FILTER_STUDIO, 2)

    @After
    fun restoreCurrentDesign() = selectDesignOption(context, DesignScreen.FILTER_STUDIO, 0)

    @Test
    fun aRaidHuntNarrowsTiersOnItsOwnPage() {
        var started: FilterDefinition? = null
        composeRule.setContent {
            PokemonAlertsV2Theme {
                HuntTargetSheet(
                    catalog = FilterCatalog(raidTiers = listOf("1", "5", "Mega")),
                    artwork = emptyMap(),
                    questRewardThumbnails = emptyMap(),
                    categoryCounts = emptyMap(),
                    onDismiss = {},
                    onStart = { _, definition, _, _ -> started = definition }
                )
            }
        }
        composeRule.onNodeWithTag("hunt_start").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Raids filter").performScrollTo().performClick()
        composeRule.onNodeWithTag("hunt_row_raid").performScrollTo().performClick()
        composeRule.onNodeWithTag("type_editor_raids").assertIsDisplayed()
        composeRule.onNodeWithTag("token_tile_5").performClick()
        composeRule.onNodeWithTag("hunt_start").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(FilterSelection.only(listOf("5")), started?.raidTiers) }
    }
}
