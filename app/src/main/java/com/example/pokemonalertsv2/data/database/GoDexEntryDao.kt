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

    @Query("SELECT * FROM godex_pending_updates WHERE entryKey = :entryKey")
    suspend fun getPendingUpdate(entryKey: String): GoDexPendingUpdateEntity?

    @Query("SELECT * FROM godex_pending_updates ORDER BY timestamp ASC LIMIT 1")
    suspend fun getNextPendingUpdate(): GoDexPendingUpdateEntity?

    @Query("SELECT * FROM godex_pending_updates ORDER BY timestamp ASC")
    suspend fun getPendingUpdates(): List<GoDexPendingUpdateEntity>

    @Query("SELECT COUNT(*) FROM godex_pending_updates")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT entryKey FROM godex_pending_updates")
    fun observePendingEntryKeys(): Flow<List<String>>

    @Query("DELETE FROM godex_pending_updates WHERE entryKey = :entryKey AND revision = :revision")
    suspend fun deletePendingUpdateIfRevision(entryKey: String, revision: Long): Int

    @Query(
        """
        UPDATE godex_pending_updates
        SET attemptCount = attemptCount + 1, lastError = :error
        WHERE entryKey = :entryKey AND revision = :revision
        """
    )
    suspend fun recordPendingFailure(entryKey: String, revision: Long, error: String): Int

    @Query("DELETE FROM godex_pending_updates")
    suspend fun clearPendingUpdates()

    @Transaction
    suspend fun setDesiredState(entryKey: String, caught: Boolean, now: Long) {
        val current = getPendingUpdate(entryKey)
        val revision = maxOf(now, (current?.revision ?: 0L) + 1L)
        updateNeeded(entryKey, !caught)
        insertPendingUpdate(
            GoDexPendingUpdateEntity(
                entryKey = entryKey,
                caught = caught,
                revision = revision,
                timestamp = now
            )
        )
    }

    @Transaction
    suspend fun replaceAllPreservingPending(entries: List<GoDexEntryEntity>) {
        val desiredStates = getPendingUpdates().associate { it.entryKey to it.caught }
        clear()
        insertAll(
            entries.map { entry ->
                desiredStates[entry.entryKey]?.let { caught ->
                    entry.copy(needed = !caught)
                } ?: entry
            }
        )
    }

    @Transaction
    suspend fun clearGoDexData() {
        clear()
        clearPendingUpdates()
    }
}
