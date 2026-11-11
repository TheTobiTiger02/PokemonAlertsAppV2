package com.example.pokemonalertsv2.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    @Test
    fun findsByTitleAndKeyword() {
        assertEquals(SettingsDestination.NOTIFICATIONS, searchSettings("quiet").first().destination)
        assertEquals(SettingsDestination.APPEARANCE_BEHAVIOR, searchSettings("dark mode").first().destination)
        assertEquals(SettingsDestination.MAP_TRACKING, searchSettings("cluster").first().destination)
    }

    @Test
    fun ignoresCaseAndAccents() {
        assertEquals("Poké Genie import", searchSettings("POKE GENIE").first().title)
    }

    @Test
    fun everyWordMustMatchAndBlankFindsNothing() {
        assertTrue(searchSettings("quiet unicorn").isEmpty())
        assertTrue(searchSettings("   ").isEmpty())
    }

    @Test
    fun titleMatchesComeBeforeKeywordMatches() {
        // "battery" is a title word for one entry and only a keyword elsewhere.
        assertEquals("Battery optimisation", searchSettings("battery").first().title)
    }
}
