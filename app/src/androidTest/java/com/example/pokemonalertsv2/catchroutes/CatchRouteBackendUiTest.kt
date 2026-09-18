package com.example.pokemonalertsv2.catchroutes

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CatchRouteBackendUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun automaticAndEventEvidence() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = CatchRouteController.get(context)
        controller.stop()
        ActivityScenario.launch(CatchRoutesActivity::class.java).use { activity ->
            activity.onActivity { assertEquals(CatchPrediction.AUTOMATIC, it.model.settings.prediction) }
            compose.onNodeWithText("Advanced settings").performScrollTo().performClick()
            compose.onNodeWithText("Automatic (learned timing)").performScrollTo().assertIsDisplayed()
            val now = System.currentTimeMillis()
            val p = CatchPoint(49.7408, 8.6201)
            val observed = SpawnOpportunity("event", "event-point", p, now - 1000, now + 600000, "observed_encounter",
                requiresLiveConfirmation = true, activityPattern = "likely_event")
            val inferred = observed.copy(id = "learned", pointId = "learned-point", basis = "inferred_lifetime", requiresLiveConfirmation = false, activityPattern = "unknown")
            val plan = CatchItinerary(CatchRouteSettings(start = p, startAtMillis = now + 60000),
                listOf(CatchPathPosition(p, 0.0)), listOf(CatchEncounter(observed, now + 60000, 0.0), CatchEncounter(inferred, now + 60000, 0.0)), listOf(p))
            try {
                controller.begin(plan)
                compose.onNodeWithTag("catch_route_form").performScrollToNode(hasTestTag("catch_stop_0"))
                compose.onNodeWithTag("catch_stop_0").performClick()
                compose.onNodeWithText("Likely event spawnpoint", substring = true).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Requires live confirmation", substring = true).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Estimated 60-minute lifetime", substring = true).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Close").performClick()
                controller.pause().join()
                compose.onNodeWithText("Timing needs refreshing before visits resume.").performScrollTo().assertIsDisplayed()
                assertTrue(controller.session.value!!.remaining.isEmpty())
                assertEquals(plan.path, controller.session.value!!.itinerary.path)
            } finally { controller.stop() }
        }
    }
}
