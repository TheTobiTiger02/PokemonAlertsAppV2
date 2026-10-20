package com.example.pokemonalertsv2.catchroutes

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Each of the planner's newer designs edits a setting, and the edit reaches the view model. */
@RunWith(AndroidJUnit4::class)
class CatchRoutePlannerDesignsTest {
    @get:Rule val rule = createAndroidComposeRule<CatchRoutesActivity>()

    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("design_compare", Context.MODE_PRIVATE)
    private var previous = 0

    @Before
    fun setUp() {
        previous = prefs.getInt("CATCH_ROUTES", 0)
        runBlocking { CatchRouteController.get(ApplicationProvider.getApplicationContext()).stop() }
    }

    /** Picked back through the strip too: the choice is also held in memory for the rest of the test run. */
    @After
    fun tearDown() {
        choose(previous)
        prefs.edit().putInt("CATCH_ROUTES", previous).commit()
    }

    private fun choose(option: Int) {
        rule.onNodeWithTag("design_compare_catch_routes").performScrollToIndex(option)
        rule.onNodeWithTag("design_option_catch_routes_$option").performClick()
        rule.waitForIdle()
    }

    @Test
    fun rowsFinishPageSetsTheFinish() {
        choose(1)
        rule.onNodeWithTag("plan_row_finish").performScrollTo().performClick()
        rule.onNodeWithTag("finish_ANYWHERE").performClick()
        rule.waitForIdle()
        assertEquals(CatchFinish.ANYWHERE, rule.activity.model.settings.finish)
    }

    @Test
    fun sheetWhenPageSetsTheDuration() {
        choose(2)
        rule.onNodeWithTag("plan_sheet_summary").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("plan_row_when").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("duration_90").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(90, rule.activity.model.settings.durationMinutes)
    }

    @Test
    fun stepsWhatStepTurnsOnSpacialRend() {
        choose(3)
        assertTrue(!rule.activity.model.settings.spacialRend)
        rule.onNodeWithTag("step_next").performClick()
        rule.onNodeWithTag("step_next").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("include_spacial_rend").performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(rule.activity.model.settings.spacialRend)
    }
}

/**
 * Opt-in: holds the planner open so adb can drive and screenshot it (it is not exported, so adb
 * cannot open it). Pass `-e holdCatchPlanner <seconds>`. Never runs in a normal test pass. No
 * compose rule here: its test clock would stop frames while the test sleeps.
 */
@RunWith(AndroidJUnit4::class)
class CatchRoutePlannerHoldFixture {
    @Test
    fun holdPlannerOpen() {
        val seconds = InstrumentationRegistry.getArguments().getString("holdCatchPlanner")?.toLong()
        assumeTrue(seconds != null)
        ActivityScenario.launch(CatchRoutesActivity::class.java).use { SystemClock.sleep(seconds!! * 1000) }
    }
}
