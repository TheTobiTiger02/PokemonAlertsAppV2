package com.example.pokemonalertsv2.ui.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.pokemonalertsv2.data.insights.SpawnActivity
import com.example.pokemonalertsv2.data.insights.SpawnActivityDay
import com.example.pokemonalertsv2.data.insights.SpawnActivityDaysResponse
import com.example.pokemonalertsv2.data.insights.SpawnDayTotal
import com.example.pokemonalertsv2.data.insights.SpawnSpeciesCount
import com.example.pokemonalertsv2.data.insights.SpawnSpeciesResponse
import com.example.pokemonalertsv2.data.insights.SpawnSpeciesTotals
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The new Spawns layouts: days picked on the bars, and summary cards that open pages. */
class InsightsOptionsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val days = listOf("2026-09-15", "2026-09-16", "2026-09-17")
    private val activity = SpawnActivity(
        days = SpawnActivityDaysResponse(days = days, summary = days.map { SpawnActivityDay(it, active = 100) }),
        species = SpawnSpeciesResponse(
            days = days,
            perDay = days.map { SpawnDayTotal(it, total = 40) },
            totals = SpawnSpeciesTotals(sightings = 120, species = 2),
            species = listOf(SpawnSpeciesCount(16, count = 80, name = "Pidgey", rank = 1), SpawnSpeciesCount(19, count = 40, name = "Rattata", rank = 2))
        )
    )

    private fun show(onDay: (String?) -> Unit = {}) {
        composeRule.setContent {
            PokemonAlertsV2Theme {
                SpawnInsightsScreen(
                    state = SpawnInsightsUiState(tab = InsightsTab.SPAWNS, spawns = SpawnActivityUiState(activity = activity)),
                    onQueryChange = {},
                    onRangeChange = {},
                    onRun = {},
                    onSpawnDayChange = onDay
                )
            }
        }
    }

    @Test
    fun tabsPickADayOnTheBars() {
        var day: String? = "unset"
        show { day = it }
        composeRule.onNodeWithTag("insights_sub_species").performClick()
        composeRule.onNodeWithTag("insights_bar_1").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("2026-09-16", day) }
    }

}
