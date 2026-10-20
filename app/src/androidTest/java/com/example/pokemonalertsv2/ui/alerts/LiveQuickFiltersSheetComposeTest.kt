package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Live quick filters edit the feed definition in place and hand the rest to Filter Studio. */
@RunWith(AndroidJUnit4::class)
class LiveQuickFiltersSheetComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var definition by mutableStateOf(FilterDefinition())
    private var showDismissed by mutableStateOf(false)
    private var studioOpened = 0

    private fun show() {
        composeRule.setContent {
            PokemonAlertsV2Theme {
                LiveQuickFiltersSheet(
                    definition = definition,
                    catalog = FilterCatalog(areas = listOf("Alsbach", "Darmstadt")),
                    categoryCounts = mapOf(AlertCategory.RAID to 3),
                    visibleCount = 10,
                    totalCount = 20,
                    showDismissed = showDismissed,
                    onShowDismissedChange = { showDismissed = it },
                    onDefinitionChange = { definition = it },
                    onOpenFilterStudio = { studioOpened++ },
                    onDismiss = {}
                )
            }
        }
    }

    @Test
    fun typesAreasAndDismissedEditInPlace() {
        show()
        composeRule.onNodeWithContentDescription("Raids filter").performClick()
        composeRule.runOnIdle { assertFalse(definition.alertTypes.contains(FilterAlertType.RAID.name)) }
        composeRule.onNodeWithText("Alsbach").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(1, definition.areas.selectedCount)
            assertTrue(definition.areas.contains("Alsbach"))
        }
        composeRule.onNodeWithTag("live_filters_dismissed").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(showDismissed) }
        composeRule.onNodeWithTag("live_filters_clear").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(FilterDefinition(), definition)
            assertFalse(showDismissed)
        }
    }

    @Test
    fun moreFiltersOpensFilterStudio() {
        show()
        composeRule.onNodeWithTag("live_filters_studio").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, studioOpened) }
    }
}
