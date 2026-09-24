package com.example.pokemonalertsv2.navigation

import com.example.pokemonalertsv2.ui.settings.SettingsDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinksTest {

    @Test
    fun `root hosts map to stable destinations`() {
        assertEquals(DeepLinkTarget.RootTab(AppNavigationRequest(AppDestination.ALERTS)), parseDeepLink("pokemonalerts://alerts"))
        assertEquals(DeepLinkTarget.RootTab(AppNavigationRequest(AppDestination.ALERTS, AlertsView.HISTORY)), parseDeepLink("pokemonalerts://history"))
        assertEquals(DeepLinkTarget.RootTab(AppNavigationRequest(AppDestination.MAP)), parseDeepLink("pokemonalerts://map"))
        assertEquals(DeepLinkTarget.RootTab(AppNavigationRequest(AppDestination.EVENTS)), parseDeepLink("pokemonalerts://events"))
        assertEquals(DeepLinkTarget.RootTab(AppNavigationRequest(AppDestination.SETTINGS)), parseDeepLink("pokemonalerts://settings"))
    }

    @Test
    fun `old numeric extras keep their original destinations`() {
        assertEquals(AppNavigationRequest(AppDestination.ALERTS), legacyNavigationRequestOrNull(0))
        assertEquals(AppNavigationRequest(AppDestination.ALERTS, AlertsView.HISTORY), legacyNavigationRequestOrNull(1))
        assertEquals(AppNavigationRequest(AppDestination.MAP), legacyNavigationRequestOrNull(2))
        assertEquals(AppNavigationRequest(AppDestination.EVENTS), legacyNavigationRequestOrNull(3))
        assertEquals(AppNavigationRequest(AppDestination.SETTINGS), legacyNavigationRequestOrNull(4))
        assertNull(legacyNavigationRequestOrNull(5))
    }

    @Test
    fun `scheme and host are case insensitive`() {
        assertEquals(DeepLinkTarget.RootTab(AppNavigationRequest(AppDestination.MAP)), parseDeepLink("PokemonAlerts://MAP"))
    }

    @Test
    fun `trailing slash and query are ignored`() {
        assertEquals(DeepLinkTarget.RootTab(AppNavigationRequest(AppDestination.MAP)), parseDeepLink("pokemonalerts://map/"))
        assertEquals(DeepLinkTarget.RootTab(AppNavigationRequest(AppDestination.MAP)), parseDeepLink("pokemonalerts://map?from=widget"))
        assertEquals(DeepLinkTarget.RootTab(AppNavigationRequest(AppDestination.MAP)), parseDeepLink("pokemonalerts://map#top"))
    }

    @Test
    fun `settings sub page resolves by destination name`() {
        assertEquals(
            DeepLinkTarget.Settings(SettingsDestination.GODEX),
            parseDeepLink("pokemonalerts://settings/godex")
        )
        assertEquals(
            DeepLinkTarget.Settings(SettingsDestination.RAID_COUNTERS),
            parseDeepLink("pokemonalerts://settings/RAID_COUNTERS")
        )
    }

    @Test
    fun `unknown settings page falls back to the settings tab rather than failing`() {
        assertEquals(
            DeepLinkTarget.RootTab(AppNavigationRequest(AppDestination.SETTINGS)),
            parseDeepLink("pokemonalerts://settings/not_a_page")
        )
    }

    @Test
    fun `alert id is taken from the rest of the path`() {
        assertEquals(DeepLinkTarget.Alert("abc123"), parseDeepLink("pokemonalerts://alert/abc123"))
    }

    @Test
    fun `an alert id containing a slash round trips`() {
        assertEquals(
            DeepLinkTarget.Alert("Mewtwo|2026-08-28/x"),
            parseDeepLink("pokemonalerts://alert/Mewtwo|2026-08-28/x")
        )
    }

    @Test
    fun `alert with no id is not a target`() {
        assertNull(parseDeepLink("pokemonalerts://alert"))
        assertNull(parseDeepLink("pokemonalerts://alert/"))
    }

    @Test
    fun `foreign schemes and junk are rejected`() {
        assertNull(parseDeepLink(null))
        assertNull(parseDeepLink(""))
        assertNull(parseDeepLink("https://example.com/map"))
        assertNull(parseDeepLink("pokemonalerts://"))
        assertNull(parseDeepLink("pokemonalerts://unknown"))
    }
}
