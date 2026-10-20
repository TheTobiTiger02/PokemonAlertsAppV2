package com.example.pokemonalertsv2.catchroutes

import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Each of the planner's newer designs edits a setting, and the edit reaches the view model. */
@RunWith(AndroidJUnit4::class)
class CatchRoutePlannerDesignsTest {
    @get:Rule val rule = createAndroidComposeRule<CatchRoutesActivity>()

    @Before
    fun setUp() {
        runBlocking { CatchRouteController.get(ApplicationProvider.getApplicationContext()).stop() }
    }


    @Test
    fun sheetWhenPageSetsTheDuration() {
        rule.onNodeWithTag("plan_sheet_summary").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("plan_row_when").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("duration_90").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(90, rule.activity.model.settings.durationMinutes)
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
