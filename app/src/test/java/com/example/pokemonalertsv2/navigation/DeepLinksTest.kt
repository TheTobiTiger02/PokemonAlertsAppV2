package com.example.pokemonalertsv2.navigation

import com.example.pokemonalertsv2.ui.settings.SettingsDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinksTest {

    @Test
    fun `root hosts map to their tab indices`() {
        assertEquals(DeepLinkTarget.RootTab(0), parseDeepLink("pokemonalerts://alerts"))
        assertEquals(DeepLinkTarget.RootTab(1), parseDeepLink("pokemonalerts://history"))
        assertEquals(DeepLinkTarget.RootTab(2), parseDeepLink("pokemonalerts://map"))
        assertEquals(DeepLinkTarget.RootTab(3), parseDeepLink("pokemonalerts://settings"))
    }

    @Test
    fun `scheme and host are case insensitive`() {
        assertEquals(DeepLinkTarget.RootTab(2), parseDeepLink("PokemonAlerts://MAP"))
    }

    @Test
    fun `trailing slash and query are ignored`() {
        assertEquals(DeepLinkTarget.RootTab(2), parseDeepLink("pokemonalerts://map/"))
        assertEquals(DeepLinkTarget.RootTab(2), parseDeepLink("pokemonalerts://map?from=widget"))
        assertEquals(DeepLinkTarget.RootTab(2), parseDeepLink("pokemonalerts://map#top"))
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
            DeepLinkTarget.RootTab(3),
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
