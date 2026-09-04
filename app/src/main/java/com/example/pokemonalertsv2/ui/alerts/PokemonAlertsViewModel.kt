package com.example.pokemonalertsv2.ui.alerts

import android.app.Application
import com.example.pokemonalertsv2.util.WalkingRouteUtils
import com.example.pokemonalertsv2.util.WalkingRouteRepository
import com.example.pokemonalertsv2.util.WalkingRouteInfo
import com.example.pokemonalertsv2.util.CachedLocationProvider
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.data.MapStylePreference
import com.example.pokemonalertsv2.data.FilterPreset
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterCatalog
import com.example.pokemonalertsv2.data.FilterCatalogRepository
import com.example.pokemonalertsv2.data.FilterSelectionMode
import com.example.pokemonalertsv2.data.PokemonSpeciesRepository
import com.example.pokemonalertsv2.data.normalizeFilterToken
import com.example.pokemonalertsv2.data.FilterAssignment
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.FilterStateDocument
import com.example.pokemonalertsv2.data.FilterSurface
import com.example.pokemonalertsv2.data.SortPreference
import com.example.pokemonalertsv2.notifications.AlertSnoozeScheduler
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.widget.AlertsWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                // Parse once per alert and sort on the parsed value: endTime is an ISO string,
                // so sorting it directly ordered lexicographically, not chronologically.
                val activeAlerts = withContext(Dispatchers.Default) {
                    alerts.asSequence()
                        .filter { !it.isInvalidated }
                        .map { it to (TimeUtils.parseEndTimeToMillis(it.endTime) ?: Long.MAX_VALUE) }
                        .filter { (_, end) -> end > now }
                        .sortedByDescending { (_, end) -> end }
                        .map { (alert, _) -> alert }
                        .toList()
                }
                // Room re-emits on every table write; skipping content-equal lists keeps the
                // distance/filter/sort pipelines (and the grid diff) from re-running for nothing.
                _uiState.update { current ->
                    if (current.alerts == activeAlerts) current else current.copy(alerts = activeAlerts)
                }
            }
        }
    }

    // ── Location and distance pipeline ────────────────────────────────────
    // These used to live in PokemonAlertsScreen as `remember` blocks, which meant every entry
    // to the Alerts tab recomputed a Location.distanceBetween, a route lookup and an endTime
    // parse for every alert on the main thread, because AnimatedContent disposes the outgoing
    // screen. Holding them here computes once and survives navigation.

    private val _userLocation = MutableStateFlow<Location?>(null)
    val userLocation: StateFlow<Location?> = _userLocation

    private val _locationLookupComplete = MutableStateFlow(false)
    val locationLookupComplete: StateFlow<Boolean> = _locationLookupComplete

    /** [granted] comes from the screen, which owns the permission prompt. */
    fun refreshUserLocation(granted: Boolean) {
        viewModelScope.launch {
            _userLocation.value = if (granted) {
                CachedLocationProvider.get(getApplication(), timeoutMs = 5_000, highAccuracy = true)
                    ?.takeIf { validMapCoordinates(it.latitude, it.longitude) != null }
            } else {
                null
            }
            _locationLookupComplete.value = true
        }
    }

    /**
     * Periodic companion to [refreshUserLocation] for the feed's 30 s poll: a
     * balanced-power fix keeps walking distances honest while the user walks with the
     * screen open. Deliberately quiet -- no high-accuracy GNSS and no lookup-state
     * transition; a failed fix just leaves the previous location standing.
     */
    fun refreshUserLocationQuietly(granted: Boolean) {
        if (!granted) return
        viewModelScope.launch {
            CachedLocationProvider.get(getApplication(), timeoutMs = 4_000, highAccuracy = false)
                ?.takeIf { validMapCoordinates(it.latitude, it.longitude) != null }
                ?.let { _userLocation.value = it }
        }
    }

    fun markLocationLookupPending() {
        _locationLookupComplete.value = false
    }

    private val walkingRoutes: StateFlow<Map<String, WalkingRouteInfo>> =
        combine(_uiState.map { it.alerts }.distinctUntilChanged(), _userLocation) { alerts, location ->
            alerts to location
        }.map { (alerts, location) ->
            if (location == null) emptyMap() else WalkingRouteRepository.getInstance()
                .getWalkingRoutes(location, alerts.filter { it.mapCoordinatesOrNull() != null })
        }
            // Ranking candidates means a straight-line distance for every alert plus a
            // sort -- over 1000 alerts that has no business running on the main thread.
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Alerts decorated with distance/route/expiry, ready for the feed to filter and sort. */
    val alertsWithDistance: StateFlow<List<AlertUiModel>> = combine(
        _uiState.map { it.alerts }.distinctUntilChanged(),
        _userLocation,
        walkingRoutes
    ) { alerts, location, routes ->
        alerts.map { alert ->
            val straightLine = location?.let { origin ->
                alert.mapCoordinatesOrNull()?.let { coordinates ->
                    val results = FloatArray(1)
                    Location.distanceBetween(origin.latitude, origin.longitude, coordinates.latitude, coordinates.longitude, results)
                    results.getOrNull(0)?.takeUnless(Float::isNaN)
                }
            }
            val display = WalkingRouteUtils.buildRouteDisplayInfo(straightLine, routes[alert.uniqueId])
            AlertUiModel(
                alert = alert,
                distanceInfo = AlertDistanceInfo(
                    distanceMeters = display.effectiveDistanceMeters,
                    distanceText = display.distanceText,
                    walkingText = display.walkingText,
                    straightLineDistanceMeters = display.straightLineDistanceMeters,
                    routedWalkingDistanceMeters = display.routedDistanceMeters,
                    walkingDurationSeconds = display.walkingDurationSeconds,
                    source = display.source
                ),
                endMillis = TimeUtils.parseEndTimeToMillis(alert.endTime),
                typeKeys = alert.typeKeys()
            )
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Live per-category counts over the active alerts; feeds every filter chip badge. */
    val categoryCounts: StateFlow<Map<AlertCategory, Int>> = _uiState
        .map { state -> countAlertsByCategory(state.alerts) }
        .flowOn(Dispatchers.Default)
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
            repository.alertPreferences.updateFilterStateDocument { document ->
                val feed = document.feed.resolve(document).copy(maxWalkingMinutes = minutes.coerceIn(0, 240))
                document.withAssignment(FilterSurface.FEED, FilterAssignment.local(feed))
            }
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

    val filterStateDocument = repository.alertPreferences.filterStateDocument
        .asPreferenceState(FilterStateDocument())

    val feedFilterDefinition = filterStateDocument
        .map { document -> document.feed.resolve(document) }
        .asPreferenceState(FilterDefinition())

    val mapFilterDefinition = filterStateDocument
        .map { document -> document.map.resolve(document) }
        .asPreferenceState(FilterDefinition())

    val mapShowDismissed = repository.alertPreferences.mapShowDismissed
        .asPreferenceState(false)

    fun updateSelectedFeedCategories(categories: Set<AlertCategory>) {
        viewModelScope.launch {
            repository.alertPreferences.updateFeedCategories(categories.toStoredNames())
            repository.alertPreferences.updateFilterStateDocument { document ->
                val current = document.feed.resolve(document)
                document.withAssignment(
                    FilterSurface.FEED,
                    FilterAssignment.local(current.copy(alertTypes = selectionFromMuted(categories)))
                )
            }
        }
    }

    fun updateSelectedMapCategories(categories: Set<AlertCategory>) {
        viewModelScope.launch {
            repository.alertPreferences.updateMapCategories(categories.toStoredNames())
            repository.alertPreferences.updateFilterStateDocument { document ->
                val current = document.map.resolve(document)
                document.withAssignment(
                    FilterSurface.MAP,
                    FilterAssignment.local(current.copy(alertTypes = selectionFromMuted(categories)))
                )
            }
        }
    }

    // Catalog and artwork for the on-map filter sheet. Both are cheap to hold and are what the
    // pickers need; loading happens off the main thread so opening the map never blocks on it.
    private val filterCatalogRepository = FilterCatalogRepository.getInstance(application)
    private val _filterCatalog = MutableStateFlow(FilterCatalog())
    val filterCatalog: StateFlow<FilterCatalog> = _filterCatalog

    /** Reward artwork lifted from live quest alerts, so pickers match what the map shows. */
    val questRewardThumbnails: StateFlow<Map<String, String>> = _uiState
        .map { it.alerts }
        .distinctUntilChanged()
        .map { alerts -> com.example.pokemonalertsv2.data.questRewardThumbnails(alerts) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val filterArtwork: StateFlow<Map<String, String>> =
        PokemonSpeciesRepository.getInstance(application).searchSpecies("")
            .map { species -> species.associate { normalizeFilterToken(it.name) to it.imageUrl } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _filterCatalog.value = filterCatalogRepository.cached() ?: FilterCatalog()
            runCatching { filterCatalogRepository.refresh() }.onSuccess { _filterCatalog.value = it }
        }
    }

    /**
     * Applies a whole map [FilterDefinition] from the on-map sheet. This writes the same
     * assignment the Filter Studio edits, so the two surfaces never diverge. Linked profiles
     * are intentionally demoted to a local copy: quick edits on the map must not silently
     * rewrite a profile that the feed or notifications also use.
     */
    fun updateMapFilterDefinition(definition: FilterDefinition) {
        viewModelScope.launch {
            repository.alertPreferences.updateFilterStateDocument { document ->
                document.withAssignment(FilterSurface.MAP, FilterAssignment.local(definition))
            }
            // Keep the legacy muted-category mirror in step so widgets and older code paths agree.
            repository.alertPreferences.updateMapCategories(mutedFromSelection(definition.alertTypes))
        }
    }

    /** Inverse of [selectionFromMuted]; stored names are [AlertCategory] names, not filter-type names. */
    private fun mutedFromSelection(selection: FilterSelection): Set<String> = when (selection.mode) {
        FilterSelectionMode.ALL -> emptySet()
        FilterSelectionMode.NONE -> FILTERABLE_ALERT_CATEGORIES.toSet().toStoredNames()
        FilterSelectionMode.ONLY -> FILTERABLE_ALERT_CATEGORIES
            .filterNot { category -> selection.contains(category.name) }
            .toSet()
            .toStoredNames()
    }

    private fun selectionFromMuted(categories: Set<AlertCategory>): FilterSelection {
        if (categories.isEmpty()) return FilterSelection.All
        val muted = categories.map { it.name }.toSet()
        return FilterSelection.only(FilterAlertType.entries.filterNot { it.name in muted }.map { it.name })
    }

    fun updateMapShowDismissed(enabled: Boolean) {
        viewModelScope.launch {
            repository.alertPreferences.updateMapShowDismissed(enabled)
        }
    }

    fun updateSelectedArea(area: String) {
        viewModelScope.launch {
            repository.alertPreferences.updateSelectedArea(area)
            repository.alertPreferences.updateFilterStateDocument { document ->
                val selection = if (area.equals("All", ignoreCase = true)) FilterSelection.All
                else FilterSelection.only(listOf(area))
                val feed = document.feed.resolve(document).copy(areas = selection)
                document.withAssignment(FilterSurface.FEED, FilterAssignment.local(feed))
            }
            AlertsWidgetProvider.requestUpdate(getApplication())
        }
    }

    fun updateMaxDistance(distance: Int) {
        viewModelScope.launch {
            repository.alertPreferences.updateMaxDistance(distance.coerceIn(0, 50))
            repository.alertPreferences.updateFilterStateDocument { document ->
                val feed = document.feed.resolve(document).copy(maxDistanceKm = distance.coerceIn(0, 50))
                document.withAssignment(FilterSurface.FEED, FilterAssignment.local(feed))
            }
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
    val showWeatherCells = repository.alertPreferences.showWeatherCells
        .asPreferenceState(true)

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

    fun updateShowWeatherCells(enabled: Boolean) {
        viewModelScope.launch {
            repository.alertPreferences.updateShowWeatherCells(enabled)
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
