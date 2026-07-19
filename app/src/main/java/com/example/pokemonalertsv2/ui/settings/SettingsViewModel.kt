package com.example.pokemonalertsv2.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.data.SortPreference
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import com.example.pokemonalertsv2.data.godex.GoDexDebugEntry
import com.example.pokemonalertsv2.widget.AlertsWidgetProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PokemonAlertsRepository.create(application)
    private val goDexRepository = GoDexRepository.getInstance(application)

    val goDexConfig = goDexRepository.config
    val goDexEntries = goDexRepository.entries
    val goDexSyncUiState = goDexRepository.syncUiState

    fun buildGoDexDebugEntries(entries: List<GoDexEntryEntity>): List<GoDexDebugEntry> =
        goDexRepository.debugEntries(entries)

    val onboardingCompleted: StateFlow<Boolean?> = repository.observeOnboardingCompleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val themeMode: StateFlow<Int> = repository.observeThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val sortPreference: StateFlow<SortPreference> = repository.alertPreferences.sortPreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortPreference.POSTED_TIME)
    
    val notificationsEnabled: StateFlow<Boolean> = repository.alertPreferences.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val raidsNotifications: StateFlow<Boolean> = repository.alertPreferences.raidsNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val spawnsNotifications: StateFlow<Boolean> = repository.alertPreferences.spawnsNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val questsNotifications: StateFlow<Boolean> = repository.alertPreferences.questsNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val hundosNotifications: StateFlow<Boolean> = repository.alertPreferences.hundosNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val pvpNotifications: StateFlow<Boolean> = repository.alertPreferences.pvpNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val nundosNotifications: StateFlow<Boolean> = repository.alertPreferences.nundosNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val kecleonNotifications: StateFlow<Boolean> = repository.alertPreferences.kecleonNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val rocketNotifications: StateFlow<Boolean> = repository.alertPreferences.rocketNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val notificationVibrate: StateFlow<Boolean> = repository.alertPreferences.notificationVibrate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val silenceUntil: StateFlow<Long> = repository.alertPreferences.silenceUntil
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
        
    val selectedArea: StateFlow<String> = repository.alertPreferences.selectedArea
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "All")
        
    val maxDistance: StateFlow<Int> = repository.alertPreferences.maxDistance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
        
    val snoozeDuration: StateFlow<Int> = repository.alertPreferences.snoozeDuration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)
    
    // Excluded type preferences
    val excludedHundoTypes: StateFlow<Set<String>> = repository.alertPreferences.excludedHundoTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    
    val excludedNundoTypes: StateFlow<Set<String>> = repository.alertPreferences.excludedNundoTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    
    val excludedPvpTypes: StateFlow<Set<String>> = repository.alertPreferences.excludedPvpTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    
    val excludedSpawnTypes: StateFlow<Set<String>> = repository.alertPreferences.excludedSpawnTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    
    val excludedRocketTypes: StateFlow<Set<String>> = repository.alertPreferences.excludedRocketTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    
    val excludedRaidTiers: StateFlow<Set<String>> = repository.alertPreferences.excludedRaidTiers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun completeOnboarding() {
        viewModelScope.launch {
            repository.setOnboardingCompleted(true)
        }
    }

    fun updateThemeMode(mode: Int) {
        viewModelScope.launch {
            repository.setThemeMode(mode.coerceIn(0, 2))
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }

    fun updateSortPreference(preference: SortPreference) {
        viewModelScope.launch {
            repository.alertPreferences.updateSortPreference(preference)
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.alertPreferences.updateNotificationsEnabled(enabled)
        }
    }
    
    fun updateRaidsNotifications(enabled: Boolean) {
        viewModelScope.launch {
            repository.alertPreferences.updateRaidsNotifications(enabled)
        }
    }
    
    fun updateSpawnsNotifications(enabled: Boolean) {
        viewModelScope.launch {
            repository.alertPreferences.updateSpawnsNotifications(enabled)
        }
    }
    
    fun updateQuestsNotifications(enabled: Boolean) {
        viewModelScope.launch {
            repository.alertPreferences.updateQuestsNotifications(enabled)
        }
    }
    
    fun updateHundosNotifications(enabled: Boolean) {
        viewModelScope.launch {
            repository.alertPreferences.updateHundosNotifications(enabled)
        }
    }
    
    fun updatePvpNotifications(enabled: Boolean) {
        viewModelScope.launch {
            repository.alertPreferences.updatePvpNotifications(enabled)
        }
    }
    
    fun updateNundosNotifications(enabled: Boolean) {
        viewModelScope.launch {
            repository.alertPreferences.updateNundosNotifications(enabled)
        }
    }
    
    fun updateKecleonNotifications(enabled: Boolean) {
        viewModelScope.launch {
            repository.alertPreferences.updateKecleonNotifications(enabled)
        }
    }
    
    fun updateRocketNotifications(enabled: Boolean) {
        viewModelScope.launch {
            repository.alertPreferences.updateRocketNotifications(enabled)
        }
    }
    
    fun updateNotificationVibrate(enabled: Boolean) {
        viewModelScope.launch {
            repository.alertPreferences.updateNotificationVibrate(enabled)
        }
    }
    
    fun silenceNotificationsFor(durationMinutes: Int) {
        viewModelScope.launch {
            val silenceUntil = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
            repository.alertPreferences.updateSilenceUntil(silenceUntil)
        }
    }
    
    fun clearNotificationSilence() {
        viewModelScope.launch {
            repository.alertPreferences.updateSilenceUntil(0L)
        }
    }
    
    fun updateSelectedArea(area: String) {
        viewModelScope.launch {
            repository.alertPreferences.updateSelectedArea(area)
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }
    
    fun updateMaxDistance(distance: Int) {
        viewModelScope.launch {
            repository.alertPreferences.updateMaxDistance(distance)
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }
    
    fun updateSnoozeDuration(minutes: Int) {
        viewModelScope.launch {
            repository.alertPreferences.updateSnoozeDuration(minutes)
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

    fun updateGoDexNotificationFilter(enabled: Boolean) {
        viewModelScope.launch {
            goDexRepository.setNotificationFilterEnabled(enabled)
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
        }
    }
    
    fun toggleExcludedHundoType(type: String) {
        viewModelScope.launch {
            val current = excludedHundoTypes.value
            val updated = if (type in current) current - type else current + type
            repository.alertPreferences.updateExcludedHundoTypes(updated)
        }
    }
    
    fun toggleExcludedNundoType(type: String) {
        viewModelScope.launch {
            val current = excludedNundoTypes.value
            val updated = if (type in current) current - type else current + type
            repository.alertPreferences.updateExcludedNundoTypes(updated)
        }
    }
    
    fun toggleExcludedPvpType(type: String) {
        viewModelScope.launch {
            val current = excludedPvpTypes.value
            val updated = if (type in current) current - type else current + type
            repository.alertPreferences.updateExcludedPvpTypes(updated)
        }
    }
    
    fun toggleExcludedSpawnType(type: String) {
        viewModelScope.launch {
            val current = excludedSpawnTypes.value
            val updated = if (type in current) current - type else current + type
            repository.alertPreferences.updateExcludedSpawnTypes(updated)
        }
    }
    
    fun toggleExcludedRocketType(type: String) {
        viewModelScope.launch {
            val current = excludedRocketTypes.value
            val updated = if (type in current) current - type else current + type
            repository.alertPreferences.updateExcludedRocketTypes(updated)
        }
    }
    
    fun toggleExcludedRaidTier(tier: String) {
        viewModelScope.launch {
            val current = excludedRaidTiers.value
            val updated = if (tier in current) current - tier else current + tier
            repository.alertPreferences.updateExcludedRaidTiers(updated)
        }
    }
}
