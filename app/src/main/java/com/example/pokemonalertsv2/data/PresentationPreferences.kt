package com.example.pokemonalertsv2.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class FeedLayout(val label: String) { COMPACT("Compact"), VISUAL("Visual") }

/** Presentation only; stored with existing settings so backup/restore includes it. */
class PresentationPreferences(private val store: DataStore<Preferences>) {
    val feedLayout = store.data.map { prefs ->
        FeedLayout.entries.firstOrNull { it.name == prefs[LAYOUT] } ?: FeedLayout.VISUAL
    }.distinctUntilChanged()
    val pinnedTools = store.data.map { decodePins(it[PINS]) }.distinctUntilChanged()
    suspend fun setFeedLayout(layout: FeedLayout) { store.edit { it[LAYOUT] = layout.name } }
    suspend fun togglePin(id: String) {
        require(id in TOOL_IDS)
        store.edit { prefs ->
            val pins = decodePins(prefs[PINS])
            prefs[PINS] = (if (id in pins) pins - id else (pins + id).take(4)).joinToString(",")
        }
    }
    companion object {
        private val LAYOUT = stringPreferencesKey("feed_layout")
        private val PINS = stringPreferencesKey("pinned_tool_ids")
        val TOOL_IDS = setOf("hunt", "routes", "raids", "roster", "godex", "insights")
        internal fun decodePins(value: String?) = value.orEmpty().split(',').filter { it in TOOL_IDS }.distinct().take(4)
    }
}
