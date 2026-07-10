package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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

    private fun alert(name: String, latitude: Double?, longitude: Double?) = PokemonAlert(
        name = name,
        latitude = latitude,
        longitude = longitude
    )
}
