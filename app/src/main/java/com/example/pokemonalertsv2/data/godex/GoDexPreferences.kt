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
            notificationFilterEnabled = prefs[FILTER_ENABLED_KEY] ?: false,
            sessionCookies = prefs[COOKIES_KEY].orEmpty(),
            writeBackUrl = prefs[WRITE_BACK_URL_KEY].orEmpty(),
            sessionState = prefs[SESSION_STATE_KEY]
                ?.let { runCatching { GoDexSessionState.valueOf(it) }.getOrNull() }
                ?: if (prefs[COOKIES_KEY].isNullOrBlank()) {
                    GoDexSessionState.NONE
                } else {
                    GoDexSessionState.AUTHENTICATED
                },
            lastSuccessfulWriteMillis = prefs[LAST_WRITE_KEY] ?: 0L,
            lastWriteError = prefs[LAST_WRITE_ERROR_KEY]
        )
    }

    suspend fun saveSuccessfulSync(url: String, title: String, timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[URL_KEY] = url
            prefs[TITLE_KEY] = title
            prefs[LAST_SYNC_KEY] = timestamp
        }
    }

    suspend fun saveSessionCookies(cookies: String) {
        dataStore.edit { prefs ->
            prefs[COOKIES_KEY] = cookies
            if (cookies.isBlank()) {
                prefs[SESSION_STATE_KEY] = GoDexSessionState.NONE.name
                prefs.remove(LAST_WRITE_ERROR_KEY)
            }
        }
    }

    suspend fun saveAuthenticatedSession(cookies: String, writeBackUrl: String) {
        dataStore.edit { prefs ->
            prefs[COOKIES_KEY] = cookies
            prefs[WRITE_BACK_URL_KEY] = writeBackUrl
            prefs[URL_KEY] = writeBackUrl
            prefs[SESSION_STATE_KEY] = GoDexSessionState.AUTHENTICATED.name
            prefs.remove(LAST_WRITE_ERROR_KEY)
        }
    }

    suspend fun markReauthenticationRequired(message: String) {
        dataStore.edit { prefs ->
            prefs[SESSION_STATE_KEY] = GoDexSessionState.REAUTH_REQUIRED.name
            prefs[LAST_WRITE_ERROR_KEY] = message
        }
    }

    suspend fun saveWriteSuccess(timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[LAST_WRITE_KEY] = timestamp
            prefs[SESSION_STATE_KEY] = GoDexSessionState.AUTHENTICATED.name
            prefs.remove(LAST_WRITE_ERROR_KEY)
        }
    }

    suspend fun saveWriteError(message: String) {
        dataStore.edit { prefs -> prefs[LAST_WRITE_ERROR_KEY] = message }
    }

    suspend fun saveWriteBackUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[WRITE_BACK_URL_KEY] = url
            prefs[URL_KEY] = url
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
            prefs.remove(COOKIES_KEY)
            prefs.remove(WRITE_BACK_URL_KEY)
            prefs.remove(SESSION_STATE_KEY)
            prefs.remove(LAST_WRITE_KEY)
            prefs.remove(LAST_WRITE_ERROR_KEY)
        }
    }

    private companion object {
        val URL_KEY = stringPreferencesKey("godex_hundo_collection_url")
        val TITLE_KEY = stringPreferencesKey("godex_hundo_collection_title")
        val LAST_SYNC_KEY = longPreferencesKey("godex_hundo_last_successful_sync")
        val FILTER_ENABLED_KEY = booleanPreferencesKey("godex_hundo_notification_filter")
        val COOKIES_KEY = stringPreferencesKey("godex_session_cookies")
        val WRITE_BACK_URL_KEY = stringPreferencesKey("godex_write_back_url")
        val SESSION_STATE_KEY = stringPreferencesKey("godex_session_state")
        val LAST_WRITE_KEY = longPreferencesKey("godex_last_successful_write")
        val LAST_WRITE_ERROR_KEY = stringPreferencesKey("godex_last_write_error")
    }
}
