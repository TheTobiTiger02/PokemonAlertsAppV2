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
private val SELECTED_AREA_KEY = androidx.datastore.preferences.core.stringPreferencesKey("selected_area")
private val MAX_DISTANCE_KEY = androidx.datastore.preferences.core.intPreferencesKey("max_distance")
private val SNOOZE_DURATION_KEY = androidx.datastore.preferences.core.intPreferencesKey("snooze_duration")

// Sub-type filtering keys - Sets of excluded types (if a type is in the set, it's filtered out)
private val EXCLUDED_HUNDO_TYPES_KEY = stringSetPreferencesKey("excluded_hundo_types")
private val EXCLUDED_NUNDO_TYPES_KEY = stringSetPreferencesKey("excluded_nundo_types")
private val EXCLUDED_PVP_TYPES_KEY = stringSetPreferencesKey("excluded_pvp_types")
private val EXCLUDED_SPAWN_TYPES_KEY = stringSetPreferencesKey("excluded_spawn_types")
private val EXCLUDED_ROCKET_TYPES_KEY = stringSetPreferencesKey("excluded_rocket_types")
private val EXCLUDED_RAID_TIERS_KEY = stringSetPreferencesKey("excluded_raid_tiers")
private val DISMISSED_ALERTS_KEY = stringSetPreferencesKey("dismissed_alert_ids")

// Pokemon species filtering - Sets of allowed pokemon species explicitly manually selected (empty = allow all)
private val ALLOWED_HUNDO_SPECIES_KEY = stringSetPreferencesKey("allowed_hundo_species")
private val ALLOWED_NUNDO_SPECIES_KEY = stringSetPreferencesKey("allowed_nundo_species")
private val ALLOWED_PVP_SPECIES_KEY = stringSetPreferencesKey("allowed_pvp_species")
private val ALLOWED_SPAWN_SPECIES_KEY = stringSetPreferencesKey("allowed_spawn_species")

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
    
    val selectedArea: Flow<String>
    suspend fun updateSelectedArea(area: String)
    
    val maxDistance: Flow<Int>
    suspend fun updateMaxDistance(distance: Int)

    val snoozeDuration: Flow<Int>
    suspend fun updateSnoozeDuration(minutes: Int)
    
    // Sub-type filtering - Sets of excluded types
    val excludedHundoTypes: Flow<Set<String>>
    suspend fun updateExcludedHundoTypes(types: Set<String>)
    
    val excludedNundoTypes: Flow<Set<String>>
    suspend fun updateExcludedNundoTypes(types: Set<String>)
    
    val excludedPvpTypes: Flow<Set<String>>
    suspend fun updateExcludedPvpTypes(types: Set<String>)
    
    val excludedSpawnTypes: Flow<Set<String>>
    suspend fun updateExcludedSpawnTypes(types: Set<String>)
    
    val excludedRocketTypes: Flow<Set<String>>
    suspend fun updateExcludedRocketTypes(types: Set<String>)
    
    val excludedRaidTiers: Flow<Set<String>>
    suspend fun updateExcludedRaidTiers(types: Set<String>)
    
    // Dismissed alerts
    val dismissedAlertIds: Flow<Set<String>>
    suspend fun addDismissedAlert(alertId: String)
    suspend fun removeDismissedAlert(alertId: String)
    suspend fun clearDismissedAlerts()

    val allowedHundoSpecies: Flow<Set<String>>
    suspend fun updateAllowedHundoSpecies(species: Set<String>)

    val allowedNundoSpecies: Flow<Set<String>>
    suspend fun updateAllowedNundoSpecies(species: Set<String>)

    val allowedPvpSpecies: Flow<Set<String>>
    suspend fun updateAllowedPvpSpecies(species: Set<String>)

    val allowedSpawnSpecies: Flow<Set<String>>
    suspend fun updateAllowedSpawnSpecies(species: Set<String>)
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
    
    override val selectedArea: Flow<String> = dataStore.data.map { preferences ->
        preferences[SELECTED_AREA_KEY] ?: "All"
    }
    
    override suspend fun updateSelectedArea(area: String) {
        dataStore.edit { prefs ->
            prefs[SELECTED_AREA_KEY] = area
        }
    }
    
    override val maxDistance: Flow<Int> = dataStore.data.map { preferences ->
        preferences[MAX_DISTANCE_KEY] ?: 0
    }
    
    override suspend fun updateMaxDistance(distance: Int) {
        dataStore.edit { prefs ->
            prefs[MAX_DISTANCE_KEY] = distance
        }
    }

    override val snoozeDuration: Flow<Int> = dataStore.data.map { preferences ->
        preferences[SNOOZE_DURATION_KEY] ?: 10
    }

    override suspend fun updateSnoozeDuration(minutes: Int) {
        dataStore.edit { prefs ->
            prefs[SNOOZE_DURATION_KEY] = minutes
        }
    }
    
    // Sub-type filtering implementations
    override val excludedHundoTypes: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[EXCLUDED_HUNDO_TYPES_KEY] ?: emptySet()
    }
    
    override suspend fun updateExcludedHundoTypes(types: Set<String>) {
        dataStore.edit { prefs ->
            prefs[EXCLUDED_HUNDO_TYPES_KEY] = types
        }
    }
    
    override val excludedNundoTypes: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[EXCLUDED_NUNDO_TYPES_KEY] ?: emptySet()
    }
    
    override suspend fun updateExcludedNundoTypes(types: Set<String>) {
        dataStore.edit { prefs ->
            prefs[EXCLUDED_NUNDO_TYPES_KEY] = types
        }
    }
    
    override val excludedPvpTypes: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[EXCLUDED_PVP_TYPES_KEY] ?: emptySet()
    }
    
    override suspend fun updateExcludedPvpTypes(types: Set<String>) {
        dataStore.edit { prefs ->
            prefs[EXCLUDED_PVP_TYPES_KEY] = types
        }
    }
    
    override val excludedSpawnTypes: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[EXCLUDED_SPAWN_TYPES_KEY] ?: emptySet()
    }
    
    override suspend fun updateExcludedSpawnTypes(types: Set<String>) {
        dataStore.edit { prefs ->
            prefs[EXCLUDED_SPAWN_TYPES_KEY] = types
        }
    }
    
    override val excludedRocketTypes: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[EXCLUDED_ROCKET_TYPES_KEY] ?: emptySet()
    }
    
    override suspend fun updateExcludedRocketTypes(types: Set<String>) {
        dataStore.edit { prefs ->
            prefs[EXCLUDED_ROCKET_TYPES_KEY] = types
        }
    }
    
    override val excludedRaidTiers: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[EXCLUDED_RAID_TIERS_KEY] ?: emptySet()
    }
    
    override suspend fun updateExcludedRaidTiers(types: Set<String>) {
        dataStore.edit { prefs ->
            prefs[EXCLUDED_RAID_TIERS_KEY] = types
        }
    }
    
    // Dismissed alerts implementations
    override val dismissedAlertIds: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[DISMISSED_ALERTS_KEY] ?: emptySet()
    }
    
    override suspend fun addDismissedAlert(alertId: String) {
        dataStore.edit { prefs ->
            val current = prefs[DISMISSED_ALERTS_KEY] ?: emptySet()
            // Limit to prevent unbounded growth, keep last 500
            val updated = if (current.size >= 500) {
                current.drop(current.size - 499).toSet() + alertId
            } else {
                current + alertId
            }
            prefs[DISMISSED_ALERTS_KEY] = updated
        }
    }
    
    override suspend fun removeDismissedAlert(alertId: String) {
        dataStore.edit { prefs ->
            val current = prefs[DISMISSED_ALERTS_KEY] ?: emptySet()
            prefs[DISMISSED_ALERTS_KEY] = current - alertId
        }
    }
    
    override suspend fun clearDismissedAlerts() {
        dataStore.edit { prefs ->
            prefs[DISMISSED_ALERTS_KEY] = emptySet()
        }
    }

    override val allowedHundoSpecies: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[ALLOWED_HUNDO_SPECIES_KEY] ?: emptySet()
    }
    
    override suspend fun updateAllowedHundoSpecies(species: Set<String>) {
        dataStore.edit { prefs -> prefs[ALLOWED_HUNDO_SPECIES_KEY] = species }
    }

    override val allowedNundoSpecies: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[ALLOWED_NUNDO_SPECIES_KEY] ?: emptySet()
    }
    
    override suspend fun updateAllowedNundoSpecies(species: Set<String>) {
        dataStore.edit { prefs -> prefs[ALLOWED_NUNDO_SPECIES_KEY] = species }
    }

    override val allowedPvpSpecies: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[ALLOWED_PVP_SPECIES_KEY] ?: emptySet()
    }
    
    override suspend fun updateAllowedPvpSpecies(species: Set<String>) {
        dataStore.edit { prefs -> prefs[ALLOWED_PVP_SPECIES_KEY] = species }
    }

    override val allowedSpawnSpecies: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[ALLOWED_SPAWN_SPECIES_KEY] ?: emptySet()
    }
    
    override suspend fun updateAllowedSpawnSpecies(species: Set<String>) {
        dataStore.edit { prefs -> prefs[ALLOWED_SPAWN_SPECIES_KEY] = species }
    }

    companion object {
        private const val MAX_SEEN_ALERTS = 200
    }
}

