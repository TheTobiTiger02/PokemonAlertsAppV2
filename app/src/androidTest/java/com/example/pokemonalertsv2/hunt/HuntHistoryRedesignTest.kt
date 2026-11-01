package com.example.pokemonalertsv2.hunt

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.health.WatchStats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A finished hunt with a walked path, a shiny, a hundo and watch data shows up in the
 * redesigned history, and its summary opens with the map and the watch section.
 */
@RunWith(AndroidJUnit4::class)
class HuntHistoryRedesignTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = HuntRepository.getInstance(context)

    @After fun tearDown() = runBlocking { repository.clearHistory() }

    @Test fun finishedHuntShowsPathCatchesAndWatchStats() {
        val start = System.currentTimeMillis() - 45 * 60_000L
        val summary = runBlocking {
            repository.clearHistory()
            repository.start("Hundo hunt", FilterDefinition(), nowMillis = start)
            // A short loop through a park, one fix every ~15 m.
            for (i in 0..40) {
                val angle = i / 40.0 * 2 * Math.PI
                repository.recordFix(49.7600 + 0.0012 * Math.sin(angle), 8.6100 + 0.0018 * Math.cos(angle), 5f)
            }
            repository.recordCatch(HuntCatch("a", "Dratini", null, start + 10 * 60_000L, pokedexId = 147, shiny = true, iv = 87, cp = 512,
                latitude = 49.7612, longitude = 8.6100))
            repository.recordCatch(HuntCatch("b", "Larvitar", null, start + 25 * 60_000L, pokedexId = 246, iv = 100, cp = 701,
                latitude = 49.7600, longitude = 8.6118))
            repository.recordCatch(HuntCatch("c", "Gible", null, start + 40 * 60_000L, pokedexId = 443, iv = 76, cp = 455))
            repository.finish(nowMillis = start + 45 * 60_000L)?.also { finished ->
                // What Health Connect would hand back for the walk.
                val watch = WatchStats.fromSamples(
                    finished.startedAtMillis, finished.endedAtMillis,
                    heartRates = (0..20).map { minute -> start + minute * 60_000L to 95 + minute },
                    steps = 2200, calories = 90.0, deviceName = "Galaxy Watch6",
                    fetchedAtMillis = System.currentTimeMillis()
                )!!
                repository.updateWatchStats(finished.startedAtMillis, watch)
            }
        }
        requireNotNull(summary)
        assertTrue(summary.trackPoints.size >= 20)
        assertEquals(3, summary.catches.size)
        assertEquals(2200L, runBlocking { repository.history.first() }.first().watch?.steps)

        ActivityScenario.launch(HuntHistoryActivity::class.java).use {
            composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("hunt_history_row").fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNodeWithText("Hundo hunt").assertIsDisplayed()
            composeRule.onNodeWithText("3 caught").assertIsDisplayed()
            screenshot("hunt_history")
            composeRule.onAllNodesWithTag("hunt_history_row").onFirst().performClick()
            composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("hunt_summary").fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNodeWithTag("hunt_summary_map").assertIsDisplayed()
            composeRule.onNodeWithText("Best IV").assertIsDisplayed()
            // Give the map a moment to draw before the picture; tiles may be missing offline.
            Thread.sleep(3_000)
            screenshot("hunt_summary")
        }
    }

    /** Saves what is on screen to the app's files, for pulling with adb when checking by eye. */
    private fun screenshot(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
