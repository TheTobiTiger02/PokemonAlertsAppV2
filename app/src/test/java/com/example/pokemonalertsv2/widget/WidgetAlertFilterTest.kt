package com.example.pokemonalertsv2.widget

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WidgetAlertFilterTest {

    @Before
    fun setUp() {
        WidgetAlertSnapshotStore.clearForTesting()
    }

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
    fun filterWithoutDistance_keepsOutOfRangeCadenceCandidate() {
        val outOfRange = sampleAlert("Out Of Range", area = "North")
        val wrongArea = sampleAlert("Wrong Area", area = "South")

        val result = WidgetAlertFilter.filterWithoutDistance(
            alerts = listOf(outOfRange, wrongArea),
            criteria = criteria(
                selectedArea = "North",
                maxDistanceKm = 5
            )
        )

        assertEquals(listOf(outOfRange), result)
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
    fun filterAlerts_hidesExpiredAlerts() {
        val active = sampleAlert("Active", endTime = "2000")
        val expired = sampleAlert("Expired", endTime = "1000")

        val result = WidgetAlertFilter.filterAlerts(
            alerts = listOf(active, expired),
            criteria = criteria(),
            origin = null
        )

        assertEquals(listOf(active), (result as WidgetAlertFilter.Result.Filtered).alerts)
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

    @Test
    fun snapshotStore_preservesPreviousDistanceResultButDropsExpiredAndDismissedAlerts() {
        val appWidgetId = 42
        val visible = sampleAlert("Visible", area = "North", endTime = "2000")
        val expired = sampleAlert("Expired", area = "North", endTime = "1000")
        val dismissed = sampleAlert("Dismissed", area = "North", endTime = "2000")
        val wrongArea = sampleAlert("Wrong Area", area = "South", endTime = "2000")
        WidgetAlertSnapshotStore.putForTesting(
            appWidgetId = appWidgetId,
            alerts = listOf(visible, expired, dismissed, wrongArea)
        )

        val result = WidgetAlertSnapshotStore.resolve(
            appWidgetId = appWidgetId,
            alerts = listOf(sampleAlert("Needs Location", area = "North", endTime = "2000")),
            criteria = criteria(
                dismissedAlertIds = setOf(dismissed.uniqueId),
                selectedArea = "North",
                maxDistanceKm = 5
            ),
            origin = null
        )

        assertEquals(listOf(visible), result)
    }

    @Test
    fun snapshotStore_returnsEmptyWhenDistanceNeedsMissingLocationAndNoPreviousSnapshotExists() {
        val result = WidgetAlertSnapshotStore.resolve(
            appWidgetId = 99,
            alerts = listOf(sampleAlert("Needs Location")),
            criteria = criteria(maxDistanceKm = 5),
            origin = null
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun snapshotStore_tracksCadenceExpirationForDistanceFilteredAlerts() {
        val outOfRange = sampleAlert("Out Of Range", endTime = "1500")

        WidgetAlertSnapshotStore.updateCadence(
            appWidgetId = 10,
            alerts = listOf(outOfRange)
        )

        assertEquals(1_500L, WidgetAlertSnapshotStore.nextExpirationMillis(nowMillis = 1_000L))
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
        area: String? = null,
        endTime: String = "2000"
    ) = PokemonAlert(
        name = name,
        endTime = endTime,
        latitude = 49.74,
        longitude = 8.62,
        type = listOf("Quest"),
        area = area
    )

    private companion object {
        val origin = WidgetAlertFilter.Origin(latitude = 49.74, longitude = 8.62)
    }
}
