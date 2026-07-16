package com.example.pokemonalertsv2.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.pokemonalertsv2.data.AffectedAlert
import com.example.pokemonalertsv2.data.matches
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

    @Query("DELETE FROM alerts WHERE uniqueId IN (:uniqueIds)")
    abstract suspend fun deleteByUniqueIds(uniqueIds: List<String>)

    @Query("DELETE FROM alerts WHERE uniqueId = :uniqueId OR (:serverId IS NOT NULL AND id = :serverId)")
    abstract suspend fun deleteAlert(uniqueId: String, serverId: Int?)

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

    /** Removes affected alerts and stores their weather-change alert atomically. */
    @Transaction
    open suspend fun replaceAffectedWithWeather(
        weatherAlert: AlertEntity,
        affectedAlerts: List<AffectedAlert>
    ): List<String> {
        val affectedUniqueIds = getAllAlerts()
            .asSequence()
            .filterNot { it.toDomain().isWeatherChange }
            .filter { entity ->
                val candidate = entity.toDomain()
                affectedAlerts.any { affected -> affected.matches(candidate) }
            }
            .map { it.uniqueId }
            .distinct()
            .toList()

        if (affectedUniqueIds.isNotEmpty()) {
            deleteByUniqueIds(affectedUniqueIds)
        }
        insertAlerts(listOf(weatherAlert))
        return affectedUniqueIds
    }
}
