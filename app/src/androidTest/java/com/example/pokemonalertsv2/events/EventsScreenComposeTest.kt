package com.example.pokemonalertsv2.events

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class EventsScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-09-17T10:00:00Z").toEpochMilli()
    private val events = listOf(
        GameEvent("raids", "Mega Venusaur in Mega Raids", "raid-battles", startAt = "2026-09-16T04:00:00Z", endAt = "2026-09-22T20:00:00Z",
            raidBosses = listOf(EventPokemon("Mega Venusaur", pokemonId = 3))),
        GameEvent("spotlight", "Rattata Spotlight Hour", "pokemon-spotlight-hour", heading = "Pokémon Spotlight Hour",
            link = "https://leekduck.com/events/spot/", startAt = "2026-09-17T16:00:00Z", endAt = "2026-09-17T17:00:00Z", localTime = true,
            featured = listOf(EventPokemon("Rattata", canBeShiny = true, pokemonId = 19)),
            bonuses = listOf(EventBonus("2× Catch Stardust"))),
        GameEvent("gbl", "Great League", "go-battle-league", startAt = "2026-09-15T20:00:00Z", endAt = "2026-09-22T20:00:00Z"),
    )

    @Test
    fun runningAndUpcomingShowHiddenTypesToggleAndDetailOpens() {
        var settings by mutableStateOf(EventSettings())
        var opened: String? = null
        var starred: String? = null
        composeRule.setContent {
            PokemonAlertsV2Theme {
                EventsContent(
                    state = EventsUiState(events = events, settings = settings, nowMillis = now),
                    onRefresh = {},
                    onToggleHidden = { type -> settings = settings.copy(hiddenTypes = settings.hiddenTypes.let { if (type in it) it - type else it + type }) },
                    onToggleStar = { starred = it },
                    onToggleReminderType = {},
                    onLeadMinutes = {},
                    onOpenLink = { opened = it },
                )
            }
        }
        composeRule.onNodeWithText("Happening now").assertIsDisplayed()
        composeRule.onNodeWithTag("event_raids").assertIsDisplayed()
        composeRule.onNodeWithText("Today").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("event_gbl").fetchSemanticsNodes().size)

        composeRule.onNodeWithTag("events_type_go-battle-league").performClick()
        composeRule.onNodeWithTag("event_gbl").assertIsDisplayed()

        composeRule.onNodeWithTag("event_star_spotlight").performClick()
        assertEquals("spotlight", starred)

        composeRule.onNodeWithTag("event_spotlight").performClick()
        composeRule.onNodeWithTag("event_detail").assertIsDisplayed()
        composeRule.onNodeWithText("2× Catch Stardust").assertIsDisplayed()
        composeRule.onNodeWithText("You'll be reminded 15 min before").assertIsDisplayed()
        composeRule.onNodeWithTag("event_open_link").performClick()
        assertEquals("https://leekduck.com/events/spot/", opened)
    }
}
