package com.example.pokemonalertsv2.megaboost

import android.content.Context
import com.example.pokemonalertsv2.catchroutes.catchBody
import com.example.pokemonalertsv2.data.PokemonAlertsApi
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.data.counters.RaidCounterPreferences
import com.example.pokemonalertsv2.data.gamemaster.GameMasterRepository
import com.example.pokemonalertsv2.data.pokegenie.PokeGenieRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Best mega sheet shows. [live] is null until the first load finishes. */
data class MegaBoostUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val live: LiveSpeciesResponse? = null,
    val area: String = ALL_AREAS,
    val types: Map<LiveSpecies, List<String>> = emptyMap(),
    val megas: List<MegaCandidate> = emptyList(),
    val spriteUrls: Map<String, List<String>> = emptyMap(),
    val activeMegaId: String? = null,
) {
    /** "All" first, then every area with live Pokémon, each with its live total. */
    val areas: List<Pair<String, Int>> get() = live?.let { response ->
        listOf(ALL_AREAS to response.areas.sumOf { it.total }) + response.areas.map { it.area to it.total }
    }.orEmpty()
    val counts: Map<LiveSpecies, Int> get() = live?.let { liveCounts(it, area) }.orEmpty()
    val totals: List<Pair<String, Int>> get() = typeTotals(counts, types)
    val ranking: List<MegaBoostRow> get() = rankMegaBoost(counts, types, megas)
}

/** Loads and holds the sheet's data for as long as [scope] lives (the sheet's composition). */
class MegaBoostController(context: Context, private val scope: CoroutineScope) {
    private val gameMaster = GameMasterRepository.getInstance(context.applicationContext)
    private val pokeGenie = PokeGenieRepository.getInstance(context.applicationContext)
    private val preferences = RaidCounterPreferences(context.applicationContext.alertPreferencesDataStore)
    private val state = MutableStateFlow(MegaBoostUiState())
    val uiState: StateFlow<MegaBoostUiState> = state.asStateFlow()
    private var loadJob: Job? = null

    init {
        scope.launch { preferences.settings.collectLatest { s -> state.update { it.copy(activeMegaId = s.activeMegaId) } } }
    }

    fun load() {
        if (loadJob?.isActive == true) return
        loadJob = scope.launch {
            state.update { it.copy(loading = true, error = null) }
            try {
                if (state.value.megas.isEmpty()) loadMegas()
                val response = PokemonAlertsApi.liveSpeciesService.liveSpecies()
                // catchBody's 404 message is about catch routes; this one names the missing endpoint.
                if (response.code() == 404) throw IllegalStateException("The backend does not serve live Pokémon yet. Deploy the latest backend.")
                val live = response.catchBody()
                val dex = live.areas.flatMap { area -> area.species.map { it.pokemonId } }
                val types = gameMaster.typesBySpeciesForm(dex)
                state.update { current ->
                    // Keep the chosen area while it still has Pokémon; otherwise the busiest one.
                    val area = current.area.takeIf { chosen -> chosen == ALL_AREAS && current.live != null || live.areas.any { it.area == chosen } }
                        ?: live.areas.maxByOrNull { it.total }?.area ?: ALL_AREAS
                    current.copy(live = live, types = current.types + types, area = area)
                }
                if (state.value.megas.isEmpty()) state.update { it.copy(error = "Mega data is not downloaded yet. Check your connection and try again.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(error = e.message ?: "Live Pokémon could not be loaded.") }
            } finally {
                state.update { it.copy(loading = false) }
            }
        }
    }

    private suspend fun loadMegas() {
        runCatching { gameMaster.syncIfNeeded() }
        val megas = runCatching { gameMaster.megaSpecies() }.getOrDefault(emptyList())
        if (megas.isEmpty()) return
        val ids = megas.map { it.pokemonId }
        val owned = runCatching { pokeGenie.ownedBaseSpeciesIds() }.getOrDefault(emptySet())
        val types = runCatching { gameMaster.typesFor(ids) }.getOrDefault(emptyMap())
        val sprites = runCatching { gameMaster.spriteUrls(ids) }.getOrDefault(emptyMap())
        state.update {
            it.copy(
                megas = megas.map { mega -> MegaCandidate(mega.pokemonId, mega.displayName, types[mega.pokemonId].orEmpty(), mega.baseSpeciesId in owned) },
                spriteUrls = sprites,
            )
        }
    }

    fun selectArea(area: String) = state.update { it.copy(area = area) }

    fun setActive(megaId: String) {
        scope.launch { preferences.setActiveMega(megaId) }
    }
}
