package com.example.pokemonalertsv2.util

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    @Test
    fun samePointIsZero() {
        assertEquals(0.0, haversineMeters(49.7, 8.6, 49.7, 8.6), 0.0)
    }

    @Test
    fun oneDegreeOfLatitudeIsAbout111Kilometres() {
        assertEquals(111_195.0, haversineMeters(49.0, 8.6, 50.0, 8.6), 1.0)
    }

    @Test
    fun knownCityDistance() {
        // Darmstadt Hauptbahnhof to Frankfurt Hauptbahnhof, about 26.2 km in a straight line.
        assertEquals(26_180.0, haversineMeters(49.8726, 8.6290, 50.1070, 8.6633), 50.0)
    }

    @Test
    fun isSymmetric() {
        assertEquals(
            haversineMeters(49.75, 8.62, 49.78, 8.66),
            haversineMeters(49.78, 8.66, 49.75, 8.62),
            1e-9
        )
    }

    @Test
    fun antipodesDoNotOverflowTheArcsine() {
        assertEquals(Math.PI * 6_371_000.0, haversineMeters(0.0, 0.0, 0.0, 180.0), 1.0)
    }

    @Test
    fun walkingSpeedMatchesTheBackend() {
        // The backend's WALKING_SPEED_KMH (src/routing_providers.js) is 4.9 km/h; both sides
        // must estimate the same walk times.
        assertEquals(4.9f / 3.6f, WalkingRouteUtils.AVERAGE_WALKING_SPEED_MPS, 0.002f)
    }
}
