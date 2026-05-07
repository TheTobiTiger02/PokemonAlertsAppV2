package com.example.pokemonalertsv2.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PokemonSpeciesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(species: List<PokemonSpeciesEntity>)

    @Query("SELECT * FROM pokemon_species ORDER BY id ASC")
    fun getAllSpecies(): Flow<List<PokemonSpeciesEntity>>

    @Query("SELECT * FROM pokemon_species WHERE name LIKE '%' || :query || '%' ORDER BY id ASC")
    fun searchSpecies(query: String): Flow<List<PokemonSpeciesEntity>>
    
    @Query("SELECT name FROM pokemon_species")
    suspend fun getAllSpeciesNames(): List<String>
    
    @Query("SELECT COUNT(*) FROM pokemon_species")
    suspend fun getCount(): Int
}
