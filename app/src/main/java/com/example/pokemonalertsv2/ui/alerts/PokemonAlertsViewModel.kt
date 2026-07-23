package com.example.pokemonalertsv2.ui.alerts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.data.MapStylePreference
import com.example.pokemonalertsv2.notifications.AlertSnoozeScheduler
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.widget.AlertsWidgetProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AlertsUiState(
    val alerts: List<PokemonAlert> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val highlightedAlertId: String? = null,
    val syncMetadata: SyncMetadata = SyncMetadata()
)

data class SyncMetadata(
    val lastSuccessfulSyncMillis: Long? = null,
    val lastAttemptMillis: Long? = null,
    val isShowingCachedData: Boolean = false
)

class PokemonAlertsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PokemonAlertsRepository.create(application)

    private val _uiState = MutableStateFlow(AlertsUiState(isLoading = true))
    val uiState: StateFlow<AlertsUiState> = _uiState
    private var refreshJob: Job? = null
    private val _selectedAlertFilter = MutableStateFlow(AlertFilter.ALL)
    val selectedAlertFilter: StateFlow<AlertFilter> = _selectedAlertFilter

    init {
        viewModelScope.launch {
            val persisted = repository.alertPreferences.lastSuccessfulAlertSyncMillis.first()
            if (persisted > 0L) {
                _uiState.update { current ->
                    current.copy(syncMetadata = current.syncMetadata.copy(lastSuccessfulSyncMillis = persisted))
                }
            }
        }
        viewModelScope.launch {
            repository.alertPreferences.selectedAlertFilterName.collect { stored ->
                _selectedAlertFilter.value = AlertFilter.entries.firstOrNull { it.name == stored } ?: AlertFilter.ALL
            }
        }
        refreshAlerts()
        viewModelScope.launch {
            repository.alerts.collect { alerts ->
                val now = System.currentTimeMillis()
                val activeAlerts = alerts.filter {
                    val end = TimeUtils.parseEndTimeToMillis(it.endTime) ?: Long.MAX_VALUE
                    end > now && !it.isInvalidated
                }.sortedByDescending { it.endTime }
                _uiState.update { it.copy(alerts = activeAlerts) }
            }
        }
    }

    fun refreshAlerts() {
        startRefresh(showLoading = true)
    }

    fun refreshAlertsInBackground() {
        startRefresh(showLoading = false)
    }

    private fun startRefresh(showLoading: Boolean) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            } else {
                _uiState.update { it.copy(errorMessage = null) }
            }
            runCatching {
                repository.fetchAlerts()
            }.onSuccess {
                val now = System.currentTimeMillis()
                repository.alertPreferences.updateLastSuccessfulAlertSyncMillis(now)
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        syncMetadata = SyncMetadata(
                            lastSuccessfulSyncMillis = now,
                            lastAttemptMillis = now,
                            isShowingCachedData = false
                        )
                    )
                }
            }.onFailure { throwable ->
                val now = System.currentTimeMillis()
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = if (current.alerts.isNotEmpty()) {
                            "Offline — showing cached alerts"
                        } else {
                            throwable.localizedMessage ?: "Unable to load alerts"
                        },
                        syncMetadata = current.syncMetadata.copy(
                            lastAttemptMillis = now,
                            isShowingCachedData = current.alerts.isNotEmpty()
                        )
                    )
                }
            }
        }
    }

    fun highlightAlert(alertId: String?) {
        _uiState.update { current -> current.copy(highlightedAlertId = alertId) }
    }

    fun consumeError() {
        _uiState.update { current -> current.copy(errorMessage = null) }
    }

    fun updateSelectedAlertFilter(filter: AlertFilter) {
        _selectedAlertFilter.value = filter
        viewModelScope.launch { repository.alertPreferences.updateSelectedAlertFilterName(filter.name) }
    }

    fun updateSelectedArea(area: String) {
        viewModelScope.launch {
            repository.alertPreferences.updateSelectedArea(area)
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }

    fun updateMaxDistance(distance: Int) {
        viewModelScope.launch {
            repository.alertPreferences.updateMaxDistance(distance.coerceIn(0, 50))
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }
    
    val dismissedAlertIds = repository.alertPreferences.dismissedAlertIds
    val sortPreference = repository.alertPreferences.sortPreference
    val mapStylePreference = repository.alertPreferences.mapStylePreference
    val showMapCountdowns = repository.alertPreferences.showMapCountdowns
    val showSpawnRadius = repository.alertPreferences.showSpawnRadius
    val spacialRendEnabled = repository.alertPreferences.spacialRendEnabled
    
    val selectedArea = repository.alertPreferences.selectedArea
    val maxDistance = repository.alertPreferences.maxDistance
    val snoozeDuration = repository.alertPreferences.snoozeDuration
    
    fun dismissAlert(alertId: String) {
        viewModelScope.launch {
            repository.alertPreferences.addDismissedAlert(alertId)
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }
    
    fun undoDismissAlert(alertId: String) {
        viewModelScope.launch {
            repository.alertPreferences.removeDismissedAlert(alertId)
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }

    fun updateSortPreference(preference: com.example.pokemonalertsv2.data.SortPreference) {
        viewModelScope.launch {
            repository.alertPreferences.updateSortPreference(preference)
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }

    fun updateMapStylePreference(preference: MapStylePreference) {
        viewModelScope.launch {
            repository.alertPreferences.updateMapStylePreference(preference)
        }
    }

    fun updateShowMapCountdowns(enabled: Boolean) {
        viewModelScope.launch {
            repository.alertPreferences.updateShowMapCountdowns(enabled)
        }
    }

    fun updateShowSpawnRadius(enabled: Boolean) {
        viewModelScope.launch {
            repository.alertPreferences.updateShowSpawnRadius(enabled)
        }
    }

    fun updateSpacialRendEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.alertPreferences.updateSpacialRendEnabled(enabled)
        }
    }

    fun snoozeAlert(alert: PokemonAlert, minutes: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val safeMinutes = minutes.coerceIn(1, 24 * 60)
            repository.alertPreferences.updateSnoozeDuration(safeMinutes)
            val scheduled = AlertSnoozeScheduler.schedule(getApplication(), alert, safeMinutes)
            onResult(scheduled)
        }
    }
}
