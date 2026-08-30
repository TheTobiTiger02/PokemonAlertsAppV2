package com.example.pokemonalertsv2.data.backup

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {

    private fun sample() = mutablePreferencesOf(
        booleanPreferencesKey("notifications_enabled") to true,
        intPreferencesKey("max_distance") to 12,
        longPreferencesKey("silence_until") to 1_700_000_000_000L,
        stringPreferencesKey("selected_area") to "Alsbach",
        stringSetPreferencesKey("allowed_hundo_species") to setOf("Mewtwo", "Rayquaza")
    )

    @Test
    fun `every supported type survives a round trip`() {
        val text = SettingsBackup.export(sample(), exportedAtMillis = 1L)
        val backup = SettingsBackup.parse(text).getOrThrow()

        val restored = mutablePreferencesOf()
        val applied = SettingsBackup.apply(backup, restored)

        assertEquals(5, applied)
        assertEquals(true, restored[booleanPreferencesKey("notifications_enabled")])
        assertEquals(12, restored[intPreferencesKey("max_distance")])
        assertEquals(1_700_000_000_000L, restored[longPreferencesKey("silence_until")])
        assertEquals("Alsbach", restored[stringPreferencesKey("selected_area")])
        assertEquals(
            setOf("Mewtwo", "Rayquaza"),
            restored[stringSetPreferencesKey("allowed_hundo_species")]
        )
    }

    @Test
    fun `session credentials never reach the exported file`() {
        val prefs = sample().apply {
            this[stringPreferencesKey("godex_session_cookies")] = "sessionid=super-secret"
            this[stringPreferencesKey("godex_session_state")] = "state-token"
            this[stringPreferencesKey("godex_write_back_url")] = "https://example.test/write"
        }

        val text = SettingsBackup.export(prefs, exportedAtMillis = 1L)

        assertFalse("the cookie value must not appear", text.contains("super-secret"))
        assertFalse(text.contains("godex_session_cookies"))
        assertFalse(text.contains("godex_session_state"))
        assertFalse(text.contains("godex_write_back_url"))
        // The non-secret GoDex settings are still worth backing up.
        assertTrue(SettingsBackup.parse(text).getOrThrow().entries.containsKey("selected_area"))
    }

    @Test
    fun `an excluded key in a hand edited file is still not imported`() {
        val backup = SettingsBackup.Backup(
            exportedAtMillis = 1L,
            entries = mapOf(
                "godex_session_cookies" to SettingsBackup.Entry("string", "sessionid=leaked"),
                "selected_area" to SettingsBackup.Entry("string", "Alsbach")
            )
        )

        val restored = mutablePreferencesOf()
        val applied = SettingsBackup.apply(backup, restored)

        assertEquals(1, applied)
        assertNull(restored[stringPreferencesKey("godex_session_cookies")])
        assertEquals("Alsbach", restored[stringPreferencesKey("selected_area")])
    }

    @Test
    fun `one unusable entry does not cost the user the rest`() {
        val backup = SettingsBackup.Backup(
            exportedAtMillis = 1L,
            entries = mapOf(
                "broken_type" to SettingsBackup.Entry("colour", "blue"),
                "bad_number" to SettingsBackup.Entry("int", "not-a-number"),
                "selected_area" to SettingsBackup.Entry("string", "Alsbach")
            )
        )

        val restored = mutablePreferencesOf()
        assertEquals(1, SettingsBackup.apply(backup, restored))
        assertEquals("Alsbach", restored[stringPreferencesKey("selected_area")])
    }

    @Test
    fun `a newer format version is refused rather than half understood`() {
        val text = """{"version":99,"exportedAtMillis":1,"entries":{}}"""
        assertTrue(SettingsBackup.parse(text).isFailure)
    }

    @Test
    fun `junk is refused`() {
        assertTrue(SettingsBackup.parse("not json at all").isFailure)
        assertTrue(SettingsBackup.parse("").isFailure)
    }

    @Test
    fun `the suggested file name is dated and json`() {
        val name = SettingsBackupRepository.suggestedFileName(0L)
        assertTrue(name.startsWith("pokemon-alerts-settings-"))
        assertTrue(name.endsWith(".json"))
    }
}
