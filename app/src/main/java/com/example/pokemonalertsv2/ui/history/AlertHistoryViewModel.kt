package com.example.pokemonalertsv2.ui.history

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.data.TotalStatsResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val alerts: List<PokemonAlert> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val totalServerCount: Int = 0,
    val errorMessage: String? = null,
    /** Currently active server-side date filter (YYYY-MM-DD) or null = all time. */
    val selectedDate: String? = null,
    /** Currently active server-side type filter (e.g. "Raid") or null = all types. */
    val selectedType: String? = null,
    /** Current server-side text search. Blank = no search. */
    val searchQuery: String = "",
    /** All-time stats from /api/stats/total. */
    val totalStats: TotalStatsResponse? = null
)

class AlertHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PokemonAlertsRepository.create(application)

    private val _uiState = MutableStateFlow(HistoryUiState(isLoading = true))
    val uiState: StateFlow<HistoryUiState> = _uiState

    private var currentOffset = 0
    private var searchJob: Job? = null

    init {
        // Observe cached history from Room — UI always reflects the local DB.
        viewModelScope.launch {
            repository.historyAlerts.collect { cached ->
                _uiState.update { it.copy(alerts = cached) }
            }
        }
        // Kick off a network refresh + stats fetch in parallel.
        refreshHistory()
        fetchTotalStats()
    }

    // ── Filtering (server-side) ───────────────────────────────────────────

    /**
     * Sets (or clears) the server-side date filter and triggers a refresh.
     * @param date "YYYY-MM-DD" or null to clear.
     */
    fun setDateFilter(date: String?) {
        _uiState.update { it.copy(selectedDate = date) }
        refreshHistory()
    }

    /**
     * Sets (or clears) the server-side type filter and triggers a refresh.
     * @param type API type string (e.g. "Raid", "Hundo") or null = all types.
     */
    fun setTypeFilter(type: String?) {
        _uiState.update { it.copy(selectedType = type) }
        refreshHistory()
    }

    /**
     * Updates the server-side text search query. The visible query updates
     * immediately, while the refresh is debounced to avoid one request per key.
     */
    fun setSearchQuery(query: String) {
        currentOffset = 0
        _uiState.update { it.copy(searchQuery = query, errorMessage = null, canLoadMore = false) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            refreshHistory()
        }
    }

    // ── Pagination ───────────────────────────────────────────────────────

    /**
     * Full refresh: clears the local cache and loads the first page from the
     * API with the current [selectedDate] filter. Called on init and pull-to-refresh.
     */
    fun refreshHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, canLoadMore = true) }
            currentOffset = 0
            val state = _uiState.value
            val date = state.selectedDate
            val type = state.selectedType
            Log.d(TAG, "Refreshing history… date=$date, type=$type")
            val query = state.searchQuery.trim().takeIf { it.isNotEmpty() }
            runCatching {
                repository.refreshHistory(PAGE_SIZE, date = date, type = type, q = query)
            }.onSuccess { response ->
                currentOffset = PAGE_SIZE
                val total = response.total ?: response.data.size
                Log.d(TAG, "Refresh OK – got ${response.data.size} of $total total")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        totalServerCount = total,
                        canLoadMore = currentOffset < total
                    )
                }
            }.onFailure { throwable ->
                Log.e(TAG, "Refresh failed", throwable)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.localizedMessage ?: "Failed to load history"
                    )
                }
            }
        }
    }

    /** Loads the next page. No-op if already loading or all pages fetched. */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.canLoadMore) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val state = _uiState.value
            val date = state.selectedDate
            val type = state.selectedType
            Log.d(TAG, "Loading more – offset=$currentOffset, date=$date, type=$type")
            val query = state.searchQuery.trim().takeIf { it.isNotEmpty() }
            runCatching {
                repository.fetchHistoryPage(PAGE_SIZE, currentOffset, date = date, type = type, q = query)
            }.onSuccess { response ->
                currentOffset += PAGE_SIZE
                val total = response.total ?: _uiState.value.totalServerCount
                Log.d(TAG, "Page OK – ${response.data.size} items, offset now $currentOffset/$total")
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        totalServerCount = total,
                        canLoadMore = currentOffset < total
                    )
                }
            }.onFailure { throwable ->
                Log.e(TAG, "Load-more failed", throwable)
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    // ── Stats ────────────────────────────────────────────────────────────

    private fun fetchTotalStats() {
        viewModelScope.launch {
            runCatching {
                repository.getTotalStats()
            }.onSuccess { stats ->
                Log.d(TAG, "Stats OK – total=${stats.totalAlerts}, today=${stats.totalToday}, " +
                    "byType=${stats.byType}")
                _uiState.update { it.copy(totalStats = stats) }
            }.onFailure { throwable ->
                Log.e(TAG, "Stats fetch failed", throwable)
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private companion object {
        const val TAG = "AlertHistoryVM"
        const val PAGE_SIZE = 50
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
