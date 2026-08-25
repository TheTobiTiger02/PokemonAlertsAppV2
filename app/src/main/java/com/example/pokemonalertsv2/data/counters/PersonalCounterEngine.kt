package com.example.pokemonalertsv2.data.counters

import com.example.pokemonalertsv2.data.pokegenie.OwnedPokemon
import com.example.pokemonalertsv2.data.sim.SimAttacker
import com.example.pokemonalertsv2.data.sim.SimBoss
import com.example.pokemonalertsv2.data.sim.SimMove
import com.example.pokemonalertsv2.data.sim.SimSpecies
import com.example.pokemonalertsv2.data.sim.TeamBuilder
import com.example.pokemonalertsv2.data.sim.WeatherBoost
import java.util.Locale

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
    val estimatedAttackers: Double
)

data class PersonalRanking(
    val ranked: List<PersonalCounter>,
    val team: List<PersonalTeamSlot>,
    val combinedTdo: Double,
    val bossHp: Int
) {
    val bossFraction: Double get() = if (bossHp > 0) (combinedTdo / bossHp).coerceAtMost(1.0) else 0.0
    val canSolo: Boolean get() = combinedTdo >= bossHp && bossHp > 0
}

/** A row of the suggested six: one Pokemon, possibly brought several times. */
data class PersonalTeamSlot(
    val counter: PersonalCounter,
    val count: Int
)

/**
 * Ranks a Poke Genie collection against a specific raid boss.
 *
 * Unlike the Pokebattler list, which simulates generic level-40 attackers with perfect
 * IVs, this scores the exact Pokemon the user owns: their level, their IVs, their
 * moveset, and the shadow bonus where it applies.
 *
 * Where a scan has no recorded moveset — which is most of them, since Poke Genie only
 * stores moves when the user scans for them — the best legal moveset is assumed and the
 * result is flagged so the UI can say so.
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
        teamSize: Int = TeamBuilder.TEAM_SIZE
    ): PersonalRanking {
        val moveLookup = MoveLookup(moves).withLegalMoves(legalMoves)

        val candidates = owned.mapNotNull { mon ->
            val resolved = resolveSpecies(mon, species) ?: return@mapNotNull null
            val (simSpecies, pokemonId) = resolved
            buildAttacker(mon, simSpecies, moveLookup, boss, weather, friendshipMultiplier, dodgeFraction)
                ?.let { built -> Triple(mon, pokemonId, built) }
        }

        val ranked = TeamBuilder.rank(
            candidates.map { (mon, id, built) -> Triple(mon, id, built) to built.attacker },
            boss, weather, friendshipMultiplier, dodgeFraction
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
                estimatedAttackers = entry.result.estimatedAttackers
            )
        }

        // buildTeam, not take(): it enforces the one-mega-at-a-time rule.
        val suggested = TeamBuilder.buildTeam(ranked, boss, teamSize)
        val teamMembers = suggested.members
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
                    estimatedAttackers = group.representative.result.estimatedAttackers
                ),
                count = group.count
            )
        }

        return PersonalRanking(
            ranked = personal,
            team = team,
            combinedTdo = teamMembers.sumOf { it.result.tdo },
            bossHp = boss.hp
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
        dodgeFraction: Double
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
        if (fastOptions.isEmpty() || chargedOptions.isEmpty()) return null

        // Only flagged as assumed when the scan did not tell us the moveset at all; picking
        // between two moves the user actually has is a real choice, not a guess.
        val assumed = recordedFast == null || recordedCharged.isEmpty()

        var best: SimAttacker? = null
        var bestRating = -1.0
        fastOptions.forEach { fast ->
            chargedOptions.forEach { charged ->
                val candidate = SimAttacker(species, level, atk, def, sta, fast, charged, mon.shadow)
                val rating = com.example.pokemonalertsv2.data.sim.RaidSimulator
                    .simulate(candidate, boss, weather, friendshipMultiplier, dodgeFraction)
                    .rating
                if (rating > bestRating) {
                    bestRating = rating
                    best = candidate
                }
            }
        }
        return best?.let { BuiltAttacker(it, assumed = assumed) }
    }

    private const val DEFAULT_IV = 10
}

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
