package com.example.pokemonalertsv2

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class MainNavigationTest {

    @Test
    fun navigationLayoutModeUsesBottomBarOnCompactAndRailFromMediumWidths() {
        assertEquals(
            NavigationLayoutMode.BOTTOM_BAR,
            navigationLayoutModeForWidth(360.dp)
        )
        assertEquals(
            NavigationLayoutMode.RAIL,
            navigationLayoutModeForWidth(600.dp)
        )
        assertEquals(
            NavigationLayoutMode.RAIL,
            navigationLayoutModeForWidth(840.dp)
        )
    }
}
