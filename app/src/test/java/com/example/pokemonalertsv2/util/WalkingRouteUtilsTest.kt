package com.example.pokemonalertsv2.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

class WalkingRouteUtilsTest {
    private lateinit var previousLocale: Locale

    @Before
    fun setUp() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun formatWalkingDurationSeconds_keepsVeryShortRoutesAtOneMinute() {
        assertEquals("1 min walk", WalkingRouteUtils.formatWalkingDurationSeconds(1))
    }

    @Test
    fun buildRouteDisplayInfo_prefersRouteDistanceAndDuration() {
        val displayInfo = WalkingRouteUtils.buildRouteDisplayInfo(
            straightLineDistanceMeters = 1_000f,
            routeInfo = WalkingRouteInfo(distanceMeters = 1_600, durationSeconds = 960)
        )

        assertEquals(1_000f, displayInfo.straightLineDistanceMeters)
        assertEquals(1_600f, displayInfo.routedDistanceMeters)
        assertEquals(1_600f, displayInfo.effectiveDistanceMeters)
        assertEquals(DistanceSource.ROUTED, displayInfo.source)
        assertEquals("1.6 km", displayInfo.distanceText)
        assertEquals("16 min walk", displayInfo.walkingText)
    }

    @Test
    fun buildRouteDisplayInfo_labelsDirectDistanceAndHidesWalkingTextWithoutRoute() {
        val displayInfo = WalkingRouteUtils.buildRouteDisplayInfo(
            straightLineDistanceMeters = 1_000f,
            routeInfo = null
        )

        assertEquals(1_000f, displayInfo.straightLineDistanceMeters)
        assertNull(displayInfo.routedDistanceMeters)
        assertEquals(1_000f, displayInfo.effectiveDistanceMeters)
        assertEquals(DistanceSource.DIRECT, displayInfo.source)
        assertEquals("1.0 km direct", displayInfo.distanceText)
        assertNull(displayInfo.walkingText)
    }

    @Test
    fun buildRouteDisplayInfo_rejectsInvalidDirectDistance() {
        val displayInfo = WalkingRouteUtils.buildRouteDisplayInfo(Float.NaN, null)

        assertEquals(DistanceSource.UNAVAILABLE, displayInfo.source)
        assertNull(displayInfo.effectiveDistanceMeters)
        assertNull(displayInfo.distanceText)
        assertNull(displayInfo.walkingText)
    }
}
