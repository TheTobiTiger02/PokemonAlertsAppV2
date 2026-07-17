package com.example.pokemonalertsv2.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface GoDexEntryDao {
    @Query("SELECT * FROM godex_entries ORDER BY pokedexId, entryKey")
    fun observeAll(): Flow<List<GoDexEntryEntity>>

    @Query("SELECT * FROM godex_entries ORDER BY pokedexId, entryKey")
    suspend fun getAll(): List<GoDexEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<GoDexEntryEntity>)

    @Query("DELETE FROM godex_entries")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(entries: List<GoDexEntryEntity>) {
        clear()
        insertAll(entries)
    }
}
