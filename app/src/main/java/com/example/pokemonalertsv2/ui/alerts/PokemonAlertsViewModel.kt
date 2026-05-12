package com.example.pokemonalertsv2.ui.alerts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.util.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlertsUiState(
    val alerts: List<PokemonAlert> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val highlightedAlertId: String? = null
)

class PokemonAlertsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PokemonAlertsRepository.create(application)

    private val _uiState = MutableStateFlow(AlertsUiState(isLoading = true))
    val uiState: StateFlow<AlertsUiState> = _uiState

    init {
        refreshAlerts()
        viewModelScope.launch {
            repository.alerts.collect { alerts ->
                val now = System.currentTimeMillis()
                val activeAlerts = alerts.filter {
                    val end = TimeUtils.parseEndTimeToMillis(it.endTime) ?: Long.MAX_VALUE
                    end > now
                }.sortedByDescending { it.endTime }
                _uiState.update { it.copy(alerts = activeAlerts) }
                repository.syncToWear(activeAlerts)
            }
        }
    }

    fun refreshAlerts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                repository.fetchAlerts()
            }.onSuccess {
                _uiState.update { current -> current.copy(isLoading = false) }
            }.onFailure { throwable ->
                _uiState.update { current ->
                    current.copy(isLoading = false, errorMessage = throwable.localizedMessage ?: "Unknown error")
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
    
    val dismissedAlertIds = repository.alertPreferences.dismissedAlertIds
    
    val selectedArea = repository.alertPreferences.selectedArea
    val maxDistance = repository.alertPreferences.maxDistance
    
    fun dismissAlert(alertId: String) {
        viewModelScope.launch {
            repository.alertPreferences.addDismissedAlert(alertId)
        }
    }
    
    fun undoDismissAlert(alertId: String) {
        viewModelScope.launch {
            repository.alertPreferences.removeDismissedAlert(alertId)
        }
    }
}
