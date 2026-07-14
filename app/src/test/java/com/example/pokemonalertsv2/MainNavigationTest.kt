package com.example.pokemonalertsv2

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class MainNavigationTest {

    @Test
    fun rootTabIndicesResolveAlertsAndMapDestinations() {
        assertEquals(ALERTS_TAB_INDEX, rootTabIndexOrNull(ALERTS_TAB_INDEX))
        assertEquals(MAP_TAB_INDEX, rootTabIndexOrNull(MAP_TAB_INDEX))
        assertEquals(null, rootTabIndexOrNull(-1))
        assertEquals(null, rootTabIndexOrNull(4))
    }

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
