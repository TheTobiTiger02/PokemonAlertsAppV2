package com.example.pokemonalertsv2.ui.alerts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.data.MapStylePreference
import com.example.pokemonalertsv2.data.FilterPreset
import com.example.pokemonalertsv2.data.SortPreference
import com.example.pokemonalertsv2.notifications.AlertSnoozeScheduler
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.widget.AlertsWidgetProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.pokemonalertsv2.util.TravelTime
import androidx.compose.runtime.Immutable

@Immutable
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

    init {
        viewModelScope.launch {
            val persisted = repository.alertPreferences.lastSuccessfulAlertSyncMillis.first()
            if (persisted > 0L) {
                _uiState.update { current ->
                    current.copy(syncMetadata = current.syncMetadata.copy(lastSuccessfulSyncMillis = persisted))
                }
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

    /** Live per-category counts over the active alerts; feeds every filter chip badge. */
    val categoryCounts: StateFlow<Map<AlertCategory, Int>> = _uiState
        .map { state -> countAlertsByCategory(state.alerts) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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

    val maxWalkingMinutes = repository.alertPreferences.maxWalkingMinutes
        .asPreferenceState(TravelTime.NO_LIMIT)

    fun updateMaxWalkingMinutes(minutes: Int) {
        viewModelScope.launch {
            repository.alertPreferences.updateMaxWalkingMinutes(minutes)
        }
    }

    val filterPresets = repository.alertPreferences.filterPresets
        .asPreferenceState(emptyList())

    /** Captures whatever the feed controls currently say, under [name]. */
    fun saveFilterPreset(
        name: String,
        categories: Set<AlertCategory>,
        sort: SortPreference,
        area: String,
        maxDistance: Int
    ) {
        viewModelScope.launch {
            repository.alertPreferences.saveFilterPreset(
                FilterPreset(
                    name = name,
                    sort = sort.name,
                    area = area,
                    maxDistance = maxDistance,
                    categories = categories.toStoredNames()
                )
            )
        }
    }

    fun deleteFilterPreset(name: String) {
        viewModelScope.launch { repository.alertPreferences.deleteFilterPreset(name) }
    }

    /**
     * Puts every control back at once.
     *
     * Unknown enum names fall back to the defaults rather than throwing: a preset saved by
     * an older build must not be able to crash the feed.
     */
    fun applyFilterPreset(preset: FilterPreset) {
        val sort = SortPreference.entries.firstOrNull { it.name == preset.sort }
            ?: SortPreference.POSTED_TIME
        updateSelectedFeedCategories(preset.categories.toCategorySelection())
        updateSortPreference(sort)
        updateSelectedArea(preset.area)
        updateMaxDistance(preset.maxDistance)
    }

    // Per-surface category selections. Each surface stores its own set so the map keeps
    // showing raids while the feed is narrowed to quests, and neither disturbs the other.
    val selectedFeedCategories = repository.alertPreferences.feedCategories
        .map { stored -> stored.toCategorySelection() }
        .asPreferenceState(emptySet())

    val selectedMapCategories = repository.alertPreferences.mapCategories
        .map { stored -> stored.toCategorySelection() }
        .asPreferenceState(emptySet())

    val mapShowDismissed = repository.alertPreferences.mapShowDismissed
        .asPreferenceState(false)

    fun updateSelectedFeedCategories(categories: Set<AlertCategory>) {
        viewModelScope.launch {
            repository.alertPreferences.updateFeedCategories(categories.toStoredNames())
        }
    }

    fun updateSelectedMapCategories(categories: Set<AlertCategory>) {
        viewModelScope.launch {
            repository.alertPreferences.updateMapCategories(categories.toStoredNames())
        }
    }

    fun updateMapShowDismissed(enabled: Boolean) {
        viewModelScope.launch {
            repository.alertPreferences.updateMapShowDismissed(enabled)
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
            repository.alertPreferences.updateMaxDistance(distance.coerceIn(0, 50))
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }
    
    // Hot StateFlows rather than the raw DataStore Flows: a cold flow makes every collector
    // supply its own initialValue, which showed a frame of default filters ("All" area,
    // unlimited distance) on each screen entry and opened one DataStore read per subscriber.
    private fun <T> Flow<T>.asPreferenceState(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val dismissedAlertIds = repository.alertPreferences.dismissedAlertIds
        .asPreferenceState(emptySet())
    val sortPreference = repository.alertPreferences.sortPreference
        .asPreferenceState(SortPreference.POSTED_TIME)
    val mapStylePreference = repository.alertPreferences.mapStylePreference
        .asPreferenceState(MapStylePreference.fromStoredValue(null))
    val showMapCountdowns = repository.alertPreferences.showMapCountdowns
        .asPreferenceState(false)
    val autoEnterMapPip = repository.alertPreferences.autoEnterMapPip
        .asPreferenceState(false)
    val showSpawnRadius = repository.alertPreferences.showSpawnRadius
        .asPreferenceState(false)
    val spacialRendEnabled = repository.alertPreferences.spacialRendEnabled
        .asPreferenceState(false)

    val selectedArea = repository.alertPreferences.selectedArea
        .asPreferenceState("All")
    val maxDistance = repository.alertPreferences.maxDistance
        .asPreferenceState(0)
    val snoozeDuration = repository.alertPreferences.snoozeDuration
        .asPreferenceState(10)
    
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

    fun updateAutoEnterMapPip(enabled: Boolean) {
        viewModelScope.launch {
            repository.alertPreferences.updateAutoEnterMapPip(enabled)
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
