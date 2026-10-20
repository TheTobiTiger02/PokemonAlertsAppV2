package com.example.pokemonalertsv2.events

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.ui.components.DesignScreen
import com.example.pokemonalertsv2.ui.components.selectDesignOption
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** The new Events designs put showing and reminding in one sheet. */
class EventsOptionsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val now = Instant.parse("2026-09-17T10:00:00Z").toEpochMilli()
    private val events = listOf(
        GameEvent("raids", "Mega Venusaur in Mega Raids", "raid-battles", startAt = "2026-09-16T04:00:00Z", endAt = "2026-09-22T20:00:00Z"),
        GameEvent("gbl", "Great League", "go-battle-league", startAt = "2026-09-15T20:00:00Z", endAt = "2026-09-22T20:00:00Z"),
    )
    private var settings by mutableStateOf(EventSettings())

    @After
    fun restore() = selectDesignOption(context, DesignScreen.EVENTS, 0)

    private fun show(option: Int) {
        selectDesignOption(context, DesignScreen.EVENTS, option)
        composeRule.setContent {
            PokemonAlertsV2Theme {
                EventsContent(
                    state = EventsUiState(events = events, settings = settings, nowMillis = now),
                    onRefresh = {},
                    onToggleHidden = { type -> settings = settings.copy(hiddenTypes = settings.hiddenTypes.let { if (type in it) it - type else it + type }) },
                    onShowAllTypes = { settings = settings.copy(hiddenTypes = emptySet()) },
                    onToggleStar = {},
                    onToggleReminderType = { type -> settings = settings.copy(reminderTypes = settings.reminderTypes.let { if (type in it) it - type else it + type }) },
                    onLeadMinutes = { settings = settings.copy(leadMinutes = it) },
                    onOpenLink = {},
                )
            }
        }
    }

    @Test
    fun oneSheetShowsAndRemindsPerType() {
        show(1)
        assertEquals(0, composeRule.onAllNodesWithTag("events_reminder_settings").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("events_filter").performClick()
        composeRule.onNodeWithTag("events_settings_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("events_show_go-battle-league").performClick()
        composeRule.onNodeWithTag("events_remind_raid-battles").performClick()
        composeRule.onNodeWithTag("events_lead_60").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertTrue("go-battle-league" in settings.hiddenTypes)
            assertTrue("raid-battles" in settings.reminderTypes)
            assertEquals(60, settings.leadMinutes)
        }
    }

    @Test
    fun tabbedSheetSplitsShowAndRemind() {
        show(2)
        composeRule.onNodeWithTag("events_filter").performClick()
        composeRule.onNodeWithTag("events_show_raid-battles").performClick()
        composeRule.onNodeWithTag("events_settings_tab_remind").performClick()
        composeRule.onNodeWithTag("events_remind_go-battle-league").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertTrue("raid-battles" in settings.hiddenTypes)
            assertTrue("go-battle-league" in settings.reminderTypes)
        }
    }
}
