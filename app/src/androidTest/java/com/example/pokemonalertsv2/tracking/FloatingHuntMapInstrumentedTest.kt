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
                overlay.setAlerts(targets, targets.first().uniqueId, targets.size)
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

    @Test fun minimizeFoldsIntoTheBubbleAndExpandRestoresTheMap() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(FloatingMapOverlay.canDraw(context))
        lateinit var overlay: FloatingMapOverlay
        val reported = mutableListOf<Boolean>()
        instrumentation.runOnMainSync {
            overlay = FloatingMapOverlay(context)
            overlay.onMinimizedChanged = { reported += it }
            overlay.show()
        }
        try {
            instrumentation.runOnMainSync {
                assertTrue(overlay.isShowing)
                assertFalse(overlay.isMinimized)
                overlay.minimize()
                assertTrue(overlay.isMinimized)
                overlay.setBubbleReadout("120 m")
                overlay.pulseBubble()
                overlay.expand()
                assertFalse(overlay.isMinimized)
            }
            assertEquals(listOf(true, false), reported)
        } finally { instrumentation.runOnMainSync { overlay.hide() } }
    }

    @Test fun suppressingKeepsTheWindowAndItsStateAndThemesRepaint() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(FloatingMapOverlay.canDraw(context))
        lateinit var overlay: FloatingMapOverlay
        instrumentation.runOnMainSync {
            overlay = FloatingMapOverlay(context)
            overlay.show()
        }
        try {
            instrumentation.runOnMainSync {
                overlay.setSuppressed(true)
                assertTrue(overlay.isShowing)
                overlay.applyColors(OverlayColors.Dark)
                overlay.setSuppressed(false)
                assertFalse(overlay.isMinimized)
                overlay.minimize()
                overlay.applyColors(OverlayColors.Light)
                overlay.setSuppressed(true)
                overlay.setSuppressed(false)
                assertTrue(overlay.isMinimized)
                overlay.setNotice("Mewtwo raid ended")
                overlay.setUndoOffer("Pikachu")
                overlay.setRouteLine(false)
            }
        } finally { instrumentation.runOnMainSync { overlay.hide() } }
    }

    @Test fun aWindowHiddenWhileMinimizedReopensMinimized() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(FloatingMapOverlay.canDraw(context))
        lateinit var overlay: FloatingMapOverlay
        instrumentation.runOnMainSync {
            overlay = FloatingMapOverlay(context)
            overlay.restoreLook(com.example.pokemonalertsv2.data.FloatingMapLook(opacity = 0.5f, minimized = true))
            overlay.show()
        }
        try {
            instrumentation.runOnMainSync { assertTrue(overlay.isMinimized) }
        } finally { instrumentation.runOnMainSync { overlay.hide() } }
    }

    @Test fun followingKeepsTheTrainersZoom() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(FloatingMapOverlay.canDraw(context))
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
            // Engaging from far out comes down to walking zoom.
            instrumentation.runOnMainSync { overlay.follow(49.740, 8.6, engage = true) }
            SystemClock.sleep(1_500)
            var zoom = 0.0
            instrumentation.runOnMainSync { zoom = map!!.cameraPosition.zoom }
            assertEquals(com.example.pokemonalertsv2.ui.alerts.MAP_PIP_CLOSE_ZOOM, zoom, 0.1)
            // The trainer zooms out; following the next fix keeps their zoom and centres.
            instrumentation.runOnMainSync {
                map!!.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.zoomTo(15.5))
                overlay.follow(49.741, 8.601)
            }
            SystemClock.sleep(1_500)
            instrumentation.runOnMainSync {
                val camera = map!!.cameraPosition
                assertEquals(15.5, camera.zoom, 0.1)
                assertEquals(49.741, camera.target!!.latitude, 0.0005)
                assertEquals(8.601, camera.target!!.longitude, 0.0005)
            }
        } finally { instrumentation.runOnMainSync { overlay.hide() } }
    }
}
