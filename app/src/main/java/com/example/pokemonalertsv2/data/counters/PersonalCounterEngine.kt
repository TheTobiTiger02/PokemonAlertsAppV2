package com.example.pokemonalertsv2.data.counters

import com.example.pokemonalertsv2.data.pokegenie.OwnedPokemon
import com.example.pokemonalertsv2.data.sim.SimAttacker
import com.example.pokemonalertsv2.data.sim.SimBoss
import com.example.pokemonalertsv2.data.sim.SimMove
import com.example.pokemonalertsv2.data.sim.SimSpecies
import com.example.pokemonalertsv2.data.sim.TeamBuilder
import com.example.pokemonalertsv2.data.sim.WeatherBoost
import java.util.Locale

enum class PersonalMovesMode(val label: String) {
    CURRENT("Current moves"),
    BEST_POTENTIAL("Best potential")
}

/** One of the user's Pokemon, scored against the current boss. */
data class PersonalCounter(
    val owned: OwnedPokemon,
    val pokemonId: String,
    val displayName: String,
    val fastMove: SimMove,
    val chargedMove: SimMove,
    /** True when the scan had no moveset and the best legal one was assumed. */
    val movesetAssumed: Boolean,
    val dps: Double,
    val tdo: Double,
    val rating: Double,
    val estimatedAttackers: Double,
    /** The server response level used for this row; CSV IVs never alter it. */
    val evaluatedLevel: Double? = owned.level,
    /** True when the row came directly from Pokébattler rather than the legacy simulator. */
    val serverBacked: Boolean = false,
    /** Anonymous by-level Pokébattler results use perfect IVs; the imported IVs are display-only. */
    val rankingIgnoresIv: Boolean = false,
    val metrics: CounterMetrics = CounterMetrics(
        estimator = estimatedAttackers / 6.0,
        tdo = tdo
    )
)

data class PersonalRanking(
    val ranked: List<PersonalCounter>,
    val team: List<PersonalTeamSlot>,
    val combinedTdo: Double,
    val bossHp: Int,
    /** Damage from the actual suggested six, including sequential faints and relobbies. */
    val teamDamageWithinTimer: Double = combinedTdo,
    val teamDeaths: Double = 0.0,
    val teamTimeToWinSeconds: Double? = null,
    /** Personal rows are authoritative Pokébattler metrics when true. */
    val serverBacked: Boolean = false,
    val evaluatedLevels: List<Double> = emptyList(),
    val missingLevels: List<Double> = emptyList()
) {
    val bossFraction: Double get() = if (bossHp > 0) {
        (teamDamageWithinTimer / bossHp).coerceAtMost(1.0)
    } else 0.0
    val canSolo: Boolean get() = teamDamageWithinTimer >= bossHp && bossHp > 0
}

/** A row of the suggested six: one Pokemon, possibly brought several times. */
data class PersonalTeamSlot(
    val counter: PersonalCounter,
    val count: Int
)

/**
 * Legacy local ranking engine retained for deterministic simulator tests and callers outside
 * the raid-counter screen. The production My Pokémon path is [RaidCountersRepository]'s
 * Pokébattler-backed join and never calls this engine.
 *
 * Ranks a Poke Genie collection against a specific raid boss.
 *
 * Unlike the Pokebattler list, which simulates generic level-40 attackers with perfect
 * IVs, this scores the exact Pokemon the user owns: their level, their IVs, their
 * moveset, and the shadow bonus where it applies.
 *
 * Current mode requires recorded moves. Best-potential mode is explicit: it keeps known moves,
 * fills only missing components with legal choices, and flags those assumptions in the UI.
 */
object PersonalCounterEngine {

    fun rank(
        owned: List<OwnedPokemon>,
        boss: SimBoss,
        species: Map<String, SimSpecies>,
        moves: Map<String, SimMove>,
        /** Legal fast and charged move ids per species, for scans with no recorded moveset. */
        legalMoves: Map<String, Pair<List<String>, List<String>>> = emptyMap(),
        weather: WeatherBoost = WeatherBoost.NONE,
        friendshipMultiplier: Double = 1.0,
        dodgeFraction: Double = 0.0,
        teamSize: Int = TeamBuilder.TEAM_SIZE,
        movesMode: PersonalMovesMode = PersonalMovesMode.CURRENT,
        metric: CounterMetric = CounterMetric.ESTIMATOR,
        trials: Int = 1,
        bosses: List<SimBoss> = emptyList()
    ): PersonalRanking {
        val moveLookup = MoveLookup(moves).withLegalMoves(legalMoves)

        val candidates = owned.mapNotNull { mon ->
            val resolved = resolveSpecies(mon, species) ?: return@mapNotNull null
            val (simSpecies, pokemonId) = resolved
            buildAttacker(
                mon, simSpecies, moveLookup, boss, weather, friendshipMultiplier, dodgeFraction, movesMode,
                metric, bosses
            )
                ?.let { built -> Triple(mon, pokemonId, built) }
        }

        val ranked = TeamBuilder.rank(
            candidates.map { (mon, id, built) -> Triple(mon, id, built) to built.attacker },
            boss,
            weather,
            friendshipMultiplier,
            dodgeFraction,
            metric,
            trials,
            bosses = bosses
        )

        val personal = ranked.map { entry ->
            val (mon, pokemonId, built) = entry.owned
            PersonalCounter(
                owned = mon,
                pokemonId = pokemonId,
                displayName = prettifyPokemonName(pokemonId),
                fastMove = built.attacker.fastMove,
                chargedMove = built.attacker.chargedMove,
                movesetAssumed = built.assumed,
                dps = entry.result.dps,
                tdo = entry.result.tdo,
                rating = entry.result.rating,
                estimatedAttackers = entry.result.estimatedAttackers,
                metrics = entry.result.toCounterMetrics()
            )
        }

        // buildTeam, not take(): it enforces the one-mega-at-a-time rule.
        val suggested = TeamBuilder.buildTeam(ranked, boss, teamSize)
        val teamMembers = suggested.members
        val simulationTargets = bosses.ifEmpty { listOf(boss) }
        val teamSimulations = if (teamMembers.isEmpty()) {
            emptyList()
        } else {
            listOf(
                com.example.pokemonalertsv2.data.sim.RaidSimulator.simulateTeamTrials(
                    team = teamMembers.map { it.attacker },
                    bosses = simulationTargets,
                    weather = weather,
                    friendshipMultiplier = friendshipMultiplier,
                    dodgeFraction = dodgeFraction,
                    trials = trials
                )
            )
        }
        val teamDamage = teamSimulations.map { it.totalDamageWithinTimer }.averageOrNull()
            ?: teamMembers.sumOf { it.result.tdo }
        val teamDeaths = teamSimulations.map { it.deaths }.averageOrNull() ?: 0.0
        val teamTime = teamSimulations.mapNotNull { it.timeToWinSeconds }.averageOrNull()
        val team = TeamBuilder.groupTeam(teamMembers).map { group ->
            val (mon, pokemonId, built) = group.representative.owned
            PersonalTeamSlot(
                counter = PersonalCounter(
                    owned = mon,
                    pokemonId = pokemonId,
                    displayName = prettifyPokemonName(pokemonId),
                    fastMove = built.attacker.fastMove,
                    chargedMove = built.attacker.chargedMove,
                    movesetAssumed = built.assumed,
                    dps = group.representative.result.dps,
                    tdo = group.representative.result.tdo,
                    rating = group.representative.result.rating,
                    estimatedAttackers = group.representative.result.estimatedAttackers,
                    metrics = group.representative.result.toCounterMetrics()
                ),
                count = group.count
            )
        }

        return PersonalRanking(
            ranked = personal,
            team = team,
            combinedTdo = teamMembers.sumOf { it.result.tdo },
            bossHp = boss.hp,
            teamDamageWithinTimer = teamDamage,
            teamDeaths = teamDeaths,
            teamTimeToWinSeconds = teamTime
        )
    }

    private data class BuiltAttacker(val attacker: SimAttacker, val assumed: Boolean)

    /** Walks the candidate ids until one is in the game master. */
    private fun resolveSpecies(
        mon: OwnedPokemon,
        species: Map<String, SimSpecies>
    ): Pair<SimSpecies, String>? {
        mon.matchKeys.forEach { key ->
            val upper = key.uppercase(Locale.ROOT)
            species[upper]?.let { return it to upper }
        }
        return null
    }

    /**
     * Builds the attacker, using the recorded moveset when there is one and otherwise
     * searching the legal movesets for the best pairing against this boss.
     */
    private fun buildAttacker(
        mon: OwnedPokemon,
        species: SimSpecies,
        moveLookup: MoveLookup,
        boss: SimBoss,
        weather: WeatherBoost,
        friendshipMultiplier: Double,
        dodgeFraction: Double,
        movesMode: PersonalMovesMode,
        metric: CounterMetric,
        bosses: List<SimBoss>
    ): BuiltAttacker? {
        val level = mon.level ?: return null
        val atk = mon.atkIv ?: DEFAULT_IV
        val def = mon.defIv ?: DEFAULT_IV
        val sta = mon.staIv ?: DEFAULT_IV

        val recordedFast = moveLookup.fast(mon.quickMove)
        // Poke Genie records a second charge move when one is unlocked, and it is often the
        // better one against a given boss: a Shadow Garchomp with Earth Power AND Breaking
        // Swipe is a strong Dragon counter only if the second move is considered.
        val recordedCharged = listOfNotNull(
            moveLookup.charged(mon.chargeMove),
            moveLookup.charged(mon.chargeMove2)
        ).distinctBy { it.moveId }

        val fastOptions = listOfNotNull(recordedFast)
            .ifEmpty { moveLookup.legalFast(species.pokemonId) }
        val chargedOptions = recordedCharged
            .ifEmpty { moveLookup.legalCharged(species.pokemonId) }
        if (movesMode == PersonalMovesMode.CURRENT && (recordedFast == null || recordedCharged.isEmpty())) {
            return null
        }
        if (fastOptions.isEmpty() || chargedOptions.isEmpty()) return null

        // Only flagged as assumed when the scan did not tell us the moveset at all; picking
        // between two moves the user actually has is a real choice, not a guess.
        val assumed = recordedFast == null || recordedCharged.isEmpty()

        // A complete recorded moveset is already the only legal choice. Avoid simulating it
        // once just to rediscover that fact; this is the common path for large imports.
        if (fastOptions.size == 1 && chargedOptions.size == 1) {
            return BuiltAttacker(
                SimAttacker(species, level, atk, def, sta, fastOptions.single(), chargedOptions.single(), mon.shadow),
                assumed = assumed
            )
        }

        var best: SimAttacker? = null
        var bestScore = Double.NEGATIVE_INFINITY
        fastOptions.forEach { fast ->
            chargedOptions.forEach { charged ->
                val candidate = SimAttacker(species, level, atk, def, sta, fast, charged, mon.shadow)
                val targets = bosses.ifEmpty { listOf(boss) }
                val results = targets.map { target ->
                    com.example.pokemonalertsv2.data.sim.RaidSimulator
                        .simulate(candidate, target, weather, friendshipMultiplier, dodgeFraction)
                }
                val score = when (metric) {
                    CounterMetric.ESTIMATOR,
                    CounterMetric.TIME -> -results.map { it.metricValue(metric) }.average()
                    CounterMetric.OVERALL,
                    CounterMetric.POWER,
                    CounterMetric.TDO -> results.map { it.metricValue(metric) }.average()
                }
                if (score > bestScore) {
                    bestScore = score
                    best = candidate
                }
            }
        }
        return best?.let { BuiltAttacker(it, assumed = assumed) }
    }

    private const val DEFAULT_IV = 10
}

private fun Iterable<Double>.averageOrNull(): Double? {
    val values = toList()
    return values.takeIf { it.isNotEmpty() }?.average()
}

private fun com.example.pokemonalertsv2.data.sim.SimResult.toCounterMetrics() = CounterMetrics(
    estimator = estimator,
    overallPercent = overallPercent,
    powerPercent = powerPercent,
    tdo = tdo,
    deaths = deaths,
    timeToWinSeconds = timeToWinSeconds
)

/**
 * Maps Poke Genie move names onto Pokebattler move ids.
 *
 * Poke Genie writes display names ("Shadow Claw"); Pokebattler uses ids
 * ("SHADOW_CLAW_FAST"). Matching is done on a punctuation-free uppercase key.
 */
class MoveLookup(private val moves: Map<String, SimMove>) {

    private val fastByName: Map<String, SimMove>
    private val chargedByName: Map<String, SimMove>
    private var legalMoves: Map<String, Pair<List<String>, List<String>>> = emptyMap()

    init {
        val fast = HashMap<String, SimMove>()
        val charged = HashMap<String, SimMove>()
        moves.values.forEach { move ->
            val key = displayKey(move.moveId)
            if (move.isFast) fast[key] = move else charged[key] = move
        }
        fastByName = fast
        chargedByName = charged
    }

    fun withLegalMoves(legal: Map<String, Pair<List<String>, List<String>>>): MoveLookup {
        legalMoves = legal
        return this
    }

    fun fast(displayName: String?): SimMove? = displayName?.let { fastByName[displayKey(it)] }

    fun charged(displayName: String?): SimMove? = displayName?.let { chargedByName[displayKey(it)] }

    fun legalFast(pokemonId: String): List<SimMove> =
        legalMoves[pokemonId]?.first.orEmpty().mapNotNull { moves[it] }.filter { it.isFast }

    fun legalCharged(pokemonId: String): List<SimMove> =
        legalMoves[pokemonId]?.second.orEmpty().mapNotNull { moves[it] }.filter { !it.isFast }

    private companion object {
        /** "SHADOW_CLAW_FAST" and "Shadow Claw" both reduce to SHADOWCLAW. */
        fun displayKey(value: String): String = value
            .uppercase(Locale.ROOT)
            .removeSuffix("_FAST")
            .filter { it.isLetterOrDigit() }
    }
}
