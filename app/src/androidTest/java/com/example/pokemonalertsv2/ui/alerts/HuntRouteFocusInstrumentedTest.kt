package com.example.pokemonalertsv2.ui.alerts

import android.location.Location
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.*
import com.example.pokemonalertsv2.hunt.HuntRepository
import com.example.pokemonalertsv2.tracking.ArrivalTrackingRepository
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class HuntRouteFocusInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun googleFocusAlternatesAndKeepsOverview() = exercise(MapStylePreference.GOOGLE_STANDARD)
    @Test fun osmFocusAlternatesAndKeepsOverview() = exercise(MapStylePreference.OPENSTREETMAP)

    private fun exercise(style: MapStylePreference) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val hunts = HuntRepository.getInstance(context)
        val journeys = ArrivalTrackingRepository.getInstance(context)
        assumeTrue(runBlocking { hunts.currentSession() == null && journeys.currentDestination() == null })
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.ACCESS_FINE_LOCATION").close()
        val target = PokemonAlert(id = 9_951_001, name = "Focus near", pokemon = "Flamigo", type = listOf("Rare"), latitude = 49.741, longitude = 8.6)
        val next = target.copy(id = 9_951_002, name = "Focus far", latitude = 49.80, longitude = 8.68)
        var emit: (MapUserPose) -> Unit = {}
        fun pose(latitude: Double) = MapUserPose(Location("test").apply {
            this.latitude = latitude; longitude = 8.6; accuracy = 3f
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }, headingDegrees = 0f, headingFromSensor = true)
        runBlocking {
            hunts.start("Focus QA", FilterDefinition(alertTypes = FilterSelection.only(listOf("Rare"))))
            journeys.startTracking(target)
            hunts.setTarget(target.uniqueId)
        }
        try {
            rule.setContent { PokemonAlertsV2Theme {
                AlertsMapScreenContent(alerts = listOf(target, next), onBack = {}, onRefresh = {},
                    initialMapStyle = style, initialZoom = 16.0, showWeatherCells = false,
                    locationTrackerFactory = { _, onPose, onStatus ->
                        emit = onPose
                        object : MapPoseTracker {
                            override fun start() { onPose(pose(49.740)); onStatus(MapTrackingStatus.ACTIVE) }
                            override fun stop() = Unit
                        }
                    })
            } }
            var google: com.google.android.gms.maps.GoogleMap? = null
            var osm: org.maplibre.android.maps.MapLibreMap? = null
            fun descendants(view: View): List<View> = listOf(view) +
                if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
            rule.waitUntil(30_000) {
                instrumentation.runOnMainSync {
                    val views = descendants(rule.activity.window.decorView)
                    views.filterIsInstance<com.google.android.gms.maps.MapView>().firstOrNull()?.getMapAsync { google = it }
                    views.filterIsInstance<org.maplibre.android.maps.MapView>().firstOrNull()?.getMapAsync { osm = it }
                }
                google != null || osm != null
            }
            if (osm != null) rule.waitUntil(45_000) {
                var ready = false
                instrumentation.runOnMainSync { ready = osm?.style?.isFullyLoaded == true }
                ready
            }
            SystemClock.sleep(4_000)
            rule.onNode(hasContentDescription("Start live location tracking") or
                hasContentDescription("Following your live location") or
                hasContentDescription("Live location active; recenter map")).performClick()
            SystemClock.sleep(1_000)
            instrumentation.runOnMainSync { emit(pose(49.740)) }
            SystemClock.sleep(1_000)
            fun zoom(): Double {
                var value = 0.0
                instrumentation.runOnMainSync { value = google?.cameraPosition?.zoom?.toDouble() ?: osm!!.cameraPosition.zoom }
                return value
            }
            rule.onNodeWithContentDescription("Focus Hunt target").performClick()
            SystemClock.sleep(1_500)
            val targetZoom = zoom()
            rule.onNodeWithContentDescription("Show Hunt route").performClick()
            SystemClock.sleep(1_500)
            val routeZoom = zoom()
            assertTrue("Overview must include the far numbered alert: target=$targetZoom route=$routeZoom", routeZoom < targetZoom - 1)
            instrumentation.runOnMainSync {
                google?.let { assertTrue(it.projection.visibleRegion.latLngBounds.contains(com.google.android.gms.maps.model.LatLng(next.latitude!!, next.longitude!!))) }
                osm?.let { assertTrue(it.projection.visibleRegion.latLngBounds.contains(org.maplibre.android.geometry.LatLng(next.latitude!!, next.longitude!!))) }
                emit(pose(49.739))
            }
            SystemClock.sleep(1_500)
            assertEquals("GPS must not snap overview back to the current target", routeZoom, zoom(), 0.1)
            rule.onNodeWithContentDescription("Focus Hunt target").performClick()
            SystemClock.sleep(1_500)
            assertTrue(zoom() > routeZoom + 1)
        } finally {
            runBlocking { hunts.stop(); journeys.stopTracking() }
        }
    }
}
