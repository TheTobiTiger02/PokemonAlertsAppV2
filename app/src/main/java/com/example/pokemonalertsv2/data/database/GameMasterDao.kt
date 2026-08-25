package com.example.pokemonalertsv2.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface GameMasterDao {

    @Query("SELECT * FROM pokebattler_species WHERE pokemonId IN (:ids)")
    suspend fun species(ids: List<String>): List<PokebattlerSpeciesEntity>

    @Query("SELECT * FROM pokebattler_species WHERE pokemonId = :id")
    suspend fun species(id: String): PokebattlerSpeciesEntity?

    @Query("SELECT pokemonId, rarity, dexNumber FROM pokebattler_species WHERE pokemonId IN (:ids)")
    suspend fun speciesLookup(ids: List<String>): List<SpeciesLookupRow>

    @Query("SELECT COUNT(*) FROM pokebattler_species")
    suspend fun speciesCount(): Int

    @Query("SELECT MAX(fetchedAt) FROM pokebattler_species")
    suspend fun speciesFetchedAt(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpecies(rows: List<PokebattlerSpeciesEntity>)

    @Query("DELETE FROM pokebattler_species")
    suspend fun clearSpecies()

    @Transaction
    suspend fun replaceSpecies(rows: List<PokebattlerSpeciesEntity>) {
        clearSpecies()
        insertSpecies(rows)
    }

    @Query("SELECT COUNT(*) FROM pokebattler_moves")
    suspend fun moveCount(): Int

    @Query("SELECT * FROM pokebattler_moves")
    suspend fun allMoves(): List<PokebattlerMoveEntity>

    @Query("SELECT * FROM pokebattler_moves WHERE moveId IN (:ids)")
    suspend fun moves(ids: List<String>): List<PokebattlerMoveEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoves(rows: List<PokebattlerMoveEntity>)

    @Query("DELETE FROM pokebattler_moves")
    suspend fun clearMoves()

    @Transaction
    suspend fun replaceMoves(rows: List<PokebattlerMoveEntity>) {
        clearMoves()
        insertMoves(rows)
    }

    @Query("SELECT COUNT(*) FROM pokebattler_raid_tiers")
    suspend fun raidTierCount(): Int

    @Query("SELECT * FROM pokebattler_raid_tiers WHERE tier = :tier")
    suspend fun raidTier(tier: String): PokebattlerRaidTierEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRaidTiers(rows: List<PokebattlerRaidTierEntity>)
}
