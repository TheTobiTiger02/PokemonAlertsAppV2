package com.example.pokemonalertsv2.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the history_alerts cache table.
 *
 * This is an abstract class (not an interface) so Room can properly wrap
 * [replaceAll] in a database transaction via [@Transaction].
 */
@Dao
abstract class HistoryAlertDao {

    @Query("SELECT * FROM history_alerts ORDER BY historyId DESC")
    abstract fun observeAll(): Flow<List<HistoryAlertEntity>>

    @Query("SELECT COUNT(*) FROM history_alerts")
    abstract suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(alerts: List<HistoryAlertEntity>)

    @Query("DELETE FROM history_alerts")
    abstract suspend fun clearAll()

    /**
     * Atomically replaces the entire cache with [alerts].
     * Used on pull-to-refresh so the UI never sees a brief empty state.
     */
    @Transaction
    open suspend fun replaceAll(alerts: List<HistoryAlertEntity>) {
        clearAll()
        insertAll(alerts)
    }
}
