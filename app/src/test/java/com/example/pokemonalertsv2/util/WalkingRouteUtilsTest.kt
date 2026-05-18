package com.example.pokemonalertsv2.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun routeInfoFromMatrixCells_roundsDistanceAndDurationUp() {
        val routeInfo = WalkingRouteUtils.routeInfoFromMatrixCells(
            durationSeconds = 159.2,
            distanceMeters = 821.4
        )

        assertEquals(WalkingRouteInfo(distanceMeters = 822, durationSeconds = 160), routeInfo)
    }

    @Test
    fun routeInfoFromMatrixCells_returnsNullForFailedOrIncompleteRoutes() {
        assertNull(
            WalkingRouteUtils.routeInfoFromMatrixCells(
                durationSeconds = null,
                distanceMeters = 822.0
            )
        )
        assertNull(
            WalkingRouteUtils.routeInfoFromMatrixCells(
                durationSeconds = 160.0,
                distanceMeters = null
            )
        )
    }

    @Test
    fun buildRouteDisplayInfo_prefersRouteDistanceAndDuration() {
        val displayInfo = WalkingRouteUtils.buildRouteDisplayInfo(
            straightLineDistanceMeters = 1_000f,
            routeInfo = WalkingRouteInfo(distanceMeters = 1_600, durationSeconds = 960)
        )

        assertEquals(1_000f, displayInfo.straightLineDistanceMeters)
        assertEquals("1.6 km", displayInfo.distanceText)
        assertEquals("16 min walk", displayInfo.walkingText)
    }

    @Test
    fun buildRouteDisplayInfo_keepsStraightLineDistanceButHidesWalkingTextWithoutRoute() {
        val displayInfo = WalkingRouteUtils.buildRouteDisplayInfo(
            straightLineDistanceMeters = 1_000f,
            routeInfo = null
        )

        assertEquals(1_000f, displayInfo.straightLineDistanceMeters)
        assertEquals("1.0 km", displayInfo.distanceText)
        assertNull(displayInfo.walkingText)
    }

    @Test
    fun isRoutesApiKeyConfigured_rejectsBlankKeys() {
        assertFalse(WalkingRouteUtils.isRoutesApiKeyConfigured(""))
        assertFalse(WalkingRouteUtils.isRoutesApiKeyConfigured("   "))
        assertTrue(WalkingRouteUtils.isRoutesApiKeyConfigured("routes-key"))
    }
}
