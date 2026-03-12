package com.example.pokemonalertsv2.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AlertDao {
    @Query("SELECT * FROM alerts ORDER BY endTime DESC")
    abstract fun observeAllAlerts(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts ORDER BY endTime DESC")
    abstract suspend fun getAllAlerts(): List<AlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAlerts(alerts: List<AlertEntity>)

    @Query("DELETE FROM alerts")
    abstract suspend fun clearAll()
    
    @Query("DELETE FROM alerts WHERE endTime < :currentTime")
    abstract suspend fun deleteExpired(currentTime: String)

    /**
     * Atomically replaces all cached alerts with the fresh server list.
     * This ensures alerts removed server-side (e.g. replaced by a weather
     * change alert) are also removed locally.
     */
    @Transaction
    open suspend fun replaceAll(alerts: List<AlertEntity>) {
        clearAll()
        insertAlerts(alerts)
    }
}
