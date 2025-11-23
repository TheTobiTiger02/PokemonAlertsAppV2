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
private val FAVORITE_ALERTS_KEY = stringSetPreferencesKey("favorite_alert_ids")
private val THEME_MODE_KEY = androidx.datastore.preferences.core.intPreferencesKey("theme_mode")
private val USE_IMPERIAL_UNITS_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("use_imperial_units")
private val ONBOARDING_COMPLETED_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("onboarding_completed")

val Context.alertPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = DATA_STORE_NAME)

interface AlertPreferencesStore {
    val seenAlertIds: Flow<Set<String>>
    suspend fun getSeenAlertIds(): Set<String>
    suspend fun updateSeenAlertIds(alertIds: Set<String>)

    val favoriteAlertIds: Flow<Set<String>>
    suspend fun getFavoriteAlertIds(): Set<String>
    suspend fun updateFavoriteAlertIds(alertIds: Set<String>)

    val themeMode: Flow<Int> // 0 = System, 1 = Light, 2 = Dark
    suspend fun updateThemeMode(mode: Int)

    val useImperialUnits: Flow<Boolean>
    suspend fun updateUseImperialUnits(useImperial: Boolean)

    val onboardingCompleted: Flow<Boolean>
    suspend fun setOnboardingCompleted(completed: Boolean)
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

    override val favoriteAlertIds: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[FAVORITE_ALERTS_KEY] ?: emptySet()
    }

    override suspend fun getFavoriteAlertIds(): Set<String> = favoriteAlertIds.first()

    override suspend fun updateFavoriteAlertIds(alertIds: Set<String>) {
        dataStore.edit { prefs ->
            prefs[FAVORITE_ALERTS_KEY] = alertIds
        }
    }

    override val themeMode: Flow<Int> = dataStore.data.map { preferences ->
        preferences[THEME_MODE_KEY] ?: 0
    }

    override suspend fun updateThemeMode(mode: Int) {
        dataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode
        }
    }

    override val useImperialUnits: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[USE_IMPERIAL_UNITS_KEY] ?: false
    }

    override suspend fun updateUseImperialUnits(useImperial: Boolean) {
        dataStore.edit { prefs ->
            prefs[USE_IMPERIAL_UNITS_KEY] = useImperial
        }
    }

    override val onboardingCompleted: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED_KEY] ?: false
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { prefs ->
            prefs[ONBOARDING_COMPLETED_KEY] = completed
        }
    }

    companion object {
        private const val MAX_SEEN_ALERTS = 200
    }
}
