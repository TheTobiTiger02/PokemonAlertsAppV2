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
import com.example.pokemonalertsv2.data.counters.PersonalMovesMode
import com.example.pokemonalertsv2.data.counters.PersonalRanking
import com.example.pokemonalertsv2.data.counters.PersonalTeamSlot
import com.example.pokemonalertsv2.data.counters.suggestTeam
import com.example.pokemonalertsv2.data.gamemaster.MegaSpecies
import com.example.pokemonalertsv2.data.counters.PokebattlerUrls
import com.example.pokemonalertsv2.data.counters.RaidBossMoveset
import com.example.pokemonalertsv2.data.counters.RaidBossMovesetSelection
import com.example.pokemonalertsv2.data.counters.toMovesetOrNull
import com.example.pokemonalertsv2.data.counters.toSelection
import com.example.pokemonalertsv2.data.counters.PokebattlerWeather
import com.example.pokemonalertsv2.data.counters.RaidCounterOptions
import com.example.pokemonalertsv2.data.counters.RaidCounterPreferences
import com.example.pokemonalertsv2.data.counters.RaidCountersRepository
import com.example.pokemonalertsv2.data.counters.PokebattlerPersonalProgress
import com.example.pokemonalertsv2.data.counters.PokebattlerAuthRepository
import com.example.pokemonalertsv2.data.counters.PokebattlerPersonalResult
import com.example.pokemonalertsv2.data.gamemaster.GameMasterRepository
import com.example.pokemonalertsv2.data.pokegenie.PokeGenieRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
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
    /** Fixed HP for the boss's tier, from the weekly game master. Available offline. */
    val bossHp: Int? = null,
    val bossShiny: Boolean = false,
    val bossMove1: String? = null,
    val bossMove2: String? = null,
    /** Average plus every concrete moveset returned by Pokebattler. */
    val bossMovesets: List<RaidBossMoveset> = emptyList(),
    val bossMovesetSelection: RaidBossMovesetSelection = RaidBossMovesetSelection.Average,
    val counters: List<DecoratedCounter> = emptyList(),
    val options: RaidCounterOptions = RaidCounterOptions(),
    val source: CounterSourceId = CounterSourceId.ALL_POKEMON,
    val ownedOnly: Boolean = false,
    val ownedMatchCount: Int = 0,
    val pokeGenieCount: Int = 0,
    /** Ranking of the user's own Pokémon copied from Pokébattler. Null until computed. */
    val personal: PersonalRanking? = null,
    /**
     * The suggested six, derived here rather than in the repository.
     *
     * Which Pokémon may take a slot depends on [activeMegaId], which does not change the
     * Pokébattler request — deriving it in the ViewModel is what lets the mega picker take
     * effect instantly instead of forcing a refetch.
     */
    val team: List<PersonalTeamSlot> = emptyList(),
    /** Pokebattler id of the Mega Evolution the trainer has active, or null for none. */
    val activeMegaId: String? = null,
    /** Every mega the game master knows, best-guess-owned first. */
    val megaOptions: List<MegaSpecies> = emptyList(),
    /** Base species in the roster, for marking which megas are actually reachable. */
    val ownedBaseSpeciesIds: Set<String> = emptySet(),
    val personalMovesMode: PersonalMovesMode = PersonalMovesMode.CURRENT,
    val personalLoading: Boolean = false,
    val personalError: String? = null,
    /** Pokébattler's account id for the linked account, or null when signed out. */
    val pokebattlerUserId: String? = null,
    /** Account label, shown so the user can see which Pokébox is being ranked. */
    val pokebattlerAccountName: String? = null,
    val personalProgress: PokebattlerPersonalProgress? = null,
    /**
     * Sprite URL cascade per Pokebattler id, covering the boss and every row kind.
     * Keyed by id so the pure engine types stay free of presentation concerns.
     */
    val spriteUrls: Map<String, List<String>> = emptyMap(),
    /** One or two types per Pokebattler id, for tinting rows and the boss header. */
    val pokemonTypes: Map<String, List<String>> = emptyMap(),
    /** National dex number per id; the Pokemon GO search identifies species by number. */
    val dexNumbers: Map<String, Int> = emptyMap(),
    /** Move type keyed by the uppercased move id *and* its display label. */
    val moveTypes: Map<String, String> = emptyMap(),
    /** Showing a cached list because the network was unavailable. */
    val isStale: Boolean = false,
    /**
     * Battle-setup values Pokébattler could not serve for this boss, as labels.
     *
     * Non-empty means the counters came from Pokébattler's precomputed baseline instead of
     * the chosen setup — see [RaidCounterOptions.precomputedBaseline].
     */
    val degradedOptions: List<String> = emptyList(),
    /** True when [options] weather came from the alert rather than from the saved default. */
    val weatherFromAlert: Boolean = false,
    val fetchedAtMillis: Long = 0L,
    val unresolvedBossName: String? = null,
    val pokebattlerWebUrl: String? = null,
    val errorMessage: String? = null,
    val rateLimited: Boolean = false
) {
    val hasCounters: Boolean get() = counters.isNotEmpty()

    /** Whether the notice block would render anything, so an empty item is never emitted. */
    val hasScreenNotices: Boolean
        get() = degradedOptions.isNotEmpty() ||
            (hasCounters && (isLoading || isStale || rateLimited))

    val hasPersonalNotices: Boolean
        get() = personalLoading ||
            personalError != null ||
            (personalProgress?.serverCandidates ?: 0) > 0 ||
            personalProgress?.substitutedLevels?.isNotEmpty() == true
    val showingPersonal: Boolean get() = source != CounterSourceId.ALL_POKEMON
    val selectedBossMoveset: RaidBossMoveset? get() = bossMovesetSelection.toMovesetOrNull()
}

@Immutable
data class RaidCountersActions(
    val onOptionsChanged: (RaidCounterOptions) -> Unit = {},
    val onBossMovesetChanged: (RaidBossMoveset?) -> Unit = {},
    val onPersonalMovesModeChanged: (PersonalMovesMode) -> Unit = {},
    val onSaveAsDefault: () -> Unit = {},
    val onActiveMegaChanged: (String?) -> Unit = {},
    val onSourceChanged: (CounterSourceId) -> Unit = {},
    val onOwnedOnlyChanged: (Boolean) -> Unit = {},
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
    private val pokebattlerAuth = PokebattlerAuthRepository.getInstance(application)

    private val _uiState = MutableStateFlow(RaidCountersUiState())
    val uiState: StateFlow<RaidCountersUiState> = _uiState

    private var alert: PokemonAlert? = null
    private var boss: BossResolution.Resolved? = null
    private var loadJob: Job? = null
    private var personalJob: Job? = null
    private var personalKey: PersonalRequestKey? = null
    /** Guards the `finally` clear so a superseded job cannot unset a newer job's spinner. */
    private var personalGeneration = 0

    init {
        // The trainer-number Pokébox attempt never worked (there is no public endpoint);
        // drop the leftover value so it cannot resurface.
        viewModelScope.launch { runCatching { preferences.clearPokebattlerTrainerNumber() } }
        // Signing in or out in Settings must reach an already-open raid card.
        viewModelScope.launch {
            pokebattlerAuth.account.collect { account ->
                _uiState.update {
                    it.copy(
                        pokebattlerUserId = account?.userId,
                        pokebattlerAccountName = account?.displayName
                    )
                }
                when {
                    account == null && _uiState.value.source == CounterSourceId.POKEBATTLER_POKEBOX ->
                        onSourceChanged(CounterSourceId.ALL_POKEMON)
                    _uiState.value.source == CounterSourceId.POKEBATTLER_POKEBOX ->
                        computePersonal()
                }
            }
        }
        // Read once at alert time and the "My Pokémon" chip stays disabled after an import
        // done while the card is open, so follow the roster instead.
        viewModelScope.launch {
            pokeGenieRepository.countFlow().collect { count ->
                if (_uiState.value.pokeGenieCount == count) return@collect
                _uiState.update { it.copy(pokeGenieCount = count) }
                if (_uiState.value.showingPersonal) computePersonal()
            }
        }
    }

    /** Idempotent per alert, so recomposition and PiP transitions do not refetch. */
    fun onAlertShown(alert: PokemonAlert) {
        if (this.alert?.uniqueId == alert.uniqueId) return
        this.alert = alert
        this.boss = null
        // A new boss invalidates any in-flight or completed personal ranking.
        personalJob?.cancel()
        personalJob = null
        personalKey = null
        personalGeneration++

        if (!RaidTierParser.isRaid(alert)) {
            _uiState.value = RaidCountersUiState(visible = false)
            return
        }

        viewModelScope.launch {
            val settings = preferences.settings.first()
            val ownedCount = runCatching { pokeGenieRepository.count() }.getOrDefault(0)
            val linkedAccount = pokebattlerAuth.account.value
            // Keep an explicitly saved Pokébattler setup intact. The one exception is a saved
            // weather of "No boost", which is the app default and means "I have not said" —
            // there, the weather the alert itself reports is strictly better than nothing.
            // Any other saved weather is a real choice and is never overwritten.
            val alertWeather = PokebattlerWeather.fromAlertWeather(alert.weatherTo ?: alert.weatherFrom)
                ?.takeIf { settings.options.weather == PokebattlerWeather.NONE }
            val options = alertWeather
                ?.let { settings.options.copy(weather = it) }
                ?: settings.options
            val alertMoveset = alert.moves
                ?.let { RaidBossMoveset(it.fast, it.charged) }
                ?.takeUnless { it.isRandom }
            _uiState.value = RaidCountersUiState(
                visible = true,
                isLoading = true,
                options = options,
                weatherFromAlert = alertWeather != null,
                bossMovesetSelection = alertMoveset.toSelection(),
                bossThumbnailUrl = alert.thumbnailUrl?.takeIf { it.isNotBlank() },
                source = when {
                    settings.source == CounterSourceId.POKEBATTLER_POKEBOX &&
                        linkedAccount != null -> settings.source
                    settings.source == CounterSourceId.POKE_GENIE && ownedCount > 0 -> settings.source
                    else -> CounterSourceId.ALL_POKEMON
                },
                ownedOnly = settings.ownedOnly,
                activeMegaId = settings.activeMegaId,
                megaOptions = _uiState.value.megaOptions,
                pokeGenieCount = ownedCount,
                pokebattlerUserId = linkedAccount?.userId,
                pokebattlerAccountName = linkedAccount?.displayName
            )
            // The game master supplies dex numbers for sprites and rarity for the raid tier
            // fallback, so it can no longer wait for the "My Pokemon" tab. ~420 KB, weekly.
            runCatching { gameMaster.syncIfNeeded() }
            loadMegaOptions()
            resolveAndLoad(force = false)
        }
    }

    fun onOptionsChanged(options: RaidCounterOptions) {
        if (_uiState.value.options == options) return
        // Touching weather at all makes it the user's, so the "from this raid" note goes away.
        val stillFromAlert = _uiState.value.weatherFromAlert &&
            options.weather == _uiState.value.options.weather
        _uiState.update { it.copy(options = options, weatherFromAlert = stillFromAlert) }
        // Debounced: chips are easy to flick through and the service rate-limits.
        scheduleLoad(debounceMillis = OPTION_DEBOUNCE_MILLIS)
        if (_uiState.value.showingPersonal) computePersonal()
    }

    fun onBossMovesetChanged(moveset: RaidBossMoveset?) {
        if (_uiState.value.selectedBossMoveset == moveset) return
        _uiState.update { it.copy(bossMovesetSelection = moveset.toSelection()) }
        scheduleLoad(debounceMillis = OPTION_DEBOUNCE_MILLIS)
        if (_uiState.value.showingPersonal) computePersonal()
    }

    fun onPersonalMovesModeChanged(mode: PersonalMovesMode) {
        if (_uiState.value.personalMovesMode == mode) return
        // Keep the last useful ranking visible while the replacement calculation runs.
        _uiState.update { it.copy(personalMovesMode = mode, personalError = null) }
        if (_uiState.value.showingPersonal) computePersonal()
    }

    fun onSaveAsDefault() {
        viewModelScope.launch { preferences.updateDefaults(_uiState.value.options) }
    }

    /**
     * Records which mega is evolved and re-derives the team on the spot.
     *
     * No request and no cache invalidation: the ranked list is unchanged, only which of its
     * rows is allowed to fill a slot.
     */
    fun onActiveMegaChanged(pokemonId: String?) {
        val normalized = pokemonId?.takeIf { it.isNotBlank() }
        if (_uiState.value.activeMegaId == normalized) return
        _uiState.update {
            it.copy(
                activeMegaId = normalized,
                team = suggestTeam(it.personal?.ranked.orEmpty(), normalized)
            )
        }
        viewModelScope.launch { preferences.setActiveMega(normalized) }
    }

    /**
     * Megas the trainer could actually evolve sort first.
     *
     * The roster is only a hint — a stale CSV should not hide a mega the user really has —
     * so unowned entries stay in the list rather than being filtered out.
     */
    private suspend fun loadMegaOptions() {
        if (_uiState.value.megaOptions.isNotEmpty()) return
        val options = runCatching { gameMaster.megaSpecies() }.getOrDefault(emptyList())
        if (options.isEmpty()) return
        val ownedBases = runCatching { pokeGenieRepository.ownedBaseSpeciesIds() }
            .getOrDefault(emptySet())
        // The picker draws artwork for ids the counters list never mentions, so they are
        // not in the sprite map yet.
        val sprites = runCatching { gameMaster.spriteUrls(options.map { it.pokemonId }) }
            .getOrDefault(emptyMap())
        val types = runCatching { gameMaster.typesFor(options.map { it.pokemonId }) }
            .getOrDefault(emptyMap())
        _uiState.update {
            it.copy(
                ownedBaseSpeciesIds = ownedBases,
                spriteUrls = it.spriteUrls + sprites,
                pokemonTypes = it.pokemonTypes + types,
                megaOptions = options.sortedWith(
                    compareByDescending<MegaSpecies> { mega -> mega.baseSpeciesId in ownedBases }
                        .thenBy { mega -> mega.displayName }
                )
            )
        }
    }

    fun onSourceChanged(source: CounterSourceId) {
        if (source == CounterSourceId.POKEBATTLER_POKEBOX &&
            _uiState.value.pokebattlerUserId.isNullOrBlank()
        ) return
        if (_uiState.value.source == source) return
        _uiState.update { it.copy(source = source) }
        viewModelScope.launch { preferences.setSource(source) }
        if (source != CounterSourceId.ALL_POKEMON) {
            computePersonal()
        } else {
            personalJob?.cancel()
            personalJob = null
            personalKey = null
            personalGeneration++
            _uiState.update { it.copy(personalLoading = false) }
        }
    }

    fun onOwnedOnlyChanged(ownedOnly: Boolean) {
        _uiState.update { it.copy(ownedOnly = ownedOnly) }
        viewModelScope.launch { preferences.setOwnedOnly(ownedOnly) }
    }

    fun onRetry() {
        _uiState.update {
            it.copy(isLoading = true, errorMessage = null, rateLimited = false, personalError = null)
        }
        scheduleLoad(debounceMillis = 0, force = true)
        // The personal ranking has its own requests and its own cache; a retry must redo it
        // even when the request key is unchanged.
        if (_uiState.value.showingPersonal) computePersonal(force = true)
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
        val alert = this.alert ?: run {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

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

        // Tier HP is a local game-master read, so it costs nothing and works offline.
        val tierHp = runCatching { gameMaster.raidTier(resolution.raidLevel)?.hp }.getOrNull()

        val source = AllPokemonSource()
        val requestedMoveset = _uiState.value.selectedBossMoveset
        repository.loadCounters(resolution, _uiState.value.options, source, requestedMoveset)
            .onSuccess { result ->
                val decorated = decorate(result.payload.counters)
                val ids = listOf(resolution.pokemonId) +
                    decorated.map { entry -> entry.counter.pokemonId }
                val sprites = runCatching { gameMaster.spriteUrls(ids) }.getOrDefault(emptyMap())
                val types = runCatching { gameMaster.typesFor(ids) }.getOrDefault(emptyMap())
                val dexes = runCatching { gameMaster.dexNumbersFor(ids) }.getOrDefault(emptyMap())
                // One local read for the whole move table, memoized in the repository.
                val moveTypes = runCatching { gameMaster.moveTypesByLabel() }.getOrDefault(emptyMap())
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        bossPokemonId = resolution.pokemonId,
                        spriteUrls = it.spriteUrls + sprites,
                        pokemonTypes = it.pokemonTypes + types,
                        dexNumbers = it.dexNumbers + dexes,
                        moveTypes = if (moveTypes.isEmpty()) it.moveTypes else moveTypes,
                        bossDisplayName = resolution.displayName,
                        bossCp = result.payload.bossCp ?: resolution.bossCp,
                        bossHp = tierHp,
                        bossShiny = resolution.shiny,
                        bossMove1 = result.payload.bossMove1,
                        bossMove2 = result.payload.bossMove2,
                        bossMovesets = result.payload.availableBossMovesets,
                        counters = decorated,
                        ownedMatchCount = decorated.count { counter -> counter.isOwned },
                        isStale = result.isStale,
                        degradedOptions = result.degradedOptions,
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
                            CountersError.ServerTimeout -> "Pokébattler took too long to answer. Try again."
                            CountersError.Offline -> "Couldn't load counters. Check your connection."
                            is CountersError.Unexpected -> error.message
                            null -> "Couldn't load counters."
                        },
                        pokebattlerWebUrl = PokebattlerUrls.webUrl(resolution.pokemonId)
                    )
                }
                // The personal request has its own cache and may succeed even when the
                // generic list is rate-limited. Do not make the user's ranking depend on it.
                if (_uiState.value.showingPersonal) computePersonal()
            }
    }

    private suspend fun decorate(counters: List<com.example.pokemonalertsv2.data.counters.RaidCounter>) =
        runCatching {
            val index = pokeGenieRepository.index()
            counters.map { DecoratedCounter(it, index.bestOwned(it.pokemonId)) }
        }.getOrElse { counters.map { DecoratedCounter(it) } }

    /**
     * Identifies one personal ranking request. Recomputing is skipped when an identical
     * request is already in flight or has already produced a ranking, which is what stops
     * the generic load finishing (which calls back in here) from cancelling and restarting
     * a personal job that then never gets to finish.
     */
    private data class PersonalRequestKey(
        val bossPokemonId: String,
        val raidLevel: String,
        val source: CounterSourceId,
        val options: RaidCounterOptions,
        val bossMoveset: RaidBossMoveset?,
        val movesMode: PersonalMovesMode,
        val userId: String?
    )

    /**
     * Joins the user's roster to Pokébattler's exact-level responses. There is intentionally no
     * local damage model here: IVs, moveset performance, estimator, time and every secondary
     * metric come from the same server calculation used by the generic tab.
     */
    private fun computePersonal(force: Boolean = false) {
        val resolution = boss ?: return
        val state = _uiState.value
        if (!state.showingPersonal) return
        if (state.source == CounterSourceId.POKE_GENIE && state.pokeGenieCount == 0) {
            personalJob?.cancel()
            personalJob = null
            personalKey = null
            _uiState.update {
                it.copy(
                    personal = null,
                    personalLoading = false,
                    personalProgress = null,
                    personalError = "Import a Poké Genie CSV to rank your Pokémon with Pokébattler."
                )
            }
            return
        }

        val key = PersonalRequestKey(
            bossPokemonId = resolution.pokemonId,
            raidLevel = resolution.raidLevel,
            source = state.source,
            options = state.options,
            bossMoveset = state.selectedBossMoveset,
            movesMode = state.personalMovesMode,
            userId = state.pokebattlerUserId
        )
        if (!force && key == personalKey && (personalJob?.isActive == true || state.personal != null)) {
            return
        }

        personalJob?.cancel()
        personalKey = key
        val generation = ++personalGeneration
        personalJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    personalLoading = true,
                    personalError = null,
                    personalProgress = null
                )
            }

            try {
                val outcome = try {
                    when (key.source) {
                        CounterSourceId.POKE_GENIE -> repository.loadPokeGeniePersonal(
                            boss = resolution,
                            options = key.options,
                            owned = pokeGenieRepository.ownedForPokebattler(),
                            movesMode = key.movesMode,
                            bossMoveset = key.bossMoveset,
                            // Render each bucket as it lands so the card is never a dead
                            // spinner while the second request is still out.
                            onPartial = { partial -> publishPersonal(partial, stillLoading = true) }
                        )
                        CounterSourceId.POKEBATTLER_POKEBOX -> {
                            val userId = key.userId
                            val authorization = pokebattlerAuth.authorizationHeader()
                            if (userId.isNullOrBlank() || authorization == null) {
                                Result.failure(
                                    CountersException(
                                        CountersError.Unexpected(
                                            "Sign in to Pokébattler in Settings to rank your Pokébox."
                                        )
                                    )
                                )
                            } else {
                                repository.loadPokeBoxPersonal(
                                    boss = resolution,
                                    options = key.options,
                                    userId = userId,
                                    authorization = authorization,
                                    bossMoveset = key.bossMoveset
                                )
                            }
                        }
                        // Not reachable — showingPersonal was checked above — but exiting here
                        // must still clear the spinner, which is what the `finally` guarantees.
                        CounterSourceId.ALL_POKEMON -> return@launch
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (throwable: Throwable) {
                    Result.failure(throwable)
                }

                outcome
                    .onSuccess { result -> publishPersonal(result, stillLoading = false) }
                    .onFailure { throwable ->
                        personalKey = null
                        _uiState.update {
                            it.copy(
                                personalError = when (val error = (throwable as? CountersException)?.error) {
                                    CountersError.RateLimited -> "Pokébattler is busy. Retry in a moment."
                                    // Pokébox ranking does this every time for a large box:
                                    // the request is fine, Pokébattler's own server gives up.
                                    CountersError.ServerTimeout ->
                                        if (key.source == CounterSourceId.POKEBATTLER_POKEBOX) {
                                            "Pokébattler could not rank your Pokébox in time — its server " +
                                                "gives up after 30 seconds, which large Pokéboxes exceed. " +
                                                "Your Poké Genie CSV works; use that instead."
                                        } else {
                                            "Pokébattler took too long to answer. Retry in a moment."
                                        }
                                    CountersError.Offline -> "Pokébattler could not be reached. Check your connection and retry."
                                    is CountersError.Unexpected -> error.message
                                    null -> throwable.message ?: "Pokébattler could not rank your Pokémon."
                                }
                            )
                        }
                    }
            } finally {
                // Every non-cancellation exit clears the spinner. A cancellation is always
                // followed by a replacement job that sets it again, or by onSourceChanged.
                if (personalGeneration == generation) {
                    _uiState.update { it.copy(personalLoading = false) }
                }
            }
        }
    }

    /** Applies a full or partial Pokébattler ranking, fetching sprites for the new rows. */
    private suspend fun publishPersonal(result: PokebattlerPersonalResult, stillLoading: Boolean) {
        val ranking = result.ranking
        val ids = ranking.ranked.map { it.pokemonId } + ranking.team.map { it.counter.pokemonId }
        val sprites = runCatching { gameMaster.spriteUrls(ids) }.getOrDefault(emptyMap())
        val types = runCatching { gameMaster.typesFor(ids) }.getOrDefault(emptyMap())
        val dexes = runCatching { gameMaster.dexNumbersFor(ids) }.getOrDefault(emptyMap())
        val moveTypes = runCatching { gameMaster.moveTypesByLabel() }.getOrDefault(emptyMap())
        _uiState.update {
            it.copy(
                personalLoading = stillLoading,
                personal = ranking,
                team = suggestTeam(ranking.ranked, it.activeMegaId),
                personalProgress = result.progress,
                personalError = result.progress
                    .takeUnless { progress -> stillLoading || progress.missingLevels.isEmpty() }
                    ?.let { progress ->
                        "Pokébattler loaded ${progress.completedLevels}/${progress.totalLevels} levels. " +
                            "${progress.missingLevels.joinToString(", ")} could not be loaded; Retry to complete."
                    },
                spriteUrls = it.spriteUrls + sprites,
                pokemonTypes = it.pokemonTypes + types,
                dexNumbers = it.dexNumbers + dexes,
                moveTypes = if (moveTypes.isEmpty()) it.moveTypes else moveTypes
            )
        }
    }

    private companion object {
        const val OPTION_DEBOUNCE_MILLIS = 350L
    }
}
