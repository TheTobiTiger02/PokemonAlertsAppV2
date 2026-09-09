package com.example.pokemonalertsv2.ui.history

import android.app.Application
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokemonalertsv2.data.FilterCatalogRepository
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.data.insights.SpawnInsights
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
    LAST_30("30 days", 30)
}

@Immutable
data class SpawnInsightsUiState(
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
        if (_uiState.value.hasRun) run()
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
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
