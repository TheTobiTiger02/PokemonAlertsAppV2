package com.example.pokemonalertsv2.raidwatch

import android.content.Context
import android.util.Log
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.RaidTierParser
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.data.counters.BossResolution
import com.example.pokemonalertsv2.data.counters.CounterSourceId
import com.example.pokemonalertsv2.data.counters.PersonalMovesMode
import com.example.pokemonalertsv2.data.counters.PersonalTeamSlot
import com.example.pokemonalertsv2.data.counters.PokebattlerAuthRepository
import com.example.pokemonalertsv2.data.counters.PokebattlerWeather
import com.example.pokemonalertsv2.data.counters.PokemonGoSearch
import com.example.pokemonalertsv2.data.counters.RaidBossMoveset
import com.example.pokemonalertsv2.data.counters.RaidCounterPreferences
import com.example.pokemonalertsv2.data.counters.RaidCountersRepository
import com.example.pokemonalertsv2.data.counters.prettifyMoveName
import com.example.pokemonalertsv2.data.counters.suggestTeam
import com.example.pokemonalertsv2.data.gamemaster.GameMasterRepository
import com.example.pokemonalertsv2.data.pokegenie.PokeGenieRepository
import kotlinx.coroutines.flow.first

/**
 * Builds the suggested six for a watched raid without a ViewModel.
 *
 * `RaidCountersViewModel` is the only place a team is derived today, and a notification cannot
 * hold one. Everything underneath it is already Compose-free, so this walks the same path --
 * resolve the boss, rank the trainer's roster against Pokebattler, pick the six -- and reduces
 * the result to a [RaidTeamSnapshot] the notification and the Copy action can read.
 *
 * The option set is assembled exactly the way the screen assembles it, deliberately: the Room
 * cache is keyed on the full option set, so any divergence here would both miss the warmed
 * entry and show a team computed for settings the user never chose.
 */
object RaidTeamPrefetcher {

    private const val TAG = "RaidTeamPrefetcher"

    private const val NO_SOURCE_NOTE =
        "Import a Poké Genie CSV or link Pokébattler to see your team."
    private const val NO_MATCH_NOTE =
        "None of your Pokémon is among Pokébattler's top counters for this boss."

    /** The stored team when it belongs to [alert], otherwise a fresh computation. */
    suspend fun ensure(context: Context, alert: PokemonAlert): RaidTeamSnapshot? {
        val appContext = context.applicationContext
        RaidWatchStore(appContext).currentTeam()
            ?.takeIf { it.alertUniqueId == alert.uniqueId }
            ?.let { return it }
        return compute(appContext, alert)
    }

    /**
     * Ranks the trainer's roster against [alert] and persists the result.
     *
     * Returns null only when there is nothing to say at all -- a non-raid, or a boss
     * Pokebattler cannot resolve -- in which case no snapshot is written and the notification
     * keeps saying the team is still being built. Every other outcome, including "you have no
     * roster", is written as a snapshot carrying a note.
     */
    suspend fun compute(context: Context, alert: PokemonAlert): RaidTeamSnapshot? {
        val appContext = context.applicationContext
        if (!RaidTierParser.isRaid(alert)) return null

        val store = RaidWatchStore(appContext)
        val repository = RaidCountersRepository.getInstance(appContext)
        val gameMaster = GameMasterRepository.getInstance(appContext)
        val pokeGenie = PokeGenieRepository.getInstance(appContext)
        val auth = PokebattlerAuthRepository.getInstance(appContext)
        val settings = RaidCounterPreferences(appContext.alertPreferencesDataStore).settings.first()

        val ownedCount = runCatching { pokeGenie.count() }.getOrDefault(0)
        val account = auth.account.value
        // Mirrors RaidCountersViewModel's source choice for preferPersonalTeam = true.
        val source = when {
            settings.source == CounterSourceId.POKEBATTLER_POKEBOX && account != null ->
                CounterSourceId.POKEBATTLER_POKEBOX
            settings.source == CounterSourceId.POKE_GENIE && ownedCount > 0 ->
                CounterSourceId.POKE_GENIE
            ownedCount > 0 -> CounterSourceId.POKE_GENIE
            account != null -> CounterSourceId.POKEBATTLER_POKEBOX
            else -> null
        }
        if (source == null) {
            return empty(alert, NO_SOURCE_NOTE).also { store.saveTeam(it) }
        }

        // The alert's weather is better than nothing, but only when the user has not chosen
        // one -- "No boost" is the app default and means "I have not said".
        val alertWeather = PokebattlerWeather
            .fromAlertWeather(alert.weatherTo ?: alert.weatherFrom)
            ?.takeIf { settings.options.weather == PokebattlerWeather.NONE }
        val options = alertWeather?.let { settings.options.copy(weather = it) } ?: settings.options
        val bossMoveset = alert.moves
            ?.let { RaidBossMoveset(it.fast, it.charged) }
            ?.takeUnless { it.isRandom }

        runCatching { gameMaster.syncIfNeeded() }
        val boss = when (val resolution = repository.resolveBoss(alert)) {
            is BossResolution.Resolved -> resolution
            is BossResolution.Unresolved -> {
                Log.w(TAG, "Boss unresolved for ${alert.name}: ${resolution.reason}")
                return null
            }
        }

        val outcome = runCatching {
            when (source) {
                CounterSourceId.POKEBATTLER_POKEBOX -> repository.loadPokeBoxPersonal(
                    boss = boss,
                    options = options,
                    userId = account?.userId.orEmpty(),
                    authorization = auth.authorizationHeader(),
                    bossMoveset = bossMoveset
                )
                else -> repository.loadPokeGeniePersonal(
                    boss = boss,
                    options = options,
                    owned = pokeGenie.ownedForPokebattler(),
                    movesMode = PersonalMovesMode.CURRENT,
                    bossMoveset = bossMoveset
                )
            }
        }.getOrElse { Result.failure(it) }

        val ranking = outcome.getOrElse { throwable ->
            Log.w(TAG, "Could not rank the roster for ${alert.name}", throwable)
            // Deliberately no snapshot: a network failure is temporary and the worker retries,
            // so the notification should keep saying the team is on its way.
            return null
        }.ranking

        val team = suggestTeam(ranking.ranked, settings.activeMegaId)
        if (team.isEmpty()) {
            return empty(alert, NO_MATCH_NOTE).also { store.saveTeam(it) }
        }

        val dexNumbers = runCatching {
            gameMaster.dexNumbersFor(team.flatMap { slot -> slot.copies }.map { it.pokemonId })
        }.getOrDefault(emptyMap())

        return RaidTeamSnapshot(
            alertUniqueId = alert.uniqueId,
            members = team.map { it.toMember() },
            goQuery = PokemonGoSearch.teamQuery(team, dexNumbers),
            speciesQuery = PokemonGoSearch.speciesQuery(team, dexNumbers),
            computedAtMillis = System.currentTimeMillis()
        ).also { store.saveTeam(it) }
    }

    private fun PersonalTeamSlot.toMember(): RaidTeamMember = RaidTeamMember(
        displayName = counter.displayName,
        count = count,
        fastMove = prettifyMoveName(counter.fastMove.moveId).orEmpty(),
        chargedMove = prettifyMoveName(counter.chargedMove.moveId).orEmpty()
    )

    private fun empty(alert: PokemonAlert, note: String) = RaidTeamSnapshot(
        alertUniqueId = alert.uniqueId,
        note = note,
        computedAtMillis = System.currentTimeMillis()
    )
}
