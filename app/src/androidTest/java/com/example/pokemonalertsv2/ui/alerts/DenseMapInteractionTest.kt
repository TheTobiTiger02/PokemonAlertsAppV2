package com.example.pokemonalertsv2.ui.alerts

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import com.example.pokemonalertsv2.R
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.MapStylePreference
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Collections

/** Real provider rendering and touch input; the same fixture is used for before/after profiling. */
class DenseMapInteractionTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun googleDenseClusterTap() = exercise(MapStylePreference.GOOGLE_STANDARD)
    @Test fun osmDenseClusterTap() = exercise(MapStylePreference.OPENSTREETMAP)

    private fun exercise(style: MapStylePreference) {
        val alerts = List(999) { index ->
            PokemonAlert(
                id = 900_000 + index,
                name = "Dense fixture $index",
                pokemon = "Pikachu",
                latitude = 49.87 + (index % 33 - 16) * 0.00015,
                longitude = 8.65 + (index / 33 - 15) * 0.00015,
                endTime = "2099-01-01T00:00:00Z",
                type = listOf("Spawn")
            )
        }
        composeRule.setContent {
            PokemonAlertsV2Theme {
                AlertsMapScreenContent(
                    alerts = alerts, onBack = {}, onRefresh = {},
                    initialMapStyle = style, showBackButton = false,
                    showTimeLabels = true, showSpawnRadius = true
                )
            }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var google: com.google.android.gms.maps.GoogleMap? = null
        var osm: org.maplibre.android.maps.MapLibreMap? = null
        var nativeView: View? = null
        composeRule.waitUntil(30_000) {
            instrumentation.runOnMainSync {
                val views = descendants(composeRule.activity.window.decorView)
                if (style == MapStylePreference.OPENSTREETMAP) {
                    val view = views.filterIsInstance<org.maplibre.android.maps.MapView>().firstOrNull()
                    nativeView = view
                    view?.getMapAsync { osm = it }
                } else {
                    val view = views.filterIsInstance<com.google.android.gms.maps.MapView>().firstOrNull()
                    nativeView = view
                    view?.getMapAsync { google = it }
                }
            }
            google != null || osm != null
        }
        // Let initial location/camera positioning finish before fixing the common starting camera.
        settle(6_000)
        instrumentation.runOnMainSync {
            google?.moveCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                com.google.android.gms.maps.model.LatLng(49.87, 8.65), 12f))
            osm?.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                org.maplibre.android.geometry.LatLng(49.87, 8.65), 12.0))
        }
        settle(3_000)
        capture("${style.name}-before")
        val frames = Collections.synchronizedList(mutableListOf<Long>())
        val uiFrames = Collections.synchronizedList(mutableListOf<Long>())
        val thread = HandlerThread("dense-map-frame-metrics").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            frames += metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            if (metrics.getMetric(FrameMetrics.TOTAL_DURATION) > 100_000_000) {
                Log.i("DenseMapQA", "$style slowFrame=" + (0..8).joinToString { "$it:${metrics.getMetric(it) / 1_000_000.0}" })
            }
            uiFrames += metrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION) +
                metrics.getMetric(FrameMetrics.ANIMATION_DURATION) +
                metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) +
                metrics.getMetric(FrameMetrics.DRAW_DURATION)
        }
        instrumentation.runOnMainSync {
            composeRule.activity.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
        }
        var metricsAttached = true
        val heartbeat = Handler(android.os.Looper.getMainLooper())
        val delays = Collections.synchronizedList(mutableListOf<Long>())
        val pulse = object : Runnable {
            var expected = SystemClock.uptimeMillis() + 16
            override fun run() {
                delays += (SystemClock.uptimeMillis() - expected).coerceAtLeast(0)
                expected = SystemClock.uptimeMillis() + 16
                heartbeat.postDelayed(this, 16)
            }
        }
        heartbeat.postDelayed(pulse, 16)
        val watchHandler = Handler(thread.looper)
        val watch = object : Runnable {
            override fun run() {
                if (SystemClock.uptimeMillis() - pulse.expected > 90) {
                    Log.i("DenseMapQA", "$style busyMain=" + android.os.Looper.getMainLooper().thread.stackTrace.take(18).joinToString(" <- "))
                }
                watchHandler.postDelayed(this, 50)
            }
        }
        watchHandler.postDelayed(watch, 50)
        try {
            val screen = IntArray(2)
            var x = 0f
            var y = 0f
            instrumentation.runOnMainSync {
                nativeView!!.getLocationOnScreen(screen)
                if (google != null) {
                    val point = google!!.projection.toScreenLocation(
                        com.google.android.gms.maps.model.LatLng(49.87, 8.65))
                    x = point.x.toFloat() + screen[0]
                    y = point.y.toFloat() + screen[1]
                } else {
                    val point = osm!!.projection.toScreenLocation(
                        org.maplibre.android.geometry.LatLng(49.87, 8.65))
                    x = point.x + screen[0]
                    y = point.y + screen[1] - 12f
                }
            }
            tap(x, y)
            settle(8_000)
            // Screenshot capture synchronizes the renderer and is not part of user interaction timing.
            heartbeat.removeCallbacks(pulse)
            watchHandler.removeCallbacks(watch)
            instrumentation.runOnMainSync {
                if (metricsAttached) {
                    composeRule.activity.window.removeOnFrameMetricsAvailableListener(listener)
                    metricsAttached = false
                }
            }
            capture("${style.name}-after")
            instrumentation.runOnMainSync {
                val zoom = google?.cameraPosition?.zoom?.toDouble() ?: osm!!.cameraPosition.zoom
                Log.i("DenseMapQA", "$style zoom=$zoom osmMarkers=${osm?.markers?.size}")
                assertEquals("Cluster tap should zoom progressively", 14.0, zoom, 0.05)
                osm?.let { assertTrue("Markers must stay capped", it.markers.size in 1..MAX_RENDERED_MAP_MARKERS_ZOOMED_IN) }
            }
        } finally {
            heartbeat.removeCallbacks(pulse)
            watchHandler.removeCallbacks(watch)
            Log.i("DenseMapQA", "$style maxMainDelayMs=${delays.maxOrNull()}")
            instrumentation.runOnMainSync {
                if (metricsAttached) {
                    composeRule.activity.window.removeOnFrameMetricsAvailableListener(listener)
                    metricsAttached = false
                }
            }
            thread.quitSafely()
            val sorted = synchronized(frames) { frames.toList().sorted() }
            assertTrue("Profiling must capture actual frames", sorted.isNotEmpty())
            Log.i("DenseMapQA", "$style frames=${sorted.size} " +
                "p95Ms=${sorted.getOrNull((sorted.size * .95).toInt())?.div(1_000_000.0)} " +
                "maxMs=${sorted.lastOrNull()?.div(1_000_000.0)} " +
                "maxUiMs=${uiFrames.maxOrNull()?.div(1_000_000.0)}")
        }
        // Repeated taps must keep navigating into smaller groups instead of lifting the cap.
        repeat(2) {
            var zoomBefore = 0.0
            var point = Offset.Zero
            instrumentation.runOnMainSync {
                zoomBefore = google?.cameraPosition?.zoom?.toDouble() ?: osm!!.cameraPosition.zoom
                val latitude = google?.cameraPosition?.target?.latitude ?: osm!!.cameraPosition.target!!.latitude
                val longitude = google?.cameraPosition?.target?.longitude ?: osm!!.cameraPosition.target!!.longitude
                val cluster = clusterMapAlerts(alerts, kotlin.math.floor(zoomBefore * 2) / 2)
                    .filterIsInstance<MapMarkerItem.Cluster>()
                    .minBy { (it.latitude - latitude) * (it.latitude - latitude) +
                        (it.longitude - longitude) * (it.longitude - longitude) }
                val screen = IntArray(2)
                nativeView!!.getLocationOnScreen(screen)
                if (google != null) {
                    val location = google!!.projection.toScreenLocation(
                        com.google.android.gms.maps.model.LatLng(cluster.latitude, cluster.longitude))
                    point = Offset(location.x.toFloat() + screen[0], location.y.toFloat() + screen[1])
                } else {
                    val location = osm!!.projection.toScreenLocation(
                        org.maplibre.android.geometry.LatLng(cluster.latitude, cluster.longitude))
                    point = Offset(location.x + screen[0], location.y + screen[1] - 12f)
                }
            }
            tap(point.x, point.y)
            settle(2_000)
            instrumentation.runOnMainSync {
                val zoom = google?.cameraPosition?.zoom?.toDouble() ?: osm!!.cameraPosition.zoom
                assertEquals(zoomBefore + 2.0, zoom, 0.05)
                osm?.let { assertTrue(it.markers.size <= MAX_RENDERED_MAP_MARKERS_ZOOMED_IN) }
            }
        }
        instrumentation.runOnMainSync {
            osm?.style?.layers?.map { it.id }?.let { layers ->
                val annotationIndex = layers.indexOf("org.maplibre.annotations.points")
                assertTrue("Native marker layer should exist", annotationIndex >= 0)
                assertTrue("Spawn circles must be below alert markers", layers.indexOf("spawn-radius-layer") < annotationIndex)
            }
        }
        capture("${style.name}-zoomed-in")
        composeRule.onNodeWithTag("map_full_content").performTouchInput {
            swipe(center, center + Offset(140f, 0f), durationMillis = 300)
        }
        settle(1_000)
        composeRule.onNodeWithTag("map_full_content").performTouchInput {
            pinch(center - Offset(80f, 0f), center - Offset(240f, 0f),
                center + Offset(80f, 0f), center + Offset(240f, 0f), durationMillis = 400)
        }
        settle(1_000)
        composeRule.onNodeWithContentDescription(
            instrumentation.targetContext.getString(R.string.map_show_all_alerts)
        ).performClick()
        settle(2_000)
        instrumentation.runOnMainSync {
            osm?.let { assertTrue(it.markers.size in 1..MAX_RENDERED_MAP_MARKERS_ZOOMED_IN) }
        }
        capture("${style.name}-fit-all")

    }

    private fun tap(x: Float, y: Float) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val down = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            automation.injectInputEvent(event, true)
            event.recycle()
        }
    }

    private fun settle(durationMillis: Long) {
        val end = SystemClock.uptimeMillis() + durationMillis
        while (SystemClock.uptimeMillis() < end) {
            composeRule.mainClock.advanceTimeBy(32)
            SystemClock.sleep(32)
        }
        composeRule.waitForIdle()
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
        else emptyList()

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "dense-map-qa")
            .apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
