package com.example.pokemonalertsv2.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row of Pokebattler's `/raids` catalogue, flattened.
 *
 * A boss appears once per tier it has ever been in, so the key is the pair. Most rows sit
 * in the `RAID_LEVEL_UNSET` bucket, which is Pokebattler's list of every Pokémon rather
 * than a queryable tier — it is kept because it supplies the id vocabulary.
 */
@Entity(
    tableName = "pokebattler_raid_bosses",
    primaryKeys = ["tier", "pokemonId"],
    indices = [Index("pokemonId")]
)
data class PokebattlerRaidBossEntity(
    val tier: String,
    val pokemonId: String,
    val displayName: String,
    val cp: Int?,
    val shiny: Boolean,
    val fetchedAt: Long
)

/**
 * A distilled counters result.
 *
 * [cacheKey] encodes the boss, tier, attacker spec and every option, so two different
 * option sets can never collide. Only the top counters with one moveset each are stored,
 * which is what keeps a ~330 KB response down to a few KB on disk.
 */
@Entity(tableName = "raid_counter_cache")
data class RaidCounterCacheEntity(
    @PrimaryKey val cacheKey: String,
    val bossPokemonId: String,
    val raidLevel: String,
    val bossCp: Int?,
    val bossMove1: String?,
    val bossMove2: String?,
    val countersJson: String,
    val availableBossMovesetsJson: String = "[]",
    val fetchedAt: Long
)

/**
 * One Pokémon imported from a Poke Genie CSV export.
 *
 * [matchKey] is the best-guess Pokebattler id and [altMatchKeys] the remaining candidates,
 * newline separated. Both are computed at import time so decorating a counter list is a
 * hash lookup rather than a re-normalization of the whole box.
 */
@Entity(
    tableName = "poke_genie_mons",
    indices = [Index("matchKey")]
)
data class PokeGenieMonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scanIndex: Int?,
    val displayName: String,
    val form: String?,
    val pokedexNumber: Int?,
    val matchKey: String,
    val altMatchKeys: String,
    val cp: Int?,
    val hp: Int?,
    val atkIv: Int?,
    val defIv: Int?,
    val staIv: Int?,
    val levelMin: Double?,
    val levelMax: Double?,
    val level: Double?,
    val quickMove: String?,
    val chargeMove: String?,
    val chargeMove2: String?,
    val gender: String?,
    val shadowState: String,
    val lucky: Boolean,
    val favorite: Boolean,
    val importedAt: Long
)
