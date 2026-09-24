package com.example.pokemonalertsv2.events

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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

    private val page = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<EventPage>("""{"sections":[
        {"key":"about","title":"About","blocks":[{"type":"text","text":"Rattata appears more often."}]},
        {"key":"spawns","title":"Spawns","blocks":[{"type":"pokemon","items":[{"name":"Rattata","shiny":true,"type":"normal","pokemonId":19}]}]},
        {"key":"research","title":"Research","blocks":[
          {"type":"research","tasks":[{"task":"Catch 10 Pokémon","rewards":[{"name":"Fidough","type":"fairy","pokemonId":926,"minCp":389,"maxCp":422}]}]},
          {"type":"specialResearch","steps":[{"number":1,"name":"Rattata Hour","tasks":[{"task":"Make 7 Nice Throws","rewards":[{"name":"Ultra Ball","quantity":20}]}]}]}]},
        {"key":"sales","title":"Sales","blocks":[{"type":"text","text":"Web store box."}]}]}""")

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
                    loadPage = { id -> if (id == "spotlight") page else null },
                )
            }
        }
        composeRule.onNodeWithText("Happening now").assertIsDisplayed()
        composeRule.onNodeWithTag("event_raids").assertIsDisplayed()
        composeRule.onNodeWithText("Today").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("event_gbl").fetchSemanticsNodes().size)

        composeRule.onNodeWithTag("events_filter_button").performClick()
        composeRule.onNodeWithTag("events_type_go-battle-league").performClick()
        composeRule.onNodeWithText("Show events").performClick()
        composeRule.onNodeWithTag("event_gbl").assertIsDisplayed()

        composeRule.onNodeWithTag("event_star_spotlight").performClick()
        assertEquals("spotlight", starred)

        composeRule.onNodeWithTag("event_spotlight").performClick()
        composeRule.onNodeWithTag("event_detail").assertIsDisplayed()
        composeRule.onNodeWithText("You'll be reminded 15 min before").assertIsDisplayed()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("event_section_about").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("event_section_about").performClick()
        composeRule.onNodeWithText("Rattata appears more often.").assertIsDisplayed()
        composeRule.onNodeWithTag("event_chip_research").performClick()
        composeRule.onNodeWithText("CP 389–422").assertIsDisplayed()
        composeRule.onNodeWithTag("event_step_1").performClick()
        composeRule.onNodeWithTag("event_detail").performTouchInput { swipeUp() }
        composeRule.onNodeWithText("×20").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Web store box.").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("event_open_link").performClick()
        assertEquals("https://leekduck.com/events/spot/", opened)
    }
}
