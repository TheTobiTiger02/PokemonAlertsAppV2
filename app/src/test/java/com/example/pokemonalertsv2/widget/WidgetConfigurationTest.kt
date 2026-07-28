package com.example.pokemonalertsv2.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetConfigurationTest {
    @Test
    fun fixedDistanceIsRepresentedIndependentlyFromAppLimit() {
        val configuration = WidgetConfiguration(
            selectedAlertTypes = setOf("Raid"),
            priority = WidgetPriority.NEAREST,
            distance = WidgetDistanceMode.Fixed(12)
        )

        assertEquals(12, (configuration.distance as WidgetDistanceMode.Fixed).kilometers)
        assertEquals(WidgetPriority.NEAREST, configuration.priority)
    }
}
