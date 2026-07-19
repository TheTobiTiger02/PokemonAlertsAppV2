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

    @Query("UPDATE godex_entries SET needed = :needed WHERE entryKey = :entryKey")
    suspend fun updateNeeded(entryKey: String, needed: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingUpdate(update: GoDexPendingUpdateEntity)

    @Query("DELETE FROM godex_pending_updates WHERE id = :id")
    suspend fun deletePendingUpdate(id: Long)

    @Query("SELECT * FROM godex_pending_updates ORDER BY timestamp ASC")
    suspend fun getPendingUpdates(): List<GoDexPendingUpdateEntity>

    @Query("DELETE FROM godex_pending_updates")
    suspend fun clearPendingUpdates()
}
