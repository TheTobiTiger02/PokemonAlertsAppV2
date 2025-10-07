package com.example.pokemonalertsv2.ui.alerts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
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
    }

    fun refreshAlerts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                repository.fetchAlerts()
            }.onSuccess { alerts ->
                _uiState.update { current ->
                    current.copy(alerts = alerts.sortedByDescending { it.endTime }, isLoading = false)
                }
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
}
