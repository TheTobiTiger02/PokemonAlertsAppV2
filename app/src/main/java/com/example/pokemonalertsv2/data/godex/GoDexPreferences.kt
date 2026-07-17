package com.example.pokemonalertsv2.data.godex

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GoDexPreferences(private val dataStore: DataStore<Preferences>) {
    val config: Flow<GoDexConfig> = dataStore.data.map { prefs ->
        GoDexConfig(
            url = prefs[URL_KEY].orEmpty(),
            collectionTitle = prefs[TITLE_KEY].orEmpty(),
            lastSuccessfulSyncMillis = prefs[LAST_SYNC_KEY] ?: 0L,
            notificationFilterEnabled = prefs[FILTER_ENABLED_KEY] ?: false
        )
    }

    suspend fun saveSuccessfulSync(url: String, title: String, timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[URL_KEY] = url
            prefs[TITLE_KEY] = title
            prefs[LAST_SYNC_KEY] = timestamp
        }
    }

    suspend fun setNotificationFilterEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[FILTER_ENABLED_KEY] = enabled }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(URL_KEY)
            prefs.remove(TITLE_KEY)
            prefs.remove(LAST_SYNC_KEY)
            prefs.remove(FILTER_ENABLED_KEY)
        }
    }

    private companion object {
        val URL_KEY = stringPreferencesKey("godex_hundo_collection_url")
        val TITLE_KEY = stringPreferencesKey("godex_hundo_collection_title")
        val LAST_SYNC_KEY = longPreferencesKey("godex_hundo_last_successful_sync")
        val FILTER_ENABLED_KEY = booleanPreferencesKey("godex_hundo_notification_filter")
    }
}
