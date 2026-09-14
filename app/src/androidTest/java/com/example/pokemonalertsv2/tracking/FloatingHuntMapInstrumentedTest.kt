package com.example.pokemonalertsv2.tracking

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.hunt.huntRouteFocusCoordinates
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap

class FloatingHuntMapInstrumentedTest {
    @Test fun routeFitIncludesNumberedTargetsAndTrainer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(FloatingMapOverlay.canDraw(context))
        val targets = listOf(
            PokemonAlert(name = "Near", latitude = 49.741, longitude = 8.6),
            PokemonAlert(name = "Far", latitude = 49.80, longitude = 8.68)
        )
        lateinit var overlay: FloatingMapOverlay
        var map: MapLibreMap? = null
        instrumentation.runOnMainSync {
            overlay = FloatingMapOverlay(context)
            overlay.show { map = it }
        }
        try {
            val deadline = SystemClock.elapsedRealtime() + 30_000
            var ready = false
            while (!ready && SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync { ready = map?.style?.isFullyLoaded == true }
                SystemClock.sleep(100)
            }
            assertTrue("Floating map must load", ready)
            instrumentation.runOnMainSync {
                overlay.setAlerts(targets, targets.first().uniqueId)
                overlay.focus(49.740, 8.6, 49.741, 8.6, force = true)
            }
            SystemClock.sleep(1_500)
            var targetZoom = 0.0
            instrumentation.runOnMainSync { targetZoom = map!!.cameraPosition.zoom }
            val points = huntRouteFocusCoordinates(targets, 49.740, 8.6)
            instrumentation.runOnMainSync { overlay.focusRoute(points, force = true) }
            SystemClock.sleep(1_500)
            instrumentation.runOnMainSync {
                assertTrue(map!!.cameraPosition.zoom < targetZoom - 1)
                points.forEach {
                    assertTrue(map!!.projection.visibleRegion.latLngBounds.contains(org.maplibre.android.geometry.LatLng(it.latitude, it.longitude)))
                }
                overlay.focus(49.740, 8.6, 49.741, 8.6, force = true)
            }
            SystemClock.sleep(1_500)
            instrumentation.runOnMainSync { assertEquals(targetZoom, map!!.cameraPosition.zoom, 0.1) }
        } finally { instrumentation.runOnMainSync { overlay.hide() } }
    }
}
