package com.example.pokemonalertsv2.widget

import com.example.pokemonalertsv2.data.MAX_FILTER_DISTANCE_METERS
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetConfigurationTest {
    @Test
    fun fixedDistanceIsRepresentedIndependentlyFromAppLimit() {
        val configuration = WidgetConfiguration(
            selectedAlertTypes = setOf("Raid"),
            priority = WidgetPriority.NEAREST,
            distance = WidgetDistanceMode.Fixed(12_000)
        )

        assertEquals(12_000, (configuration.distance as WidgetDistanceMode.Fixed).meters)
        assertEquals(WidgetPriority.NEAREST, configuration.priority)
    }

    @Test
    fun storedMeterEntriesParseDirectly() {
        assertEquals(WidgetDistanceMode.InheritApp, parseStoredDistance(null))
        assertEquals(WidgetDistanceMode.InheritApp, parseStoredDistance("INHERIT"))
        assertEquals(WidgetDistanceMode.Unlimited, parseStoredDistance("UNLIMITED"))
        assertEquals(WidgetDistanceMode.Fixed(500), parseStoredDistance("FIXEDM:500"))
        assertEquals(WidgetDistanceMode.Fixed(MAX_FILTER_DISTANCE_METERS), parseStoredDistance("FIXEDM:50000"))
    }

    @Test
    fun legacyKilometerEntriesRescaleToMeters() {
        assertEquals(WidgetDistanceMode.Fixed(5_000), parseStoredDistance("FIXED:5"))
        assertEquals(WidgetDistanceMode.Fixed(1_000), parseStoredDistance("FIXED:1"))
        assertEquals(WidgetDistanceMode.Fixed(MAX_FILTER_DISTANCE_METERS), parseStoredDistance("FIXED:50"))
        assertEquals(WidgetDistanceMode.InheritApp, parseStoredDistance("FIXED:not-a-number"))
    }
}
