package com.example.pokemonalertsv2.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.SortPreference
import com.example.pokemonalertsv2.data.NotificationPreset
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.data.counters.RaidCounterOptions
import com.example.pokemonalertsv2.data.counters.RaidCounterPreferences
import com.example.pokemonalertsv2.data.counters.RaidCounterSettings
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.data.pokegenie.PokeGenieImportResult
import com.example.pokemonalertsv2.data.pokegenie.PokeGenieRepository
import com.example.pokemonalertsv2.ui.godex.GoDexWebSessionCookies
import com.example.pokemonalertsv2.widget.AlertsWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AlertPreferences(application.alertPreferencesDataStore)
    private val goDexRepository by lazy(LazyThreadSafetyMode.NONE) {
        GoDexRepository.getInstance(application)
    }

    private val raidCounterPreferences = RaidCounterPreferences(application.alertPreferencesDataStore)
    private val pokeGenieRepository by lazy(LazyThreadSafetyMode.NONE) {
        PokeGenieRepository.getInstance(application)
    }

    val raidCounterSettings: StateFlow<RaidCounterSettings> = raidCounterPreferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RaidCounterSettings())

    private val _pokeGenieImportStatus = MutableStateFlow<String?>(null)

    /** Result of the last CSV import, shown under the import button. */
    val pokeGenieImportStatus: StateFlow<String?> = _pokeGenieImportStatus

    fun updateRaidCounterDefaults(options: RaidCounterOptions) {
        viewModelScope.launch { raidCounterPreferences.updateDefaults(options) }
    }

    fun importPokeGenieCsv(uri: android.net.Uri) {
        viewModelScope.launch {
            _pokeGenieImportStatus.value = "Importing..."
            _pokeGenieImportStatus.value = when (val result = pokeGenieRepository.importFromUri(uri)) {
                is PokeGenieImportResult.Success -> {
                    val summary = result.summary
                    buildString {
                        append("Imported ${summary.importedCount} Pokemon")
                        if (summary.skippedCount > 0) append(", skipped ${summary.skippedCount} rows")
                        append(".")
                    }
                }
                is PokeGenieImportResult.Failure -> result.message
            }
        }
    }

    fun clearPokeGenie() {
        viewModelScope.launch {
            pokeGenieRepository.clear()
            _pokeGenieImportStatus.value = null
        }
    }

    val goDexConfig get() = goDexRepository.config
    val goDexEntries get() = goDexRepository.entries
    val goDexSyncUiState get() = goDexRepository.syncUiState
    val goDexPendingEntryKeys get() = goDexRepository.pendingEntryKeys

    val onboardingCompleted: StateFlow<Boolean?> = preferences.onboardingCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val themeMode: StateFlow<Int> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val sortPreference: StateFlow<SortPreference> = preferences.sortPreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortPreference.POSTED_TIME)
    
    val notificationsEnabled: StateFlow<Boolean> = preferences.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val raidsNotifications: StateFlow<Boolean> = preferences.raidsNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val spawnsNotifications: StateFlow<Boolean> = preferences.spawnsNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val questsNotifications: StateFlow<Boolean> = preferences.questsNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val hundosNotifications: StateFlow<Boolean> = preferences.hundosNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val pvpNotifications: StateFlow<Boolean> = preferences.pvpNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val nundosNotifications: StateFlow<Boolean> = preferences.nundosNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val kecleonNotifications: StateFlow<Boolean> = preferences.kecleonNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val rocketNotifications: StateFlow<Boolean> = preferences.rocketNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val notificationVibrate: StateFlow<Boolean> = preferences.notificationVibrate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val silenceUntil: StateFlow<Long> = preferences.silenceUntil
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
        
    val selectedArea: StateFlow<String> = preferences.selectedArea
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "All")
        
    val maxDistance: StateFlow<Int> = preferences.maxDistance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
        
    val snoozeDuration: StateFlow<Int> = preferences.snoozeDuration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)
    
    // Excluded type preferences
    val excludedHundoTypes: StateFlow<Set<String>> = preferences.excludedHundoTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    
    val excludedNundoTypes: StateFlow<Set<String>> = preferences.excludedNundoTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    
    val excludedPvpTypes: StateFlow<Set<String>> = preferences.excludedPvpTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    
    val excludedSpawnTypes: StateFlow<Set<String>> = preferences.excludedSpawnTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    
    val excludedRocketTypes: StateFlow<Set<String>> = preferences.excludedRocketTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    
    val excludedRaidTiers: StateFlow<Set<String>> = preferences.excludedRaidTiers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun completeOnboarding() {
        viewModelScope.launch {
            preferences.setOnboardingCompleted(true)
        }
    }

    fun updateThemeMode(mode: Int) {
        viewModelScope.launch {
            preferences.updateThemeMode(mode.coerceIn(0, 2))
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }

    fun updateSortPreference(preference: SortPreference) {
        viewModelScope.launch {
            preferences.updateSortPreference(preference)
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateNotificationsEnabled(enabled)
        }
    }
    
    fun updateRaidsNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateRaidsNotifications(enabled)
        }
    }
    
    fun updateSpawnsNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateSpawnsNotifications(enabled)
        }
    }
    
    fun updateQuestsNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateQuestsNotifications(enabled)
        }
    }
    
    fun updateHundosNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateHundosNotifications(enabled)
        }
    }
    
    fun updatePvpNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updatePvpNotifications(enabled)
        }
    }
    
    fun updateNundosNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateNundosNotifications(enabled)
        }
    }
    
    fun updateKecleonNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateKecleonNotifications(enabled)
        }
    }
    
    fun updateRocketNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateRocketNotifications(enabled)
        }
    }
    
    fun updateNotificationVibrate(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateNotificationVibrate(enabled)
        }
    }
    
    fun silenceNotificationsFor(durationMinutes: Int) {
        viewModelScope.launch {
            val silenceUntil = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
            preferences.updateSilenceUntil(silenceUntil)
        }
    }
    
    fun clearNotificationSilence() {
        viewModelScope.launch {
            preferences.updateSilenceUntil(0L)
        }
    }
    
    fun updateSelectedArea(area: String) {
        viewModelScope.launch {
            preferences.updateSelectedArea(area)
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }

    fun applyNotificationPreset(preset: NotificationPreset) {
        viewModelScope.launch {
            preferences.applyNotificationPreset(preset)
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }
    
    fun updateMaxDistance(distance: Int) {
        viewModelScope.launch {
            preferences.updateMaxDistance(distance)
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }
    
    fun updateSnoozeDuration(minutes: Int) {
        viewModelScope.launch {
            preferences.updateSnoozeDuration(minutes)
        }
    }

    fun connectGoDex(url: String) {
        viewModelScope.launch {
            runCatching { goDexRepository.connect(url.trim()) }
        }
    }

    fun syncGoDex() {
        viewModelScope.launch {
            runCatching { goDexRepository.syncConfigured() }
        }
    }

    fun refreshGoDexForPageEntry() {
        viewModelScope.launch {
            runCatching { goDexRepository.refreshForPageEntry() }
        }
    }

    fun updateGoDexNotificationFilter(enabled: Boolean) {
        viewModelScope.launch {
            goDexRepository.setNotificationFilterEnabled(enabled)
        }
    }

    fun setGoDexEntryCaught(entryKey: String, caught: Boolean) {
        viewModelScope.launch {
            goDexRepository.markAsCaught(entryKey, caught)
        }
    }

    fun disconnectGoDex() {
        viewModelScope.launch {
            goDexRepository.disconnect()
        }
    }

    fun clearGoDexSession() {
        viewModelScope.launch {
            goDexRepository.saveSessionCookies("")
            GoDexWebSessionCookies.clearGoDexSessionCookies()
        }
    }
    
    fun toggleExcludedHundoType(type: String) {
        viewModelScope.launch {
            val current = excludedHundoTypes.value
            val updated = if (type in current) current - type else current + type
            preferences.updateExcludedHundoTypes(updated)
        }
    }
    
    fun toggleExcludedNundoType(type: String) {
        viewModelScope.launch {
            val current = excludedNundoTypes.value
            val updated = if (type in current) current - type else current + type
            preferences.updateExcludedNundoTypes(updated)
        }
    }
    
    fun toggleExcludedPvpType(type: String) {
        viewModelScope.launch {
            val current = excludedPvpTypes.value
            val updated = if (type in current) current - type else current + type
            preferences.updateExcludedPvpTypes(updated)
        }
    }
    
    fun toggleExcludedSpawnType(type: String) {
        viewModelScope.launch {
            val current = excludedSpawnTypes.value
            val updated = if (type in current) current - type else current + type
            preferences.updateExcludedSpawnTypes(updated)
        }
    }
    
    fun toggleExcludedRocketType(type: String) {
        viewModelScope.launch {
            val current = excludedRocketTypes.value
            val updated = if (type in current) current - type else current + type
            preferences.updateExcludedRocketTypes(updated)
        }
    }
    
    fun toggleExcludedRaidTier(tier: String) {
        viewModelScope.launch {
            val current = excludedRaidTiers.value
            val updated = if (tier in current) current - tier else current + tier
            preferences.updateExcludedRaidTiers(updated)
        }
    }
}
