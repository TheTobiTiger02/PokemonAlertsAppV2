package com.example.pokemonalertsv2.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAlertLoaderRouteGateTest {
    private val alertIds = setOf("alert-1", "alert-2", "alert-3")

    @Test
    fun freshSnapshotWithUnchangedAlertsIsReused() {
        assertTrue(
            WidgetAlertLoader.shouldReuseRouteData(
                snapshotRouteDataAtMillis = 1_000L,
                snapshotAlertIds = alertIds,
                currentAlertIds = alertIds,
                nowMillis = 1_000L + WidgetAlertLoader.ROUTE_DATA_MAX_AGE_MILLIS - 1
            )
        )
    }

    @Test
    fun snapshotOlderThanTheWindowIsRefreshed() {
        assertFalse(
            WidgetAlertLoader.shouldReuseRouteData(
                snapshotRouteDataAtMillis = 1_000L,
                snapshotAlertIds = alertIds,
                currentAlertIds = alertIds,
                nowMillis = 1_000L + WidgetAlertLoader.ROUTE_DATA_MAX_AGE_MILLIS
            )
        )
    }

    @Test
    fun changedAlertSetBypassesTheGate() {
        // A push wave landed new alerts; routes must be fetched even on a young snapshot.
        assertFalse(
            WidgetAlertLoader.shouldReuseRouteData(
                snapshotRouteDataAtMillis = 1_000L,
                snapshotAlertIds = alertIds,
                currentAlertIds = alertIds + "alert-4",
                nowMillis = 1_000L + 1_000L
            )
        )
    }

    @Test
    fun removedAlertAlsoBypassesTheGate() {
        assertFalse(
            WidgetAlertLoader.shouldReuseRouteData(
                snapshotRouteDataAtMillis = 1_000L,
                snapshotAlertIds = alertIds,
                currentAlertIds = alertIds - "alert-2",
                nowMillis = 1_000L + 1_000L
            )
        )
    }

    @Test
    fun missingPreviousDataIsNeverReused() {
        // Default stamp 0 and no ids: the very first tick after process death.
        assertFalse(
            WidgetAlertLoader.shouldReuseRouteData(
                snapshotRouteDataAtMillis = 0L,
                snapshotAlertIds = emptySet(),
                currentAlertIds = alertIds,
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun aCustomWindowShrinksAndGrowsTheGate() {
        assertTrue(
            WidgetAlertLoader.shouldReuseRouteData(
                snapshotRouteDataAtMillis = 1_000L,
                snapshotAlertIds = alertIds,
                currentAlertIds = alertIds,
                nowMillis = 2_500L,
                maxAgeMillis = 2_000L
            )
        )
        assertFalse(
            WidgetAlertLoader.shouldReuseRouteData(
                snapshotRouteDataAtMillis = 1_000L,
                snapshotAlertIds = alertIds,
                currentAlertIds = alertIds,
                nowMillis = 3_000L,
                maxAgeMillis = 2_000L
            )
        )
    }
}
