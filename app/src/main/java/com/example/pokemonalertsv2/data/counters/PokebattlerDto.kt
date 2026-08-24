package com.example.pokemonalertsv2.data.counters

import kotlinx.serialization.Serializable

/**
 * Wire types for `fight.pokebattler.com`.
 *
 * Beware the inverted naming: the raid boss is the "defender" in the request path, and
 * the ranked list of *counters* comes back in a field also called `defenders`. So the
 * counters live at `attackers[0].randomMove.defenders`.
 *
 * `numSims`, `filterType`, `monteCarlo` and `seed` are deliberately not modelled: the API
 * returns `numSims` as a JSON string at one nesting level and a number at another, and
 * nothing here needs them. The project-wide `ignoreUnknownKeys = true` drops them.
 */
@Serializable
data class PokebattlerCountersResponse(
    val attackers: List<PbAttackerBlock> = emptyList()
)

/** Describes the boss, despite the name. There is exactly one element. */
@Serializable
data class PbAttackerBlock(
    val pokemonId: String? = null,
    val cp: Int? = null,
    val boss: String? = null,
    val stats: PbStats? = null,
    /** Results against a randomly chosen boss moveset — what the site shows by default. */
    val randomMove: PbMoveset? = null,
    /** Per boss moveset breakdown. */
    val byMove: List<PbMoveset> = emptyList()
)

@Serializable
data class PbMoveset(
    val move1: String? = null,
    val move2: String? = null,
    /** The counters, ranked. */
    val defenders: List<PbDefender> = emptyList()
)

/** One counter Pokémon. */
@Serializable
data class PbDefender(
    val pokemonId: String,
    val cp: Int? = null,
    val stats: PbStats? = null,
    val total: PbResult? = null,
    val byMove: List<PbByMove> = emptyList()
)

@Serializable
data class PbByMove(
    val move1: String? = null,
    val move2: String? = null,
    val result: PbResult? = null
)

@Serializable
data class PbResult(
    /** Estimated number of trainers needed to win. Lower is better. */
    val estimator: Double? = null,
    /** Pokebattler's headline 0..1 score. Higher is better. */
    val overallRating: Double? = null,
    val tdo: Double? = null,
    val power: Double? = null,
    val deaths: Double? = null,
    val effectiveDeaths: Double? = null,
    val potions: Double? = null,
    /** Milliseconds. Present as `combatTime` on totals and `totalCombatTime` per moveset. */
    val combatTime: Double? = null,
    val totalCombatTime: Double? = null,
    val effectiveCombatTime: Double? = null,
    val tdi: Double? = null,
    val shieldRating: Double? = null
)

@Serializable
data class PbStats(
    val attack: Int? = null,
    val defense: Int? = null,
    val stamina: Int? = null,
    /** Comes back as a JSON string, e.g. "40". */
    val level: String? = null,
    val boss: String? = null
)

/** `GET /raids` — the full boss catalogue, used to map alert names onto Pokebattler ids. */
@Serializable
data class PokebattlerRaidsResponse(
    val tiers: List<PbRaidTierBucket> = emptyList()
)

@Serializable
data class PbRaidTierBucket(
    val tier: String? = null,
    val type: String? = null,
    val raids: List<PbRaidEntry> = emptyList()
)

@Serializable
data class PbRaidEntry(
    val pokemon: String? = null,
    val pokemonId: String? = null,
    val cp: Int? = null,
    val shiny: Boolean = false
)
