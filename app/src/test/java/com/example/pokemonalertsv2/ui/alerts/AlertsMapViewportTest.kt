package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.util.DistanceSource
import com.example.pokemonalertsv2.util.WalkingRouteInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class AlertsMapViewportTest {

    @Test
    fun `coordinates reject missing non finite out of range and zero origin values`() {
        assertNull(validMapCoordinates(null, 8.0))
        assertNull(validMapCoordinates(49.0, null))
        assertNull(validMapCoordinates(Double.NaN, 8.0))
        assertNull(validMapCoordinates(49.0, Double.POSITIVE_INFINITY))
        assertNull(validMapCoordinates(91.0, 8.0))
        assertNull(validMapCoordinates(49.0, 181.0))
        assertNull(validMapCoordinates(0.0, 0.0))

        assertEquals(AlertMapCoordinates(0.0, 8.0), validMapCoordinates(0.0, 8.0))
    }

    @Test
    fun `initial viewport prioritizes a valid user location`() {
        val viewport = resolveInitialMapViewport(
            userLatitude = 50.1,
            userLongitude = 8.7,
            alerts = listOf(alert("alert", 49.8, 8.6))
        )

        assertEquals(MapViewportTarget(50.1, 8.7, 16f), viewport)
    }

    @Test
    fun `initial viewport skips invalid alerts and uses first valid alert`() {
        val viewport = resolveInitialMapViewport(
            userLatitude = null,
            userLongitude = null,
            alerts = listOf(
                alert("missing", null, null),
                alert("zero", 0.0, 0.0),
                alert("first-valid", 49.8728, 8.6512),
                alert("later-valid", 49.9, 8.7)
            )
        )

        assertEquals(MapViewportTarget(49.8728, 8.6512, 14f), viewport)
    }

    @Test
    fun `initial viewport falls back to Alsbach when no valid location exists`() {
        val viewport = resolveInitialMapViewport(
            userLatitude = 0.0,
            userLongitude = 0.0,
            alerts = listOf(alert("invalid", 95.0, 8.0))
        )

        assertEquals(MapViewportTarget(49.74677, 8.62492, 13f), viewport)
    }

    @Test
    fun `fit all coordinates keeps valid markers and rejects invalid markers`() {
        val coordinates = resolveFitAllCoordinates(
            listOf(
                alert("first", 49.8, 8.6),
                alert("missing", null, 8.7),
                alert("zero", 0.0, 0.0),
                alert("second", 49.9, 8.8)
            )
        )

        assertEquals(
            listOf(
                AlertMapCoordinates(49.8, 8.6),
                AlertMapCoordinates(49.9, 8.8)
            ),
            coordinates
        )
        assertEquals(emptyList<AlertMapCoordinates>(), resolveFitAllCoordinates(emptyList()))
    }

    @Test
    fun `selected alert distance includes routed walking metadata`() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            val info = resolveMapAlertDistanceInfo(
                userLatitude = 49.7,
                userLongitude = 8.6,
                alert = alert("destination", 49.8, 8.7),
                routeInfo = WalkingRouteInfo(distanceMeters = 1_300, durationSeconds = 960),
                distanceBetween = { _, _ -> 1_000f }
            )

            assertEquals(1_300f, info?.distanceMeters)
            assertEquals(1_000f, info?.straightLineDistanceMeters)
            assertEquals(DistanceSource.ROUTED, info?.source)
            assertEquals("1.3 km", info?.distanceText)
            assertEquals("16 min walk", info?.walkingText)
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun `selected alert distance is unavailable without valid endpoints or measurement`() {
        val destination = alert("destination", 49.8, 8.7)

        assertNull(resolveMapAlertDistanceInfo(null, 8.6, destination) { _, _ -> 100f })
        assertNull(resolveMapAlertDistanceInfo(49.7, 8.6, alert("invalid", null, null)) { _, _ -> 100f })
        assertNull(resolveMapAlertDistanceInfo(49.7, 8.6, destination) { _, _ -> Float.NaN })
    }

    private fun alert(name: String, latitude: Double?, longitude: Double?) = PokemonAlert(
        name = name,
        latitude = latitude,
        longitude = longitude
    )
}
