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
private val SORT_PREFERENCE_KEY = androidx.datastore.preferences.core.stringPreferencesKey("sort_preference")
private val NOTIFICATIONS_ENABLED_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("notifications_enabled")
private val RAIDS_NOTIFICATIONS_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("raids_notifications")
private val SPAWNS_NOTIFICATIONS_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("spawns_notifications")
private val QUESTS_NOTIFICATIONS_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("quests_notifications")
private val HUNDOS_NOTIFICATIONS_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("hundos_notifications")
private val PVP_NOTIFICATIONS_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("pvp_notifications")
private val NUNDOS_NOTIFICATIONS_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("nundos_notifications")
private val KECLEON_NOTIFICATIONS_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("kecleon_notifications")
private val ROCKET_NOTIFICATIONS_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("rocket_notifications")
private val NOTIFICATION_VIBRATE_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("notification_vibrate")
private val SILENCE_UNTIL_KEY = androidx.datastore.preferences.core.longPreferencesKey("silence_until") // Timestamp in millis when silence ends

val Context.alertPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = DATA_STORE_NAME)

enum class SortPreference {
    POSTED_TIME, TIME_REMAINING, DISTANCE, NAME
}

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
    
    val sortPreference: Flow<SortPreference>
    suspend fun updateSortPreference(preference: SortPreference)
    
    val notificationsEnabled: Flow<Boolean>
    suspend fun updateNotificationsEnabled(enabled: Boolean)
    
    val raidsNotifications: Flow<Boolean>
    suspend fun updateRaidsNotifications(enabled: Boolean)
    
    val spawnsNotifications: Flow<Boolean>
    suspend fun updateSpawnsNotifications(enabled: Boolean)
    
    val questsNotifications: Flow<Boolean>
    suspend fun updateQuestsNotifications(enabled: Boolean)
    
    val hundosNotifications: Flow<Boolean>
    suspend fun updateHundosNotifications(enabled: Boolean)
    
    val pvpNotifications: Flow<Boolean>
    suspend fun updatePvpNotifications(enabled: Boolean)
    
    val nundosNotifications: Flow<Boolean>
    suspend fun updateNundosNotifications(enabled: Boolean)
    
    val kecleonNotifications: Flow<Boolean>
    suspend fun updateKecleonNotifications(enabled: Boolean)
    
    val rocketNotifications: Flow<Boolean>
    suspend fun updateRocketNotifications(enabled: Boolean)
    
    val notificationVibrate: Flow<Boolean>
    suspend fun updateNotificationVibrate(enabled: Boolean)
    
    val silenceUntil: Flow<Long> // Timestamp in milliseconds, 0 means not silenced
    suspend fun updateSilenceUntil(timestampMillis: Long)
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
    
    override val sortPreference: Flow<SortPreference> = dataStore.data.map { preferences ->
        when (preferences[SORT_PREFERENCE_KEY]) {
            "TIME_REMAINING" -> SortPreference.TIME_REMAINING
            "DISTANCE" -> SortPreference.DISTANCE
            "NAME" -> SortPreference.NAME
            else -> SortPreference.POSTED_TIME // Default to posted time
        }
    }
    
    override suspend fun updateSortPreference(preference: SortPreference) {
        dataStore.edit { prefs ->
            prefs[SORT_PREFERENCE_KEY] = preference.name
        }
    }
    
    override val notificationsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[NOTIFICATIONS_ENABLED_KEY] ?: true
    }
    
    override suspend fun updateNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[NOTIFICATIONS_ENABLED_KEY] = enabled
        }
    }
    
    override val raidsNotifications: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[RAIDS_NOTIFICATIONS_KEY] ?: true
    }
    
    override suspend fun updateRaidsNotifications(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[RAIDS_NOTIFICATIONS_KEY] = enabled
        }
    }
    
    override val spawnsNotifications: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SPAWNS_NOTIFICATIONS_KEY] ?: true
    }
    
    override suspend fun updateSpawnsNotifications(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SPAWNS_NOTIFICATIONS_KEY] = enabled
        }
    }
    
    override val questsNotifications: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[QUESTS_NOTIFICATIONS_KEY] ?: true
    }
    
    override suspend fun updateQuestsNotifications(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[QUESTS_NOTIFICATIONS_KEY] = enabled
        }
    }
    
    override val hundosNotifications: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[HUNDOS_NOTIFICATIONS_KEY] ?: true
    }
    
    override suspend fun updateHundosNotifications(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[HUNDOS_NOTIFICATIONS_KEY] = enabled
        }
    }
    
    override val pvpNotifications: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PVP_NOTIFICATIONS_KEY] ?: true
    }
    
    override suspend fun updatePvpNotifications(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PVP_NOTIFICATIONS_KEY] = enabled
        }
    }
    
    override val nundosNotifications: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[NUNDOS_NOTIFICATIONS_KEY] ?: true
    }
    
    override suspend fun updateNundosNotifications(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[NUNDOS_NOTIFICATIONS_KEY] = enabled
        }
    }
    
    override val kecleonNotifications: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KECLEON_NOTIFICATIONS_KEY] ?: true
    }
    
    override suspend fun updateKecleonNotifications(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KECLEON_NOTIFICATIONS_KEY] = enabled
        }
    }
    
    override val rocketNotifications: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[ROCKET_NOTIFICATIONS_KEY] ?: true
    }
    
    override suspend fun updateRocketNotifications(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[ROCKET_NOTIFICATIONS_KEY] = enabled
        }
    }
    
    override val notificationVibrate: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[NOTIFICATION_VIBRATE_KEY] ?: true
    }
    
    override suspend fun updateNotificationVibrate(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[NOTIFICATION_VIBRATE_KEY] = enabled
        }
    }
    
    override val silenceUntil: Flow<Long> = dataStore.data.map { preferences ->
        preferences[SILENCE_UNTIL_KEY] ?: 0L
    }
    
    override suspend fun updateSilenceUntil(timestampMillis: Long) {
        dataStore.edit { prefs ->
            prefs[SILENCE_UNTIL_KEY] = timestampMillis
        }
    }

    companion object {
        private const val MAX_SEEN_ALERTS = 200
    }
}
