package com.example.pokemonalertsv2.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.insights.SpawnActivity
import com.example.pokemonalertsv2.data.insights.SpawnActivityDay
import com.example.pokemonalertsv2.data.insights.SpawnActivityDaysResponse
import com.example.pokemonalertsv2.data.insights.SpawnDayTotal
import com.example.pokemonalertsv2.data.insights.SpawnSpeciesCount
import com.example.pokemonalertsv2.data.insights.SpawnSpeciesResponse
import com.example.pokemonalertsv2.data.insights.SpawnSpeciesTotals
import com.example.pokemonalertsv2.events.EventSettings
import com.example.pokemonalertsv2.events.EventsContent
import com.example.pokemonalertsv2.events.EventsUiState
import com.example.pokemonalertsv2.events.GameEvent
import com.example.pokemonalertsv2.ui.components.DesignScreen
import com.example.pokemonalertsv2.ui.components.selectDesignOption
import com.example.pokemonalertsv2.ui.history.InsightsTab
import com.example.pokemonalertsv2.ui.history.SpawnActivityUiState
import com.example.pokemonalertsv2.ui.history.SpawnInsightsScreen
import com.example.pokemonalertsv2.ui.history.SpawnInsightsUiState
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Saves the Filter Studio, Events and Insights designs at the default phone size and at a 348dp
 * phone with font scale 1.3 to the app cache under design-shots/, for comparing them without a
 * free emulator to drive by hand.
 */
class StudioEventsInsightsCaptures {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var narrow by mutableStateOf(false)
    private var screen by mutableIntStateOf(0)
    private var draft by mutableStateOf(FilterDefinition())

    private val days = listOf("2026-10-14", "2026-10-15", "2026-10-16", "2026-10-17", "2026-10-18", "2026-10-19", "2026-10-20")
    private val activity = SpawnActivity(
        days = SpawnActivityDaysResponse(days = days, areas = listOf("Alsbach", "Darmstadt"),
            summary = days.mapIndexed { i, d -> SpawnActivityDay(d, active = 900 + i * 40) }),
        species = SpawnSpeciesResponse(
            days = days,
            perDay = days.mapIndexed { i, d -> SpawnDayTotal(d, total = 3000 + i * 150) },
            totals = SpawnSpeciesTotals(sightings = 24500, species = 132),
            species = listOf(
                SpawnSpeciesCount(16, count = 2400, name = "Pidgey", rank = 1),
                SpawnSpeciesCount(19, count = 1900, name = "Rattata", rank = 2),
                SpawnSpeciesCount(10, count = 1200, name = "Caterpie", rank = 3)
            )
        )
    )
    private val events = listOf(
        GameEvent("raids", "Dialga in 5-star Raid Battles", "raid-battles", startAt = "2026-10-14T04:00:00Z", endAt = "2026-10-22T20:00:00Z"),
        GameEvent("gbl", "Great League", "go-battle-league", startAt = "2026-10-13T20:00:00Z", endAt = "2026-10-21T20:00:00Z"),
        GameEvent("cd", "Community Day", "community-day", startAt = "2026-10-25T13:00:00Z", endAt = "2026-10-25T16:00:00Z"),
    )

    @Composable
    private fun Studio(option: Int) {
        val body = StudioBodyState(
            draft = draft, onDraftChange = { draft = it },
            catalog = FilterCatalog(raidTiers = listOf("1", "3", "5", "Mega", "Shadow 5"), rocketTypes = listOf("Fire", "Water", "Grass")),
            artwork = emptyMap(), rewardThumbnails = emptyMap(), matchCount = 1350, totalCount = 5600,
            areas = listOf("Alsbach", "Darmstadt", "Darmstadt-North"), profileSummary = "Custom rules",
            openProfiles = {}, saveProfile = {}, openQuestRules = {}, openDistanceOverrides = {}
        )
        when (option) {
            1 -> StudioTabsBody(body)
            2 -> StudioOverviewBody(body)
            else -> StudioTilesBody(body)
        }
    }

    @Test
    fun captureAll() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = if (narrow) 1.3f else 1f)) {
                PokemonAlertsV2Theme {
                    Box(
                        Modifier.width(if (narrow) 348.dp else 412.dp).height(if (narrow) 775.dp else 840.dp)
                            .background(MaterialTheme.colorScheme.background).testTag(FRAME)
                    ) {
                        when (screen) {
                            1, 2, 3 -> Studio(screen)
                            4, 5 -> SpawnInsightsScreen(
                                state = SpawnInsightsUiState(tab = InsightsTab.SPAWNS, spawns = SpawnActivityUiState(activity = activity)),
                                onQueryChange = {}, onRangeChange = {}, onRun = {}
                            )
                            6, 7 -> EventsContent(
                                state = EventsUiState(events = events, settings = EventSettings(), nowMillis = System.currentTimeMillis()),
                                onRefresh = {}, onToggleHidden = {}, onShowAllTypes = {}, onToggleStar = {},
                                onToggleReminderType = {}, onLeadMinutes = {}, onOpenLink = {}
                            )
                        }
                    }
                }
            }
        }
        for (isNarrow in listOf(false, true)) {
            val size = if (isNarrow) "348" else "def"
            narrow = isNarrow
            listOf(1 to "studio_A_tabs", 2 to "studio_B_overview", 3 to "studio_C_tiles").forEach { (s, name) ->
                screen = s; shot("${name}_$size")
            }
            selectDesignOption(context, DesignScreen.INSIGHTS, 1)
            screen = 4; shot("insights_A_spawnpoints_$size")
            composeRule.onNodeWithTag("insights_sub_species").performClick(); shot("insights_A_species_$size")
            selectDesignOption(context, DesignScreen.INSIGHTS, 2)
            screen = 0; composeRule.waitForIdle(); screen = 5; shot("insights_B_cards_$size")
            composeRule.onNodeWithTag("insights_card_species").performClick(); shot("insights_B_species_page_$size")
            listOf(1 to "events_A_one_sheet", 2 to "events_B_tabs").forEach { (option, name) ->
                selectDesignOption(context, DesignScreen.EVENTS, option)
                screen = 0; composeRule.waitForIdle(); screen = 6
                composeRule.onNodeWithTag("events_filter").performClick()
                sheetShot("${name}_$size")
                if (option == 2) {
                    composeRule.onNodeWithTag("events_settings_tab_remind").performClick()
                    sheetShot("${name}_remind_$size")
                }
                androidx.test.espresso.Espresso.pressBack()
            }
        }
        selectDesignOption(context, DesignScreen.INSIGHTS, 0)
        selectDesignOption(context, DesignScreen.EVENTS, 0)
    }

    private fun shot(name: String) {
        composeRule.waitForIdle()
        save(name, composeRule.onNodeWithTag(FRAME).captureToImage().asAndroidBitmap())
    }

    /** A bottom sheet draws in its own window, so it is the last root rather than the frame. */
    private fun sheetShot(name: String) {
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("events_settings_sheet").fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(2500)
        composeRule.waitForIdle()
        save(name, composeRule.onNodeWithTag("events_settings_sheet").captureToImage().asAndroidBitmap())
    }

    private fun save(name: String, bitmap: Bitmap) {
        val dir = File(context.cacheDir, "design-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val FRAME = "capture_frame"
    }
}
