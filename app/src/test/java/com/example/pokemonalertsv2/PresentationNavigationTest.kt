package com.example.pokemonalertsv2

import com.example.pokemonalertsv2.data.PresentationPreferences
import com.example.pokemonalertsv2.ui.tools.findTools
import org.junit.Assert.*
import org.junit.Test

class PresentationNavigationTest {
    @Test fun legacyEntryPointsKeepTheirDestinations() {
        assertEquals(listOf(AppDestination.ALERTS, AppDestination.HISTORY, AppDestination.MAP,
            AppDestination.EVENTS, AppDestination.SETTINGS), (0..4).map(AppDestination::fromLegacy))
        assertNull(AppDestination.fromLegacy(5))
        assertNull(AppDestination.fromLegacy(-1))
        assertEquals(AppDestination.ALERTS, AppDestination.HISTORY.root)
    }

    @Test fun restoredPinsDropUnknownDuplicatesAndOverflow() {
        assertEquals(listOf("hunt", "routes", "raids", "roster"),
            PresentationPreferences.decodePins("unknown,hunt,hunt,routes,raids,roster,godex"))
        assertTrue(PresentationPreferences.decodePins(null).isEmpty())
    }

    @Test fun familiarSearchTermsReachTheExactTools() {
        assertEquals(listOf("raids"), findTools("Pokebattler").map { it.id })
        assertEquals(listOf("roster"), findTools("csv").map { it.id })
        assertEquals(listOf("appearance"), findTools("dark compact").map { it.id })
        assertEquals(listOf("hunt"), findTools("battery saver").map { it.id })
        assertEquals(listOf("permissions"), findTools("gps blocked").map { it.id })
        assertTrue(findTools("nonexistent destination").isEmpty())
    }
}
