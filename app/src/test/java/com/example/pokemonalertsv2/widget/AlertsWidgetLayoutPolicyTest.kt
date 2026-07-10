package com.example.pokemonalertsv2.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertsWidgetLayoutPolicyTest {

    @Test
    fun unknownWidgetSizeUsesSafeCompactLayout() {
        assertTrue(shouldUseCompactWidgetLayout(minWidthDp = 0, minHeightDp = 0))
    }

    @Test
    fun twoByTwoAndShortWideWidgetsUseCompactLayout() {
        assertTrue(shouldUseCompactWidgetLayout(minWidthDp = 180, minHeightDp = 110))
        assertTrue(shouldUseCompactWidgetLayout(minWidthDp = 340, minHeightDp = 150))
        assertTrue(shouldUseCompactWidgetLayout(minWidthDp = 340, minHeightDp = 180))
    }

    @Test
    fun sufficientlyLargeWidgetUsesFullAlertList() {
        assertFalse(shouldUseCompactWidgetLayout(minWidthDp = 340, minHeightDp = 220))
    }
}
