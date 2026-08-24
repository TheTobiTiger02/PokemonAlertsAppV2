package com.example.pokemonalertsv2.ui.counters

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.RaidTierParser
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.data.counters.AllPokemonSource
import com.example.pokemonalertsv2.data.counters.BossResolution
import com.example.pokemonalertsv2.data.counters.CounterSource
import com.example.pokemonalertsv2.data.counters.CounterSourceId
import com.example.pokemonalertsv2.data.counters.CountersError
import com.example.pokemonalertsv2.data.counters.CountersException
import com.example.pokemonalertsv2.data.counters.DecoratedCounter
import com.example.pokemonalertsv2.data.counters.PokeGenieSource
import com.example.pokemonalertsv2.data.counters.PokebattlerUrls
import com.example.pokemonalertsv2.data.counters.PokebattlerWeather
import com.example.pokemonalertsv2.data.counters.RaidCounterOptions
import com.example.pokemonalertsv2.data.counters.RaidCounterPreferences
import com.example.pokemonalertsv2.data.counters.RaidCountersRepository
import com.example.pokemonalertsv2.data.pokegenie.PokeGenieRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class RaidCountersUiState(
    /** False for non-raid alerts; the card is not composed and nothing is fetched. */
    val visible: Boolean = false,
    val isLoading: Boolean = false,
    val bossDisplayName: String? = null,
    val bossCp: Int? = null,
    val bossMove1: String? = null,
    val bossMove2: String? = null,
    val counters: List<DecoratedCounter> = emptyList(),
    val options: RaidCounterOptions = RaidCounterOptions(),
    val source: CounterSourceId = CounterSourceId.ALL_POKEMON,
    val ownedOnly: Boolean = false,
    val ownedMatchCount: Int = 0,
    val pokeGenieCount: Int = 0,
    val expanded: Boolean = false,
    /** Showing a cached list because the network was unavailable. */
    val isStale: Boolean = false,
    val fetchedAtMillis: Long = 0L,
    val unresolvedBossName: String? = null,
    val pokebattlerWebUrl: String? = null,
    val errorMessage: String? = null,
    val rateLimited: Boolean = false
) {
    /** The list actually rendered, after the owned-only filter. */
    val visibleCounters: List<DecoratedCounter>
        get() = if (ownedOnly) counters.filter { it.isOwned } else counters

    val hasCounters: Boolean get() = counters.isNotEmpty()
}

@Immutable
data class RaidCountersActions(
    val onOptionsChanged: (RaidCounterOptions) -> Unit = {},
    val onSaveAsDefault: () -> Unit = {},
    val onSourceChanged: (CounterSourceId) -> Unit = {},
    val onOwnedOnlyChanged: (Boolean) -> Unit = {},
    val onToggleExpanded: () -> Unit = {},
    val onRetry: () -> Unit = {}
) {
    companion object {
        val Noop = RaidCountersActions()
    }
}

class RaidCountersViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RaidCountersRepository.getInstance(application)
    private val pokeGenieRepository = PokeGenieRepository.getInstance(application)
    private val preferences = RaidCounterPreferences(application.alertPreferencesDataStore)

    private val _uiState = MutableStateFlow(RaidCountersUiState())
    val uiState: StateFlow<RaidCountersUiState> = _uiState

    private var alert: PokemonAlert? = null
    private var boss: BossResolution.Resolved? = null
    private var loadJob: Job? = null

    /** Idempotent per alert, so recomposition and PiP transitions do not refetch. */
    fun onAlertShown(alert: PokemonAlert) {
        if (this.alert?.uniqueId == alert.uniqueId) return
        this.alert = alert

        if (!RaidTierParser.isRaid(alert)) {
            _uiState.value = RaidCountersUiState(visible = false)
            return
        }

        viewModelScope.launch {
            val settings = preferences.settings.first()
            // The alert knows the weather at the gym, which is a better default than
            // whatever the user last picked globally.
            val options = settings.options.copy(
                weather = PokebattlerWeather.fromAlertWeather(alert.currentWeather)
                    ?: settings.options.weather
            )
            _uiState.value = RaidCountersUiState(
                visible = true,
                isLoading = true,
                options = options,
                source = settings.source,
                ownedOnly = settings.ownedOnly,
                pokeGenieCount = settings.pokeGenieCount
            )
            resolveAndLoad(force = false)
        }
    }

    fun onOptionsChanged(options: RaidCounterOptions) {
        if (_uiState.value.options == options) return
        _uiState.update { it.copy(options = options) }
        // Debounced: chips are easy to flick through and the service rate-limits.
        scheduleLoad(debounceMillis = OPTION_DEBOUNCE_MILLIS)
    }

    fun onSaveAsDefault() {
        viewModelScope.launch { preferences.updateDefaults(_uiState.value.options) }
    }

    fun onSourceChanged(source: CounterSourceId) {
        if (_uiState.value.source == source) return
        _uiState.update { it.copy(source = source) }
        viewModelScope.launch { preferences.setSource(source) }
        scheduleLoad(debounceMillis = 0)
    }

    fun onOwnedOnlyChanged(ownedOnly: Boolean) {
        _uiState.update { it.copy(ownedOnly = ownedOnly) }
        viewModelScope.launch { preferences.setOwnedOnly(ownedOnly) }
    }

    fun onToggleExpanded() {
        _uiState.update { it.copy(expanded = !it.expanded) }
    }

    fun onRetry() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, rateLimited = false) }
        scheduleLoad(debounceMillis = 0, force = true)
    }

    private fun scheduleLoad(debounceMillis: Long, force: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (debounceMillis > 0) delay(debounceMillis)
            _uiState.update { it.copy(isLoading = true) }
            resolveAndLoad(force)
        }
    }

    private suspend fun resolveAndLoad(force: Boolean) {
        val alert = this.alert ?: return

        val resolution = boss?.takeIf { !force } ?: run {
            val resolved = if (force) repository.retryResolveBoss(alert) else repository.resolveBoss(alert)
            when (resolved) {
                is BossResolution.Unresolved -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            unresolvedBossName = resolved.attemptedName,
                            errorMessage = resolved.reason,
                            pokebattlerWebUrl = null
                        )
                    }
                    return
                }
                is BossResolution.Resolved -> resolved.also { boss = it }
            }
        }

        val source = sourceFor(_uiState.value.source)
        if (!source.isAvailable()) {
            _uiState.update {
                it.copy(isLoading = false, counters = emptyList(), errorMessage = null)
            }
            return
        }

        repository.loadCounters(resolution, _uiState.value.options, source)
            .onSuccess { result ->
                val decorated = source.decorate(result.payload.counters)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        bossDisplayName = resolution.displayName,
                        bossCp = result.payload.bossCp ?: resolution.bossCp,
                        bossMove1 = result.payload.bossMove1,
                        bossMove2 = result.payload.bossMove2,
                        counters = decorated,
                        ownedMatchCount = decorated.count { counter -> counter.isOwned },
                        isStale = result.isStale,
                        fetchedAtMillis = result.fetchedAtMillis,
                        unresolvedBossName = null,
                        errorMessage = null,
                        rateLimited = false,
                        pokebattlerWebUrl = PokebattlerUrls.webUrl(resolution.pokemonId)
                    )
                }
            }
            .onFailure { throwable ->
                val error = (throwable as? CountersException)?.error
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        rateLimited = error is CountersError.RateLimited,
                        errorMessage = when (error) {
                            CountersError.RateLimited -> "Pokebattler is busy. Try again in a moment."
                            CountersError.Offline -> "Couldn't load counters. Check your connection."
                            is CountersError.Unexpected -> error.message
                            null -> "Couldn't load counters."
                        },
                        pokebattlerWebUrl = PokebattlerUrls.webUrl(resolution.pokemonId)
                    )
                }
            }
    }

    private fun sourceFor(id: CounterSourceId): CounterSource = when (id) {
        CounterSourceId.POKE_GENIE -> PokeGenieSource(pokeGenieRepository)
        // Pokébox is not implemented yet; fall back rather than showing an empty card.
        CounterSourceId.POKEBATTLER_POKEBOX,
        CounterSourceId.ALL_POKEMON -> AllPokemonSource()
    }

    private companion object {
        const val OPTION_DEBOUNCE_MILLIS = 350L
    }
}
