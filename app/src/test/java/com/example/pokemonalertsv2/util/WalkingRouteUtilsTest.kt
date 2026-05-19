package com.example.pokemonalertsv2.util

import com.example.pokemonalertsv2.data.PokemonAlert
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
    fun estimateWalkingRouteInfo_appliesDetourFactorAndWalkingSpeed() {
        val routeInfo = WalkingRouteUtils.estimateWalkingRouteInfo(800f)

        assertEquals(WalkingRouteInfo(distanceMeters = 1_000, durationSeconds = 741), routeInfo)
    }

    @Test
    fun estimateWalkingRouteInfo_returnsNullForInvalidDistances() {
        assertNull(WalkingRouteUtils.estimateWalkingRouteInfo(null))
        assertNull(WalkingRouteUtils.estimateWalkingRouteInfo(-1f))
        assertNull(WalkingRouteUtils.estimateWalkingRouteInfo(Float.NaN))
        assertNull(WalkingRouteUtils.estimateWalkingRouteInfo(Float.POSITIVE_INFINITY))
    }

    @Test
    fun buildEstimatedWalkingRoutes_skipsAlertsWithoutCoordinates() {
        val completeAlert = alert(name = "Complete", latitude = 52.1, longitude = 13.1)
        val missingLatitude = alert(name = "Missing latitude", latitude = null, longitude = 13.1)
        val missingLongitude = alert(name = "Missing longitude", latitude = 52.1, longitude = null)

        val routes = WalkingRouteUtils.buildEstimatedWalkingRoutes(
            alerts = listOf(completeAlert, missingLatitude, missingLongitude),
            straightLineDistanceMeters = { _, _ -> 800f }
        )

        assertEquals(setOf(completeAlert.uniqueId), routes.keys)
        assertEquals(WalkingRouteInfo(distanceMeters = 1_000, durationSeconds = 741), routes[completeAlert.uniqueId])
    }

    @Test
    fun formatWalkingDurationSeconds_keepsVeryShortRoutesAtOneMinute() {
        val routeInfo = WalkingRouteUtils.estimateWalkingRouteInfo(0.1f)

        assertEquals("1 min walk", WalkingRouteUtils.formatWalkingDurationSeconds(routeInfo!!.durationSeconds))
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

    private fun alert(
        name: String,
        latitude: Double?,
        longitude: Double?
    ): PokemonAlert = PokemonAlert(
        name = name,
        latitude = latitude,
        longitude = longitude,
        endTime = name
    )
}
