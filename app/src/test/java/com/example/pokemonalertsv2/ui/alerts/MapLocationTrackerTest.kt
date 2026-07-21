package com.example.pokemonalertsv2.ui.alerts

import android.hardware.SensorManager
import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLocationTrackerTest {
    @Test
    fun `gps tap starts tracking and camera follow`() {
        val state = MapTrackingInteractionState().onGpsTapped()

        assertTrue(state.trackingRequested)
        assertTrue(state.cameraFollowEnabled)
    }

    @Test
    fun `camera gesture stops follow without stopping live tracking`() {
        val state = MapTrackingInteractionState()
            .onGpsTapped()
            .onUserCameraGesture()

        assertTrue(state.trackingRequested)
        assertFalse(state.cameraFollowEnabled)
    }

    @Test
    fun `second gps tap restores follow and show all disables only follow`() {
        val state = MapTrackingInteractionState()
            .onGpsTapped()
            .onUserCameraGesture()
            .onGpsTapped()
            .onShowAllAlerts()

        assertTrue(state.trackingRequested)
        assertFalse(state.cameraFollowEnabled)
    }

    @Test
    fun `heading normalization handles wraparound`() {
        assertEquals(350f, MapHeadingMath.normalize(-10f), 0.001f)
        assertEquals(10f, MapHeadingMath.normalize(370f), 0.001f)
    }

    @Test
    fun `heading smoothing takes shortest path across north`() {
        val result = MapHeadingMath.smooth(previous = 359f, next = 1f, weight = 0.5f)

        assertTrue(result < 0.01f || result > 359.99f)
    }

    @Test
    fun `movement bearing requires reliable movement`() {
        assertNull(
            MapHeadingMath.movementBearing(
                hasBearing = true,
                hasSpeed = true,
                speedMetersPerSecond = 0.2f,
                bearingDegrees = 90f
            )
        )
        assertEquals(
            90f,
            MapHeadingMath.movementBearing(
                hasBearing = true,
                hasSpeed = true,
                speedMetersPerSecond = 1.2f,
                bearingDegrees = 90f
            )!!,
            0.001f
        )
    }

    @Test
    fun `live location rejects invalid stale and older fixes`() {
        assertFalse(shouldAcceptLiveLocation(null, 10L, 30_001L, 49.0, 8.0))
        assertFalse(shouldAcceptLiveLocation(null, 10L, 0L, 91.0, 8.0))
        assertFalse(shouldAcceptLiveLocation(20L, 19L, 0L, 49.0, 8.0))
        assertTrue(shouldAcceptLiveLocation(20L, 21L, 0L, 49.0, 8.0))
    }

    @Test
    fun `accuracy polygon is closed around the requested location`() {
        val polygon = createAccuracyPolygon(49.738, 8.603, radiusMeters = 25.0, points = 12)
        val ring = polygon.coordinates().single()

        assertEquals(13, ring.size)
        assertEquals(ring.first().longitude(), ring.last().longitude(), 0.0000001)
        assertEquals(ring.first().latitude(), ring.last().latitude(), 0.0000001)
    }

    @Test
    fun `sensor axes compensate for every screen rotation`() {
        assertEquals(
            SensorManager.AXIS_X to SensorManager.AXIS_Y,
            sensorAxesForDisplayRotation(Surface.ROTATION_0)
        )
        assertEquals(
            SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X,
            sensorAxesForDisplayRotation(Surface.ROTATION_90)
        )
        assertEquals(
            SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y,
            sensorAxesForDisplayRotation(Surface.ROTATION_180)
        )
        assertEquals(
            SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X,
            sensorAxesForDisplayRotation(Surface.ROTATION_270)
        )
    }
}
