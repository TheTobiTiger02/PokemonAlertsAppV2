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
import com.example.pokemonalertsv2.data.counters.CounterSourceId
import com.example.pokemonalertsv2.data.counters.CountersError
import com.example.pokemonalertsv2.data.counters.CountersException
import com.example.pokemonalertsv2.data.counters.DecoratedCounter
import com.example.pokemonalertsv2.data.counters.PersonalCounterEngine
import com.example.pokemonalertsv2.data.counters.MoveLookup
import com.example.pokemonalertsv2.data.counters.PersonalRanking
import com.example.pokemonalertsv2.data.counters.PokebattlerUrls
import com.example.pokemonalertsv2.data.counters.PokebattlerWeather
import com.example.pokemonalertsv2.data.counters.damageMultiplier
import com.example.pokemonalertsv2.data.counters.dodgeFraction
import com.example.pokemonalertsv2.data.counters.toWeatherBoost
import com.example.pokemonalertsv2.data.counters.RaidCounterOptions
import com.example.pokemonalertsv2.data.counters.RaidCounterPreferences
import com.example.pokemonalertsv2.data.counters.RaidCountersRepository
import com.example.pokemonalertsv2.data.gamemaster.GameMasterRepository
import com.example.pokemonalertsv2.data.pokegenie.PokeGenieRepository
import com.example.pokemonalertsv2.data.sim.SimBoss
import com.example.pokemonalertsv2.data.sim.WeatherBoost
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
    val bossPokemonId: String? = null,
    /**
     * The alert feed's own thumbnail, which already names the exact form
     * (`487_f90_a1.png` for Shadow Giratina Altered), so it beats anything we rebuild.
     */
    val bossThumbnailUrl: String? = null,
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
    /** Ranking of the user's own Pokemon, simulated locally. Null until computed. */
    val personal: PersonalRanking? = null,
    val personalLoading: Boolean = false,
    val personalError: String? = null,
    /**
     * Sprite URL cascade per Pokebattler id, covering the boss and every row kind.
     * Keyed by id so the pure engine types stay free of presentation concerns.
     */
    val spriteUrls: Map<String, List<String>> = emptyMap(),
    /** Showing a cached list because the network was unavailable. */
    val isStale: Boolean = false,
    val fetchedAtMillis: Long = 0L,
    val unresolvedBossName: String? = null,
    val pokebattlerWebUrl: String? = null,
    val errorMessage: String? = null,
    val rateLimited: Boolean = false
) {
    val hasCounters: Boolean get() = counters.isNotEmpty()
    val showingPersonal: Boolean get() = source == CounterSourceId.POKE_GENIE
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
    private val gameMaster = GameMasterRepository.getInstance(application)
    private val preferences = RaidCounterPreferences(application.alertPreferencesDataStore)

    private val _uiState = MutableStateFlow(RaidCountersUiState())
    val uiState: StateFlow<RaidCountersUiState> = _uiState

    private var alert: PokemonAlert? = null
    private var boss: BossResolution.Resolved? = null
    private var loadJob: Job? = null
    private var personalJob: Job? = null

    /** Idempotent per alert, so recomposition and PiP transitions do not refetch. */
    fun onAlertShown(alert: PokemonAlert) {
        if (this.alert?.uniqueId == alert.uniqueId) return
        this.alert = alert
        this.boss = null

        if (!RaidTierParser.isRaid(alert)) {
            _uiState.value = RaidCountersUiState(visible = false)
            return
        }

        viewModelScope.launch {
            val settings = preferences.settings.first()
            val ownedCount = runCatching { pokeGenieRepository.count() }.getOrDefault(0)
            // The alert knows the weather at the gym, which beats a stale global default.
            val options = settings.options.copy(
                weather = PokebattlerWeather.fromAlertWeather(alert.currentWeather)
                    ?: settings.options.weather
            )
            _uiState.value = RaidCountersUiState(
                visible = true,
                isLoading = true,
                options = options,
                bossThumbnailUrl = alert.thumbnailUrl?.takeIf { it.isNotBlank() },
                source = if (ownedCount > 0) settings.source else CounterSourceId.ALL_POKEMON,
                ownedOnly = settings.ownedOnly,
                pokeGenieCount = ownedCount
            )
            // The game master supplies dex numbers for sprites and rarity for the raid tier
            // fallback, so it can no longer wait for the "My Pokemon" tab. ~420 KB, weekly.
            runCatching { gameMaster.syncIfNeeded() }
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
        if (source == CounterSourceId.POKE_GENIE && _uiState.value.personal == null) {
            computePersonal()
        }
    }

    fun onOwnedOnlyChanged(ownedOnly: Boolean) {
        _uiState.update { it.copy(ownedOnly = ownedOnly) }
        viewModelScope.launch { preferences.setOwnedOnly(ownedOnly) }
    }

    fun onToggleExpanded() {
        _uiState.update { it.copy(expanded = !it.expanded) }
    }

    fun onRetry() {
        _uiState.update {
            it.copy(isLoading = true, errorMessage = null, rateLimited = false, personalError = null)
        }
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

        val source = AllPokemonSource()
        repository.loadCounters(resolution, _uiState.value.options, source)
            .onSuccess { result ->
                val decorated = decorate(result.payload.counters)
                val sprites = runCatching {
                    gameMaster.spriteUrls(
                        listOf(resolution.pokemonId) + decorated.map { entry -> entry.counter.pokemonId }
                    )
                }.getOrDefault(emptyMap())
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        bossPokemonId = resolution.pokemonId,
                        spriteUrls = it.spriteUrls + sprites,
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
                computePersonal()
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

    private suspend fun decorate(counters: List<com.example.pokemonalertsv2.data.counters.RaidCounter>) =
        runCatching {
            val index = pokeGenieRepository.index()
            counters.map { DecoratedCounter(it, index.bestOwned(it.pokemonId)) }
        }.getOrElse { counters.map { DecoratedCounter(it) } }

    /**
     * Ranks the user's own collection locally.
     *
     * Runs off the main thread inside the repositories it calls; the simulation itself is
     * a few thousand cheap arithmetic passes, which is fast enough to do inline.
     */
    private fun computePersonal() {
        val resolution = boss ?: return
        if (_uiState.value.pokeGenieCount == 0) return

        personalJob?.cancel()
        personalJob = viewModelScope.launch {
            _uiState.update { it.copy(personalLoading = true, personalError = null) }

            val outcome = runCatching {
                if (!gameMaster.syncIfNeeded()) error("Couldn't download Pokemon stats")

                val tier = gameMaster.raidTier(resolution.raidLevel)
                val bossSpecies = gameMaster.simSpecies(resolution.pokemonId)
                    ?: error("No stats for ${resolution.displayName}")

                val moves = gameMaster.allMoves()
                val state = _uiState.value
                val lookup = MoveLookup(moves)

                // Pokebattler reports the boss moveset as "RANDOM" when it averages over all
                // of them, so prefer the concrete moveset the alert itself carries. Without a
                // real moveset the incoming damage falls back to a crude constant, which
                // badly distorts how long each attacker survives.
                val bossFast = moves[state.bossMove1]
                    ?: lookup.fast(alertMoves()?.fast)
                val bossCharged = moves[state.bossMove2]
                    ?: lookup.charged(alertMoves()?.charged)

                val simBoss = SimBoss(
                    species = bossSpecies,
                    cpm = tier?.cpm ?: DEFAULT_BOSS_CPM,
                    hp = tier?.hp ?: DEFAULT_BOSS_HP,
                    fastMove = bossFast,
                    chargedMove = bossCharged,
                    combatTimeSeconds = (tier?.combatTimeMs ?: DEFAULT_COMBAT_TIME_MS) / 1000.0
                )

                val owned = pokeGenieRepository.ownedForSimulation()
                val speciesIds = owned.flatMap { it.matchKeys }.distinct()
                val species = gameMaster.simSpecies(speciesIds)
                val legal = gameMaster.legalMovesFor(species.keys)

                PersonalCounterEngine.rank(
                    owned = owned,
                    boss = simBoss,
                    species = species,
                    moves = moves,
                    legalMoves = legal,
                    weather = state.options.weather.toWeatherBoost(),
                    friendshipMultiplier = state.options.friendship.damageMultiplier,
                    dodgeFraction = state.options.dodge.dodgeFraction
                )
            }

            outcome
                .onSuccess { ranking ->
                    val sprites = runCatching {
                        gameMaster.spriteUrls(
                            ranking.ranked.map { c -> c.pokemonId } +
                                ranking.team.map { slot -> slot.counter.pokemonId }
                        )
                    }.getOrDefault(emptyMap())
                    _uiState.update {
                        it.copy(
                            personalLoading = false,
                            personal = ranking,
                            personalError = null,
                            // Merge, so the Pokebattler entries keep their sprites.
                            spriteUrls = it.spriteUrls + sprites
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            personalLoading = false,
                            personalError = throwable.message ?: "Couldn't rank your Pokémon"
                        )
                    }
                }
        }
    }

    private fun alertMoves() = alert?.moves

    private companion object {
        const val OPTION_DEBOUNCE_MILLIS = 350L
        const val DEFAULT_BOSS_CPM = 0.79
        const val DEFAULT_BOSS_HP = 15_000
        const val DEFAULT_COMBAT_TIME_MS = 180_000
    }
}
