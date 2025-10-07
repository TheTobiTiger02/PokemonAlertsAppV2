package com.example.pokemonalertsv2.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private const val DATA_STORE_NAME = "pokemon_alerts_preferences"
private val SEEN_ALERTS_KEY = stringSetPreferencesKey("seen_alert_ids")

val Context.alertPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = DATA_STORE_NAME)

interface AlertPreferencesStore {
    val seenAlertIds: Flow<Set<String>>
    suspend fun getSeenAlertIds(): Set<String>
    suspend fun updateSeenAlertIds(alertIds: Set<String>)
}

class AlertPreferences(private val dataStore: DataStore<Preferences>) : AlertPreferencesStore {

    override val seenAlertIds: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[SEEN_ALERTS_KEY] ?: emptySet()
    }

    override suspend fun getSeenAlertIds(): Set<String> = seenAlertIds.first()

    override suspend fun updateSeenAlertIds(alertIds: Set<String>) {
        val trimmedSet = if (alertIds.size <= MAX_SEEN_ALERTS) {
            alertIds
        } else {
            alertIds.toList().takeLast(MAX_SEEN_ALERTS).toSet()
        }
        dataStore.edit { prefs ->
            prefs[SEEN_ALERTS_KEY] = trimmedSet
        }
    }

    companion object {
        private const val MAX_SEEN_ALERTS = 200
    }
}
