package com.example.pokemonalertsv2

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class MainNavigationTest {

    @Test
    fun rootTabIndicesResolveAlertsAndMapDestinations() {
        assertEquals(ALERTS_TAB_INDEX, rootTabIndexOrNull(ALERTS_TAB_INDEX))
        assertEquals(MAP_TAB_INDEX, rootTabIndexOrNull(MAP_TAB_INDEX))
        assertEquals(EVENTS_TAB_INDEX, rootTabIndexOrNull(EVENTS_TAB_INDEX))
        assertEquals(SETTINGS_TAB_INDEX, rootTabIndexOrNull(SETTINGS_TAB_INDEX))
        assertEquals(TOOLS_TAB_INDEX, rootTabIndexOrNull(TOOLS_TAB_INDEX))
        assertEquals(null, rootTabIndexOrNull(-1))
        assertEquals(null, rootTabIndexOrNull(TOOLS_TAB_INDEX + 1))
    }

    @Test
    fun settingsKeepsTheTabItWasOpenedFromHighlighted() {
        assertEquals(true, isNavTabSelected(MAP_TAB_INDEX, MAP_TAB_INDEX, ALERTS_TAB_INDEX))
        assertEquals(true, isNavTabSelected(EVENTS_TAB_INDEX, SETTINGS_TAB_INDEX, EVENTS_TAB_INDEX))
        assertEquals(false, isNavTabSelected(ALERTS_TAB_INDEX, SETTINGS_TAB_INDEX, EVENTS_TAB_INDEX))
        assertEquals(false, isNavTabSelected(TOOLS_TAB_INDEX, ALERTS_TAB_INDEX, EVENTS_TAB_INDEX))
    }

    @Test
    fun historyScreenLivesOnTheAlertsTab() {
        // Widgets and notifications already on the device still send History's old id.
        assertEquals(HISTORY_SCREEN_INDEX, rootTabIndexOrNull(HISTORY_SCREEN_INDEX))
        assertEquals(ALERTS_TAB_INDEX, tabForScreen(HISTORY_SCREEN_INDEX))
        assertEquals(ALERTS_TAB_INDEX, tabForScreen(ALERTS_TAB_INDEX))
        assertEquals(MAP_TAB_INDEX, tabForScreen(MAP_TAB_INDEX))
        assertEquals(SETTINGS_TAB_INDEX, tabForScreen(SETTINGS_TAB_INDEX))
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
