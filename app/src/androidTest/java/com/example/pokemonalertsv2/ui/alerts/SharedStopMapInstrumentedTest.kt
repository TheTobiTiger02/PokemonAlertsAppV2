package com.example.pokemonalertsv2.ui.alerts

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.MapStylePreference
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SharedStopMapInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun googleRocketWithQuest() = exercise(MapStylePreference.GOOGLE_STANDARD, "Rocket")
    @Test fun osmKecleonWithQuest() = exercise(MapStylePreference.OPENSTREETMAP, "Kecleon")
    @Test fun googleKecleonWithQuest() = exercise(MapStylePreference.GOOGLE_STANDARD, "Kecleon")
    @Test fun osmRocketWithQuest() = exercise(MapStylePreference.OPENSTREETMAP, "Rocket")

    private fun exercise(style: MapStylePreference, kind: String) {
        val primary = PokemonAlert(id = 7_000_001, name = kind, type = listOf(kind),
            pokemon = if (kind == "Kecleon") kind else null, latitude = 49.87, longitude = 8.65,
            endTime = "2099-01-01T00:00:00Z", pokestop = "Shared test stop")
        val quest = primary.copy(id = 7_000_000, name = "Quest", pokemon = null, type = listOf("Quest"),
            questTask = "Catch 5 Pokemon", questReward = "500 Stardust")
        rule.setContent { PokemonAlertsV2Theme {
            AlertsMapScreenContent(alerts = listOf(quest, primary), onBack = {}, onRefresh = {},
                initialMapStyle = style, initialZoom = 16.0, showWeatherCells = false)
        } }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var google: com.google.android.gms.maps.GoogleMap? = null
        var osm: org.maplibre.android.maps.MapLibreMap? = null
        var native: View? = null
        rule.waitUntil(30_000) {
            instrumentation.runOnMainSync {
                val views = descendants(rule.activity.window.decorView)
                if (style == MapStylePreference.OPENSTREETMAP) {
                    val view = views.filterIsInstance<org.maplibre.android.maps.MapView>().firstOrNull()
                    native = view
                    view?.getMapAsync { osm = it }
                } else {
                    val view = views.filterIsInstance<com.google.android.gms.maps.MapView>().firstOrNull()
                    native = view
                    view?.getMapAsync { google = it }
                }
            }
            google != null || osm != null
        }
        if (style == MapStylePreference.OPENSTREETMAP) {
            rule.waitUntil(60_000) {
                rule.mainClock.advanceTimeBy(32)
                var ready = false
                instrumentation.runOnMainSync { ready = osm?.style?.isFullyLoaded == true }
                ready
            }
        }
        // Initial saved-location positioning can complete after the provider's style callback.
        settle(6_000)
        instrumentation.runOnMainSync {
            google?.moveCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                com.google.android.gms.maps.model.LatLng(49.87, 8.65), 16f))
            osm?.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                org.maplibre.android.geometry.LatLng(49.87, 8.65), 16.0))
        }
        settle(3_000)
        if (osm != null) rule.waitUntil(15_000) {
            rule.mainClock.advanceTimeBy(32)
            var ready = false
            instrumentation.runOnMainSync { ready = osm!!.markers.size == 1 }
            ready
        }
        screenshot("$style-$kind-marker")
        var x = 0f
        var y = 0f
        instrumentation.runOnMainSync {
            val origin = IntArray(2)
            native!!.getLocationOnScreen(origin)
            if (osm != null) {
                assertEquals(1, osm!!.markers.size)
                assertEquals(primary.uniqueId, osm!!.markers.single().title)
                val p = osm!!.projection.toScreenLocation(org.maplibre.android.geometry.LatLng(49.87, 8.65))
                x = p.x.toFloat() + origin[0]; y = p.y.toFloat() + origin[1] - 12f
            } else {
                val p = google!!.projection.toScreenLocation(com.google.android.gms.maps.model.LatLng(49.87, 8.65))
                x = p.x.toFloat() + origin[0]; y = p.y.toFloat() + origin[1] - 12f
            }
        }
        val time = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, x, y, 0)
            instrumentation.sendPointerSync(event)
            event.recycle()
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Also at this stop").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Catch 5 Pokemon").assertIsDisplayed()
        rule.onNodeWithText("500 Stardust").assertIsDisplayed()
        screenshot("$style-$kind-details")
        rule.onNodeWithTag("same_stop_${quest.uniqueId}").performClick()
        settle(1_000)
        screenshot("$style-$kind-quest-full")
        // The full detail Activity must be on top, rather than silently leaving the tap unhandled.
        instrumentation.runOnMainSync {
            val resumed = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
            assertTrue("Quest full detail Activity must open", resumed.any { it is AlertDetailActivity })
        }
        instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
    }

    private fun settle(durationMillis: Long) {
        val end = SystemClock.uptimeMillis() + durationMillis
        while (SystemClock.uptimeMillis() < end) {
            rule.mainClock.advanceTimeBy(32)
            SystemClock.sleep(32)
        }
        rule.waitForIdle()
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun screenshot(name: String) {
        settle(750)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "shared-stop-$name.png")
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
