package com.example.pokemonalertsv2.data.gamemaster

import kotlinx.serialization.Serializable

/** `GET /pokemon` — base stats, types and legal movesets for every species. */
@Serializable
data class PokebattlerPokemonResponse(
    val pokemon: List<PbSpecies> = emptyList()
)

@Serializable
data class PbSpecies(
    val pokemonId: String,
    val type: String? = null,
    val type2: String? = null,
    val stats: PbBaseStats? = null,
    val quickMoves: List<String> = emptyList(),
    val cinematicMoves: List<String> = emptyList(),
    val eliteQuickMove: List<String> = emptyList(),
    val eliteCinematicMove: List<String> = emptyList(),
    /** `POKEMON_RARITY_LEGENDARY` / `_MYTHIC` / `_ULTRA_BEAST`, absent for a common species. */
    val rarity: String? = null,
    val pokedex: PbPokedex? = null,
    /** Mega and primal forms carry their own stats under their own ids. */
    val temporaryEvolution: List<PbTemporaryEvolution> = emptyList()
)

@Serializable
data class PbPokedex(
    val pokemonId: String? = null,
    /** National dex number; drives the sprite filename. */
    val pokemonNum: Int? = null
)

@Serializable
data class PbTemporaryEvolution(
    val evolution: String? = null,
    val stats: PbBaseStats? = null,
    val type: String? = null,
    val type2: String? = null
)

@Serializable
data class PbBaseStats(
    val baseStamina: Int = 0,
    val baseAttack: Int = 0,
    val baseDefense: Int = 0
)

/** `GET /moves` — power, duration and energy for every move. */
@Serializable
data class PokebattlerMovesResponse(
    val move: List<PbMove> = emptyList()
)

@Serializable
data class PbMove(
    /**
     * Defaulted because the live payload contains one entry with no id at all, and a
     * required field there would fail deserialization of the entire move list.
     */
    val moveId: String = "",
    val type: String? = null,
    val power: Double = 0.0,
    val durationMs: Int = 0,
    /** Positive for fast moves, negative for charged moves. */
    val energyDelta: Int = 0
)
