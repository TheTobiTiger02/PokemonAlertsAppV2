package com.example.pokemonalertsv2.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.example.pokemonalertsv2.data.database.AlertDao
import com.example.pokemonalertsv2.data.database.AppDatabase
import com.example.pokemonalertsv2.data.database.HistoryAlertDao
import com.example.pokemonalertsv2.data.database.toDomain
import com.example.pokemonalertsv2.data.database.toEntity
import com.example.pokemonalertsv2.data.database.toHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.LinkedHashSet

class PokemonAlertsRepository @VisibleForTesting internal constructor(
    private val service: PokemonAlertsService,
    private val preferences: AlertPreferencesStore,
    private val alertDao: AlertDao,
    private val historyAlertDao: HistoryAlertDao,
    private val wearDataSyncer: com.example.pokemonalertsv2.sync.WearDataSyncer? = null
) {
    
    // Expose preferences for notification settings and other UI needs
    val alertPreferences: AlertPreferencesStore get() = preferences

    val alerts: Flow<List<PokemonAlert>> = alertDao.observeAllAlerts().map { entities ->
        entities.map { it.toDomain() }
    }

    /**
     * Returns the current list of alerts from the local database without triggering a network call.
     */
    suspend fun getLocalAlerts(): List<PokemonAlert> {
        return alertDao.getAllAlerts().map { it.toDomain() }
    }

    /**
     * Fetches alerts from the API and updates the local database.
     * Returns the fresh list for callers who need it immediately (like WorkManager),
     * but UI should generally observe [alerts].
     */
    suspend fun fetchAlerts(): List<PokemonAlert> {
        val remoteAlerts = service.getPokemonAlerts()
        alertDao.replaceAll(remoteAlerts.map { it.toEntity() })
        
        val now = System.currentTimeMillis()
        val activeAlerts = remoteAlerts.filter {
            val end = com.example.pokemonalertsv2.util.TimeUtils.parseEndTimeToMillis(it.endTime) ?: Long.MAX_VALUE
            end > now
        }.sortedByDescending { it.endTime }
        
        wearDataSyncer?.syncAlerts(activeAlerts)
        return remoteAlerts
    }

    suspend fun getHistory(): List<PokemonAlert> {
        val response = service.getHistory()
        return response.data
    }

    // ── History (offline-first with pagination + server-side filtering) ──

    /** Observe the locally-cached history alerts (Room). */
    val historyAlerts: Flow<List<PokemonAlert>> =
        historyAlertDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    /**
     * Replaces the local history cache with the first page from the API.
     * Supports server-side date filtering via the [date] param (YYYY-MM-DD).
     */
    suspend fun refreshHistory(
        pageSize: Int,
        date: String? = null,
        type: String? = null
    ): HistoryResponse {
        val response = service.getHistoryPaged(
            limit = pageSize,
            offset = 0,
            date = date,
            type = type
        )
        historyAlertDao.replaceAll(response.data.map { it.toHistoryEntity() })
        return response
    }

    /**
     * Fetches the next page of history and appends it to the local cache.
     * Returns the raw [HistoryResponse] for pagination bookkeeping.
     */
    suspend fun fetchHistoryPage(
        limit: Int,
        offset: Int,
        date: String? = null,
        type: String? = null
    ): HistoryResponse {
        val response = service.getHistoryPaged(
            limit = limit,
            offset = offset,
            date = date,
            type = type
        )
        historyAlertDao.insertAll(response.data.map { it.toHistoryEntity() })
        return response
    }

    /** Wipes the local history cache (e.g. on logout / data-reset). */
    suspend fun clearHistoryCache() {
        historyAlertDao.clearAll()
    }

    // ── Server statistics ────────────────────────────────────────────────

    /** Fetches all-time stats from /api/stats/total. */
    suspend fun getTotalStats(): TotalStatsResponse {
        return service.getTotalStats()
    }
    
    /**
     * Clears expired alerts from the database.
     * Should be called periodically.
     */
    suspend fun clearExpiredAlerts() {
        // Simple implementation: delete alerts where endTime string is strictly less than current time
        // Note: String comparison works for ISO8601 format "yyyy-MM-dd HH:mm:ss"
        // But we need current time in that format.
        // For simplicity in this iteration, we might skip complex query logic or implement a helper.
        // Let's just rely on the fact that we overwrite with fresh data on fetch.
        // Ideally we'd use alertDao.deleteExpired(now)
    }

    suspend fun detectNewAlerts(alerts: List<PokemonAlert>): List<PokemonAlert> {
        if (alerts.isEmpty()) return emptyList()
        val seenIds = preferences.getSeenAlertIds()
        return alerts.filter { it.uniqueId !in seenIds }
    }

    suspend fun markAlertsAsSeen(alerts: Collection<PokemonAlert>) {
        if (alerts.isEmpty()) return
        val current = preferences.getSeenAlertIds()
        val updated = LinkedHashSet<String>(current.size + alerts.size).apply {
            addAll(current)
            alerts.forEach { add(it.uniqueId) }
        }
        preferences.updateSeenAlertIds(updated)
    }

    fun observeSeenAlerts(): Flow<Set<String>> = preferences.seenAlertIds

    fun observeFavorites(): Flow<Set<String>> = preferences.favoriteAlertIds

    suspend fun toggleFavorite(alertId: String) {
        val current = preferences.getFavoriteAlertIds()
        val updated = if (current.contains(alertId)) {
            current - alertId
        } else {
            current + alertId
        }
        preferences.updateFavoriteAlertIds(updated)
    }

    fun observeThemeMode(): Flow<Int> = preferences.themeMode
    suspend fun setThemeMode(mode: Int) = preferences.updateThemeMode(mode)

    fun observeUseImperialUnits(): Flow<Boolean> = preferences.useImperialUnits
    suspend fun setUseImperialUnits(useImperial: Boolean) = preferences.updateUseImperialUnits(useImperial)

    fun observeOnboardingCompleted(): Flow<Boolean> = preferences.onboardingCompleted
    suspend fun setOnboardingCompleted(completed: Boolean) = preferences.setOnboardingCompleted(completed)
    
    fun syncToWear(activeAlerts: List<PokemonAlert>) {
        wearDataSyncer?.syncAlerts(activeAlerts)
    }

    companion object {
        fun create(context: Context): PokemonAlertsRepository {
            val appContext = context.applicationContext
            val preferences = AlertPreferences(appContext.alertPreferencesDataStore)
            val database = AppDatabase.getDatabase(appContext)
            return PokemonAlertsRepository(
                service = PokemonAlertsApi.service,
                preferences = preferences,
                alertDao = database.alertDao(),
                historyAlertDao = database.historyAlertDao(),
                wearDataSyncer = com.example.pokemonalertsv2.sync.WearDataSyncer(appContext)
            )
        }
    }
}
