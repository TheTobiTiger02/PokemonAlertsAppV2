package com.example.pokemonalertsv2.widget

import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.util.WalkingRouteInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadarWidgetStateTest {
    @Test
    fun nearestOrderUsesRouteForAnAlertThenDirectDistanceAsFallback() {
        val directNearest = alert("Direct nearest", id = 1)
        val routedFarther = alert("Routed farther", id = 2)
        val directSecond = alert("Direct second", id = 3)
        val directDistances = mapOf(
            directNearest.uniqueId to 100f,
            routedFarther.uniqueId to 50f,
            directSecond.uniqueId to 200f
        )

        val ordered = orderRadarAlerts(
            alerts = listOf(routedFarther, directSecond, directNearest),
            origin = WidgetAlertFilter.Origin(49.87, 8.65),
            walkingRoutes = mapOf(
                routedFarther.uniqueId to WalkingRouteInfo(
                    distanceMeters = 500,
                    durationSeconds = 360
                )
            ),
            distanceMeters = { _, alert -> directDistances[alert.uniqueId] }
        )

        assertEquals(
            listOf(directNearest, directSecond, routedFarther),
            ordered
        )
    }

    @Test
    fun missingLocationFallsBackToEndingSoon() {
        val later = alert("Later", id = 1, endTime = "2099-01-01T12:00:00Z")
        val sooner = alert("Sooner", id = 2, endTime = "2099-01-01T11:00:00Z")

        val ordered = orderRadarAlerts(
            alerts = listOf(later, sooner),
            origin = null,
            walkingRoutes = emptyMap()
        )

        assertEquals(listOf(sooner, later), ordered)
    }

    @Test
    fun selectionIsPreservedAndMissingSelectionUsesNearest() {
        val nearest = alert("Nearest", id = 1)
        val second = alert("Second", id = 2)

        val preserved = resolveRadarSelection(
            orderedAlerts = listOf(nearest, second),
            requestedState = RadarWidgetState(
                selectedAlertId = second.uniqueId,
                viewMode = RadarViewMode.OVERVIEW
            )
        )
        val replaced = resolveRadarSelection(
            orderedAlerts = listOf(nearest, second),
            requestedState = RadarWidgetState(
                selectedAlertId = "expired|alert",
                viewMode = RadarViewMode.FOCUS
            )
        )

        assertEquals(second, preserved.selectedAlert)
        assertEquals(1, preserved.selectedIndex)
        assertEquals(RadarViewMode.OVERVIEW, preserved.state.viewMode)
        assertEquals(nearest, replaced.selectedAlert)
        assertEquals(nearest.uniqueId, replaced.state.selectedAlertId)
    }

    @Test
    fun nextWrapsAndEmptyAlertsClearSelection() {
        val first = alert("First", id = 1)
        val second = alert("Second", id = 2)
        val alerts = listOf(first, second)

        val fromFirst = advanceRadarSelection(
            alerts,
            RadarWidgetState(selectedAlertId = first.uniqueId)
        )
        val wrapped = advanceRadarSelection(alerts, fromFirst)
        val empty = advanceRadarSelection(emptyList(), wrapped)

        assertEquals(second.uniqueId, fromFirst.selectedAlertId)
        assertEquals(first.uniqueId, wrapped.selectedAlertId)
        assertNull(empty.selectedAlertId)
    }

    private fun alert(
        name: String,
        id: Int,
        endTime: String = "2099-01-01T10:00:00Z"
    ) = PokemonAlert(
        id = id,
        name = name,
        latitude = 49.87 + id * 0.001,
        longitude = 8.65 + id * 0.001,
        endTime = endTime
    )
}
