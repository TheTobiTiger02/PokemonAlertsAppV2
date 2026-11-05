package com.example.pokemonalertsv2.ui

import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.ui.alerts.detailSplitPaneWidth
import com.example.pokemonalertsv2.ui.components.readableHorizontalPadding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdaptiveContentTest {

    @Test
    fun readablePaddingKeepsThePhoneGutter() {
        assertEquals(16.dp, readableHorizontalPadding(411.dp))
        assertEquals(16.dp, readableHorizontalPadding(752.dp))
    }

    @Test
    fun readablePaddingCentresAColumnOnWideScreens() {
        // A landscape tablet beside the rail: (1125 - 720) / 2.
        assertEquals(202.5.dp, readableHorizontalPadding(1125.dp))
    }

    @Test
    fun alertDetailSplitsOnlyFromTheTwoColumnBreakpoint() {
        assertNull(detailSplitPaneWidth(753.dp))
        assertEquals(840.dp * 0.45f, detailSplitPaneWidth(840.dp))
        assertEquals(560.dp, detailSplitPaneWidth(1280.dp))
    }
}
