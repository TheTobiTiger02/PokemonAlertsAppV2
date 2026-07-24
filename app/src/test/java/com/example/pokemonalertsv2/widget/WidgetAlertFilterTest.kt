package com.example.pokemonalertsv2.widget

import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.util.WalkingRouteInfo
import org.junit.Assert.assertEquals
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
    fun snapshotResolve_prefersRoutedDistanceForMaximumDistance() {
        val alert = sampleAlert("Route Detour")

        val result = WidgetAlertSnapshotStore.resolve(
            alerts = listOf(alert),
            criteria = criteria(maxDistanceKm = 5),
            origin = origin,
            walkingRoutes = mapOf(alert.uniqueId to WalkingRouteInfo(6_000, 4_000))
        )

        assertTrue(result.alerts.isEmpty())
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
    fun filterAlerts_hidesInvalidatedAlerts() {
        val active = sampleAlert("Active")
        val invalidated = sampleAlert("Invalidated").copy(
            invalidatedByAlertId = 10587
        )

        val result = WidgetAlertFilter.filterAlerts(
            alerts = listOf(active, invalidated),
            criteria = criteria(),
            origin = null
        )

        assertEquals(listOf(active), (result as WidgetAlertFilter.Result.Filtered).alerts)
    }

    @Test
    fun filterAlerts_hidesAlertAtExactExpirationBoundary() {
        val expiringNow = sampleAlert("Boundary", endTime = "1000")

        val result = WidgetAlertFilter.filterAlerts(
            alerts = listOf(expiringNow),
            criteria = criteria(nowMillis = 1_000L),
            origin = null
        )

        assertTrue((result as WidgetAlertFilter.Result.Filtered).alerts.isEmpty())
    }

    @Test
    fun filterAlerts_skipsDistanceButKeepsNewAlertsWhenLocationIsMissing() {
        val alert = sampleAlert("Unknown Distance")
        val result = WidgetAlertFilter.filterAlerts(
            alerts = listOf(alert),
            criteria = criteria(maxDistanceKm = 5),
            origin = null
        ) as WidgetAlertFilter.Result.Filtered

        assertEquals(listOf(alert), result.alerts)
        assertEquals(false, result.distanceFilterApplied)
    }

    @Test
    fun snapshotStore_usesNewestRoomAlertsWhenDistanceLocationIsMissing() {
        val newAlert = sampleAlert("Needs Location", area = "North", endTime = "2000")
        val expired = sampleAlert("Expired", area = "North", endTime = "1000")
        val dismissed = sampleAlert("Dismissed", area = "North", endTime = "2000")
        val wrongArea = sampleAlert("Wrong Area", area = "South", endTime = "2000")

        val result = WidgetAlertSnapshotStore.resolve(
            alerts = listOf(newAlert, expired, dismissed, wrongArea),
            criteria = criteria(
                dismissedAlertIds = setOf(dismissed.uniqueId),
                selectedArea = "North",
                maxDistanceKm = 5
            ),
            origin = null
        )

        assertEquals(listOf(newAlert), result.alerts)
        assertEquals(false, result.distanceFilterApplied)
    }

    @Test
    fun snapshotStore_returnsNewAlertAfterProcessSnapshotIsCleared() {
        val newAlert = sampleAlert("Needs Location")
        WidgetAlertSnapshotStore.clearForTesting()
        val result = WidgetAlertSnapshotStore.resolve(
            alerts = listOf(newAlert),
            criteria = criteria(maxDistanceKm = 5),
            origin = null
        )

        assertEquals(listOf(newAlert), result.alerts)
        assertEquals(false, result.distanceFilterApplied)
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

    @Test
    fun semanticHundoAndNundoFiltersMatchIvDerivedSpawnAlerts() {
        val hundo = sampleAlert("Perfect", type = listOf("Spawn"), iv = "15/15/15")
        val nundo = sampleAlert("Zero", type = listOf("Spawn"), iv = "0/0/0")

        val hundos = WidgetAlertFilter.filterAlerts(
            alerts = listOf(hundo, nundo),
            criteria = criteria(widgetFilterTypes = setOf("Hundo")),
            origin = null
        ) as WidgetAlertFilter.Result.Filtered
        val nundos = WidgetAlertFilter.filterAlerts(
            alerts = listOf(hundo, nundo),
            criteria = criteria(widgetFilterTypes = setOf("Nundo")),
            origin = null
        ) as WidgetAlertFilter.Result.Filtered

        assertEquals(listOf(hundo), hundos.alerts)
        assertEquals(listOf(nundo), nundos.alerts)
    }

    @Test
    fun rareAndWeatherFiltersMatchSemanticCategories() {
        val rare = sampleAlert("Rare", type = listOf("Rare"))
        val weather = sampleAlert("Weather", type = listOf("Weather Change"))

        val rareResult = WidgetAlertFilter.filterAlerts(
            alerts = listOf(rare, weather),
            criteria = criteria(widgetFilterTypes = setOf("Rare")),
            origin = null
        ) as WidgetAlertFilter.Result.Filtered
        val weatherResult = WidgetAlertFilter.filterAlerts(
            alerts = listOf(rare, weather),
            criteria = criteria(widgetFilterTypes = setOf("Weather")),
            origin = null
        ) as WidgetAlertFilter.Result.Filtered

        assertEquals(listOf(rare), rareResult.alerts)
        assertEquals(listOf(weather), weatherResult.alerts)
    }

    @Test
    fun renderSnapshotIsGenerationTaggedAndDefensivelyCopied() {
        val source = mutableListOf(sampleAlert("First"), sampleAlert("Second"))
        val first = WidgetAlertSnapshotStore.publishRenderSnapshot(
            7,
            source,
            null,
            distanceUnavailable = true
        )
        source += sampleAlert("Changed after publish")
        val restored = WidgetAlertSnapshotStore.currentRenderSnapshot(7)
        val second = WidgetAlertSnapshotStore.publishRenderSnapshot(
            7,
            listOf(sampleAlert("Replacement")),
            null
        )

        assertEquals(listOf("First", "Second"), restored?.alerts?.map { it.name })
        assertEquals(true, restored?.distanceUnavailable)
        assertTrue(second.generation > first.generation)
        assertEquals(
            null,
            WidgetAlertSnapshotStore.currentRenderSnapshot(7, expectedGeneration = first.generation)
        )
    }

    @Test
    fun expirationPublishesEmptySnapshotAfterOneActiveAlert() {
        val alert = sampleAlert("Expiring", endTime = "2000")
        val active = WidgetAlertSnapshotStore.resolve(
            alerts = listOf(alert),
            criteria = criteria(nowMillis = 1_999L),
            origin = null
        )
        WidgetAlertSnapshotStore.publishRenderSnapshot(8, active.alerts, null)

        val expired = WidgetAlertSnapshotStore.resolve(
            alerts = listOf(alert),
            criteria = criteria(nowMillis = 2_000L),
            origin = null
        )
        WidgetAlertSnapshotStore.publishRenderSnapshot(8, expired.alerts, null)

        assertTrue(WidgetAlertSnapshotStore.currentRenderSnapshot(8)?.alerts.orEmpty().isEmpty())
    }

    private fun criteria(
        dismissedAlertIds: Set<String> = emptySet(),
        selectedArea: String = "All",
        maxDistanceKm: Int = 0,
        widgetFilterTypes: Set<String> = emptySet(),
        nowMillis: Long = 1_000L
    ) = WidgetAlertFilter.Criteria(
        dismissedAlertIds = dismissedAlertIds,
        selectedArea = selectedArea,
        maxDistanceKm = maxDistanceKm,
        widgetFilterTypes = widgetFilterTypes,
        nowMillis = nowMillis
    )

    private fun sampleAlert(
        name: String,
        area: String? = null,
        endTime: String = "2000",
        type: List<String> = listOf("Quest"),
        iv: String? = null
    ) = PokemonAlert(
        name = name,
        endTime = endTime,
        latitude = 49.74,
        longitude = 8.62,
        type = type,
        iv = iv,
        ivAttack = iv?.substringBefore('/')?.toIntOrNull(),
        ivDefense = iv?.split('/')?.getOrNull(1)?.toIntOrNull(),
        ivStamina = iv?.substringAfterLast('/')?.toIntOrNull(),
        area = area
    )

    private companion object {
        val origin = WidgetAlertFilter.Origin(latitude = 49.74, longitude = 8.62)
    }
}
