package com.example.pokemonalertsv2.widget

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WidgetAlertFilterTest {

    @Test
    fun filterAlerts_keepsInRangeAlert() {
        val alert = sampleAlert("In Range")

        val result = WidgetAlertFilter.filterAlerts(
            alerts = listOf(alert),
            criteria = criteria(maxDistanceKm = 5),
            origin = origin,
            distanceMeters = { _, _ -> 1_000f }
        )

        assertEquals(listOf(alert), (result as WidgetAlertFilter.Result.Filtered).alerts)
    }

    @Test
    fun filterAlerts_hidesOutOfRangeAlert() {
        val inRange = sampleAlert("In Range")
        val outOfRange = sampleAlert("Out Of Range")

        val result = WidgetAlertFilter.filterAlerts(
            alerts = listOf(inRange, outOfRange),
            criteria = criteria(maxDistanceKm = 5),
            origin = origin,
            distanceMeters = { _, alert ->
                if (alert.name == "In Range") 1_000f else 6_000f
            }
        )

        assertEquals(listOf(inRange), (result as WidgetAlertFilter.Result.Filtered).alerts)
    }

    @Test
    fun filterAlerts_appliesSelectedArea() {
        val north = sampleAlert("North", area = "North")
        val south = sampleAlert("South", area = "South")

        val result = WidgetAlertFilter.filterAlerts(
            alerts = listOf(north, south),
            criteria = criteria(selectedArea = "North"),
            origin = null
        )

        assertEquals(listOf(north), (result as WidgetAlertFilter.Result.Filtered).alerts)
    }

    @Test
    fun filterAlerts_hidesDismissedAlerts() {
        val visible = sampleAlert("Visible")
        val dismissed = sampleAlert("Dismissed")

        val result = WidgetAlertFilter.filterAlerts(
            alerts = listOf(visible, dismissed),
            criteria = criteria(dismissedAlertIds = setOf(dismissed.uniqueId)),
            origin = null
        )

        assertEquals(listOf(visible), (result as WidgetAlertFilter.Result.Filtered).alerts)
    }

    @Test
    fun filterAlerts_preservesPreviousWhenMaxDistanceNeedsMissingLocation() {
        val result = WidgetAlertFilter.filterAlerts(
            alerts = listOf(sampleAlert("Unknown Distance")),
            criteria = criteria(maxDistanceKm = 5),
            origin = null
        )

        assertSame(WidgetAlertFilter.Result.PreservePrevious, result)
    }

    private fun criteria(
        dismissedAlertIds: Set<String> = emptySet(),
        selectedArea: String = "All",
        maxDistanceKm: Int = 0,
        widgetFilterTypes: Set<String> = emptySet()
    ) = WidgetAlertFilter.Criteria(
        dismissedAlertIds = dismissedAlertIds,
        selectedArea = selectedArea,
        maxDistanceKm = maxDistanceKm,
        widgetFilterTypes = widgetFilterTypes,
        nowMillis = 1_000L
    )

    private fun sampleAlert(
        name: String,
        area: String? = null
    ) = PokemonAlert(
        name = name,
        endTime = "2000",
        latitude = 49.74,
        longitude = 8.62,
        type = listOf("Quest"),
        area = area
    )

    private companion object {
        val origin = WidgetAlertFilter.Origin(latitude = 49.74, longitude = 8.62)
    }
}
