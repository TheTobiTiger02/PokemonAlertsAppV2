package com.example.pokemonalertsv2.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Which tools the user starred for the quick-access row on the Tools tab.
 *
 * Kept in the existing preferences store, so backup and restore include it without any change.
 */
class ToolPreferences(private val store: DataStore<Preferences>) {
    val pinnedTools = store.data.map { decodePins(it[PINS]) }.distinctUntilChanged()

    /** Pins the tool, or unpins it if it is already pinned; a fifth pin is ignored. */
    suspend fun togglePin(id: String) {
        require(id in TOOL_IDS)
        store.edit { prefs ->
            val pins = decodePins(prefs[PINS])
            prefs[PINS] = (if (id in pins) pins - id else (pins + id).take(MAX_PINS)).joinToString(",")
        }
    }

    companion object {
        const val MAX_PINS = 4
        private val PINS = stringPreferencesKey("pinned_tool_ids")
        val TOOL_IDS = setOf("hunt", "routes", "raids", "roster", "godex", "insights")
        internal fun decodePins(value: String?) =
            value.orEmpty().split(',').filter { it in TOOL_IDS }.distinct().take(MAX_PINS)
    }
}
