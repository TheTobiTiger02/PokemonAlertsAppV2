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
    fun estimateWalkingRouteInfo_appliesDetourFactorAndWalkingSpeed() {
        // 800 m straight line -> 880 m at the measured 1.10 detour factor,
        // walked at 1.36 m/s -> 647.1 s, rounded up.
        val routeInfo = WalkingRouteUtils.estimateWalkingRouteInfo(800f)
        assertEquals(880, routeInfo?.distanceMeters)
        assertEquals(648L, routeInfo?.durationSeconds)

        assertNull(WalkingRouteUtils.estimateWalkingRouteInfo(null))
        assertNull(WalkingRouteUtils.estimateWalkingRouteInfo(-1f))
        assertNull(WalkingRouteUtils.estimateWalkingRouteInfo(Float.NaN))
        assertNull(WalkingRouteUtils.estimateWalkingRouteInfo(Float.POSITIVE_INFINITY))
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
    fun buildRouteDisplayInfo_fallsBackToEstimateWhenRouteInfoIsNull() {
        val displayInfo = WalkingRouteUtils.buildRouteDisplayInfo(
            straightLineDistanceMeters = 1_000f,
            routeInfo = null,
            fallbackToEstimate = true
        )

        assertEquals(1_000f, displayInfo.straightLineDistanceMeters)
        assertNull(displayInfo.routedDistanceMeters)
        // 1000 m straight line -> 1100 m at the 1.10 detour factor, walked at
        // 1.36 m/s -> 808.8 s, rounded up to 809 and displayed as 14 minutes.
        assertEquals(1_100f, displayInfo.effectiveDistanceMeters)
        assertEquals(DistanceSource.ESTIMATED, displayInfo.source)
        assertEquals("~1.1 km", displayInfo.distanceText)
        assertEquals("~14 min walk", displayInfo.walkingText)
    }

    @Test
    fun buildRouteDisplayInfo_labelsDirectDistanceWhenFallbackDisabled() {
        val displayInfo = WalkingRouteUtils.buildRouteDisplayInfo(
            straightLineDistanceMeters = 1_000f,
            routeInfo = null,
            fallbackToEstimate = false
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
