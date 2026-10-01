package com.example.pokemonalertsv2.ui.history

import android.app.Application
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokemonalertsv2.data.FilterCatalogRepository
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.data.insights.SpawnActivity
import com.example.pokemonalertsv2.data.insights.SpawnInsights
import com.example.pokemonalertsv2.data.insights.SpawnSpeciesResponse
import kotlinx.coroutines.delay
import com.example.pokemonalertsv2.data.insights.buildSpawnInsights
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** How far back to look. Kept short: these are the windows worth walking on. */
enum class InsightsRange(val label: String, val days: Long) {
    LAST_7("7 days", 7),
    LAST_30("30 days", 30),
    LAST_90("90 days", 90)
}

/** Insights has two tabs: a species' alert history, and what the live scanner saw spawn. */
enum class InsightsTab(val label: String) {
    ALERTS("Alerts"),
    SPAWNS("Spawns")
}

/** Spawn-tab windows; the server keeps sightings for 30 days. */
enum class SpawnRange(val label: String, val days: Int) {
    TODAY("Today", 1),
    LAST_7("7 days", 7),
    LAST_14("14 days", 14),
    LAST_30("30 days", 30)
}

@Immutable
data class SpawnActivityUiState(
    val range: SpawnRange = SpawnRange.LAST_14,
    /** Null means every area. */
    val area: String? = null,
    val isLoading: Boolean = false,
    val activity: SpawnActivity? = null,
    val errorMessage: String? = null,
    /** Species search inside the range and area; blank shows the plain ranking. */
    val search: String = "",
    /** One day of the range to list species for, or null for the whole range. */
    val day: String? = null,
    /** The species list for [search] and/or [day]; null while neither is set. */
    val searchResult: SpawnSpeciesResponse? = null,
    val isSearching: Boolean = false,
    val searchError: String? = null
)

@Immutable
data class SpawnInsightsUiState(
    val tab: InsightsTab = InsightsTab.ALERTS,
    val spawns: SpawnActivityUiState = SpawnActivityUiState(),
    val query: String = "",
    val type: String? = null,
    val range: InsightsRange = InsightsRange.LAST_7,
    val isLoading: Boolean = false,
    val insights: SpawnInsights? = null,
    val errorMessage: String? = null,
    /** How far back the server says its own catalogue reaches, when it says. */
    val coverageNote: String? = null,
    val hasRun: Boolean = false
)

class SpawnInsightsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PokemonAlertsRepository.create(application)
    private val catalog = FilterCatalogRepository.getInstance(application)

    private val _uiState = MutableStateFlow(SpawnInsightsUiState())
    val uiState: StateFlow<SpawnInsightsUiState> = _uiState

    private var loadJob: Job? = null
    private var spawnJob: Job? = null
    private var searchJob: Job? = null

    init {
        val window = catalog.cached()?.observationWindow
        if (window != null && window.days > 0) {
            _uiState.update {
                it.copy(coverageNote = "History covers about the last ${window.days} days")
            }
        }
    }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun setType(type: String?) = _uiState.update { it.copy(type = type) }

    fun setRange(range: InsightsRange) {
        _uiState.update { it.copy(range = range) }
        if (_uiState.value.hasRun || _uiState.value.query.isNotBlank()) run()
    }

    fun setTab(tab: InsightsTab) {
        _uiState.update { it.copy(tab = tab) }
        val spawns = _uiState.value.spawns
        if (tab == InsightsTab.SPAWNS && spawns.activity == null && !spawns.isLoading) loadSpawns()
    }

    fun setSpawnRange(range: SpawnRange) {
        // A picked day may not be in the new range; start the new range whole.
        _uiState.update { it.copy(spawns = it.spawns.copy(range = range, day = null)) }
        loadSpawns()
    }

    fun setSpawnArea(area: String?) {
        _uiState.update { it.copy(spawns = it.spawns.copy(area = area)) }
        loadSpawns()
    }

    fun setSpawnDay(day: String?) {
        _uiState.update { it.copy(spawns = it.spawns.copy(day = day, searchResult = null)) }
        runSearch(debounce = false)
    }

    fun setSpawnSearch(text: String) {
        _uiState.update { it.copy(spawns = it.spawns.copy(search = text.take(50))) }
        runSearch(debounce = true)
    }

    /** Searches answer from the server's cached counts, so typing only waits out a short debounce. */
    private fun runSearch(debounce: Boolean) {
        searchJob?.cancel()
        val spawns = _uiState.value.spawns
        val query = spawns.search.trim()
        if (query.isEmpty() && spawns.day == null) {
            _uiState.update { it.copy(spawns = it.spawns.copy(searchResult = null, isSearching = false, searchError = null)) }
            return
        }
        searchJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            _uiState.update { it.copy(spawns = it.spawns.copy(isSearching = true, searchError = null)) }
            runCatching {
                repository.searchSpawnSpecies(
                    days = spawns.range.days,
                    area = spawns.area,
                    query = query.ifEmpty { null },
                    day = spawns.day
                )
            }
                .onSuccess { result ->
                    _uiState.update { it.copy(spawns = it.spawns.copy(isSearching = false, searchResult = result)) }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Spawn species search failed", throwable)
                    _uiState.update {
                        it.copy(spawns = it.spawns.copy(isSearching = false, searchError = throwable.localizedMessage ?: "Search failed"))
                    }
                }
        }
    }

    fun loadSpawns() {
        runSearch(debounce = false)
        val spawns = _uiState.value.spawns
        spawnJob?.cancel()
        _uiState.update { it.copy(spawns = it.spawns.copy(isLoading = true, errorMessage = null)) }
        spawnJob = viewModelScope.launch {
            runCatching { repository.fetchSpawnActivity(days = spawns.range.days, area = spawns.area) }
                .onSuccess { activity ->
                    _uiState.update { it.copy(spawns = it.spawns.copy(isLoading = false, activity = activity)) }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Spawn activity load failed", throwable)
                    _uiState.update {
                        it.copy(
                            spawns = it.spawns.copy(
                                isLoading = false,
                                errorMessage = throwable.localizedMessage ?: "Couldn't load spawn activity"
                            )
                        )
                    }
                }
        }
    }

    /** Seeds from whatever the History tab was already looking at. */
    fun seed(query: String, type: String?) {
        _uiState.update { it.copy(query = query, type = type) }
    }

    fun run() {
        val state = _uiState.value
        val today = LocalDate.now()
        val start = today.minusDays(state.range.days - 1)

        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null, hasRun = true) }
        loadJob = viewModelScope.launch {
            runCatching {
                val history = repository.fetchInsightsHistory(
                    startDate = start.format(DATE),
                    endDate = today.format(DATE),
                    type = state.type,
                    q = state.query.trim().takeIf { it.isNotEmpty() }
                )
                // Bucketing a few thousand rows is cheap but not free, and it has
                // no business happening on the frame the user is scrolling.
                withContext(Dispatchers.Default) { buildSpawnInsights(history) }
            }.onSuccess { insights ->
                _uiState.update { it.copy(isLoading = false, insights = insights) }
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                Log.e(TAG, "Insights load failed", throwable)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.localizedMessage ?: "Couldn't load insights"
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "SpawnInsightsVM"
        const val SEARCH_DEBOUNCE_MS = 300L
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
