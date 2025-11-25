package com.example.pokemonalertsv2.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PokemonAlertsRepository.create(application)

    val onboardingCompleted: StateFlow<Boolean?> = repository.observeOnboardingCompleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
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

    fun completeOnboarding() {
        viewModelScope.launch {
            repository.setOnboardingCompleted(true)
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
}