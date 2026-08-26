package com.example.pokemonalertsv2.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Base stats and legal moves for one species or temporary form.
 *
 * Mega and primal forms are stored as their own rows under their own Pokebattler ids, so
 * a Mega Tyranitar in a Poke Genie import is simulated with mega stats.
 */
@Entity(tableName = "pokebattler_species")
data class PokebattlerSpeciesEntity(
    @PrimaryKey val pokemonId: String,
    val type1: String?,
    val type2: String?,
    val baseAttack: Int,
    val baseDefense: Int,
    val baseStamina: Int,
    /** Newline separated move ids. */
    val quickMoves: String,
    val chargedMoves: String,
    /** `POKEMON_RARITY_LEGENDARY` / `_MYTHIC` / `_ULTRA_BEAST`, or null for a common species. */
    val rarity: String? = null,
    /** National dex number, used to build sprite URLs. */
    val dexNumber: Int? = null,
    /** Icon-set form number, e.g. 2719 for Necrozma Dawn Wings -> `800_f2719.png`. */
    val formId: Int? = null,
    /** Icon-set temporary-evolution number for megas, e.g. 2 for Mega Charizard X. */
    val megaEvoId: Int? = null,
    val fetchedAt: Long
)

/** Just the columns the tier fallback and the sprite builder need. */
data class SpeciesLookupRow(
    val pokemonId: String,
    val rarity: String?,
    val dexNumber: Int?,
    val formId: Int?,
    val megaEvoId: Int?
)

@Entity(tableName = "pokebattler_moves")
data class PokebattlerMoveEntity(
    @PrimaryKey val moveId: String,
    val type: String?,
    val power: Double,
    val durationMs: Int,
    val energyDelta: Int,
    val damageWindowStartMs: Int = 0,
    val damageWindowEndMs: Int = 0,
    val fetchedAt: Long
)

/** Fixed per-tier boss stats: HP, CPM and the raid timer. */
@Entity(tableName = "pokebattler_raid_tiers")
data class PokebattlerRaidTierEntity(
    @PrimaryKey val tier: String,
    val hp: Int,
    val cpm: Double,
    val combatTimeMs: Int,
    val fetchedAt: Long
)
