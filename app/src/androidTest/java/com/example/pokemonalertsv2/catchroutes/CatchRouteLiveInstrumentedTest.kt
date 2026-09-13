package com.example.pokemonalertsv2.catchroutes

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicit live acceptance suite. Not part of deterministic unit tests. */
@RunWith(AndroidJUnit4::class)
class CatchRouteLiveInstrumentedTest {
    @Test fun generateAndPreviewAlsbach() = preview("Alsbach", CatchPoint(49.7408, 8.6201))
    @Test fun generateAndPreviewDarmstadt() = preview("Darmstadt", CatchPoint(49.8728, 8.6512))
    private fun preview(city: String, point: CatchPoint) {
        // Matrix and path share one client quota. Separate full generations by a limiter window.
        while (System.currentTimeMillis() < nextRouteAfter) Thread.sleep(250)
        nextRouteAfter = System.currentTimeMillis() + 65_000
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking { CatchRouteController.get(context).stop() }
        ActivityScenario.launch(CatchRoutesActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.model.startPoint(point)
                activity.model.edit(activity.model.settings.copy(name = "$city live route", durationMinutes = 20, finish = CatchFinish.ROUND_TRIP))
                activity.model.generate()
            }
            var busy = true
            val deadline = System.currentTimeMillis() + 45_000
            while (busy && System.currentTimeMillis() < deadline) {
                Thread.sleep(250)
                scenario.onActivity { busy = it.model.busy }
            }
            var plan: CatchItinerary? = null
            var error: String? = null
            scenario.onActivity { plan = it.model.itinerary; error = it.model.error }
            assertFalse("Generation exceeded deadline", busy)
            assertNotNull("$city failed: $error", plan)
            val route = plan!!
            assertTrue(route.encounters.isNotEmpty())
            assertTrue(route.path.size > 2)
            assertTrue(route.finishAtMillis <= route.settings.endAtMillis)
            assertTrue(catchDistance(route.path.last().point, point) <= 25)
            assertEquals(route.encounters.size, route.encounters.map { it.opportunity.id }.distinct().size)
            assertTrue(route.encounters.all { it.arrivalMillis >= it.opportunity.availableFrom && it.arrivalMillis < it.opportunity.despawnAt })
            Log.i("CatchRouteLive", "$city: ${route.encounters.size} opportunities, ${route.distanceMeters.toInt()} m, ${route.path.size} vertices, warnings=${route.warnings}")
            instrumentation.waitForIdleSync()
            Thread.sleep(1500)
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            File(context.getExternalFilesDir(null), "catch-route-$city.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(context.getExternalFilesDir(null), "catch-route-$city.txt").writeText("${route.encounters.size} opportunities\n${route.distanceMeters} meters\n${route.warnings.joinToString("\n")}")
        }
    }
    companion object { private var nextRouteAfter = 0L }
}
