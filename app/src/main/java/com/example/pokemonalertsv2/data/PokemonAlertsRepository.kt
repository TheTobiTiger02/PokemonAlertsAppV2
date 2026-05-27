package com.example.pokemonalertsv2.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.example.pokemonalertsv2.data.database.AlertDao
import com.example.pokemonalertsv2.data.database.AlertEntity
import com.example.pokemonalertsv2.data.database.AppDatabase
import com.example.pokemonalertsv2.data.database.HistoryAlertDao
import com.example.pokemonalertsv2.data.database.toDomain
import com.example.pokemonalertsv2.data.database.toEntity
import com.example.pokemonalertsv2.data.database.toHistoryEntity
import com.example.pokemonalertsv2.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import java.util.LinkedHashSet

class PokemonAlertsRepository @VisibleForTesting internal constructor(
    private val service: PokemonAlertsService,
    private val preferences: AlertPreferencesStore,
    private val alertDao: AlertDao,
    private val historyAlertDao: HistoryAlertDao
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
        if (!fetchMutex.tryLock()) {
            return getLocalAlerts()
        }
        
        return try {
            val remoteAlerts = service.getPokemonAlerts()
            val remoteEntities = remoteAlerts.map { it.toEntity() }
            if (!sameCachedAlerts(alertDao.getAllAlerts(), remoteEntities)) {
                alertDao.replaceAll(remoteEntities)
            }
            clearExpiredAlerts()
            remoteAlerts
        } finally {
            fetchMutex.unlock()
        }
    }

    suspend fun upsertAlert(alert: PokemonAlert) {
        alertDao.insertAlerts(listOf(alert.toEntity()))
    }

    suspend fun getHistory(q: String? = null): List<PokemonAlert> {
        val response = service.getHistory(q = normalizedHistoryQuery(q))
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
        type: String? = null,
        q: String? = null
    ): HistoryResponse {
        val response = service.getHistoryPaged(
            limit = pageSize,
            offset = 0,
            date = date,
            type = type,
            q = normalizedHistoryQuery(q)
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
        type: String? = null,
        q: String? = null
    ): HistoryResponse {
        val response = service.getHistoryPaged(
            limit = limit,
            offset = offset,
            date = date,
            type = type,
            q = normalizedHistoryQuery(q)
        )
        historyAlertDao.insertAll(response.data.map { it.toHistoryEntity() })
        return response
    }

    private fun normalizedHistoryQuery(q: String?): String? = q?.trim()?.takeIf { it.isNotEmpty() }

    /** Wipes the local history cache (e.g. on logout / data-reset). */
    suspend fun clearHistoryCache() {
        historyAlertDao.clearAll()
    }

    // ── Server statistics ────────────────────────────────────────────────

    /** Fetches all-time stats, or stats scoped to [date] when provided. */
    suspend fun getTotalStats(date: String? = null): TotalStatsResponse {
        return service.getTotalStats(date = date)
    }
    
    /**
     * Clears expired alerts from the database.
     * Should be called periodically.
     */
    suspend fun clearExpiredAlerts(nowMillis: Long = System.currentTimeMillis()) {
        val cachedAlerts = alertDao.getAllAlerts()
        val activeAlerts = cachedAlerts.filter { entity ->
            val endMillis = TimeUtils.parseEndTimeToMillis(entity.endTime)
            endMillis == null || endMillis > nowMillis
        }
        if (activeAlerts.size != cachedAlerts.size) {
            alertDao.replaceAll(activeAlerts)
        }
    }

    suspend fun detectNewAlerts(alerts: List<PokemonAlert>): List<PokemonAlert> {
        if (alerts.isEmpty()) return emptyList()
        val seenIds = preferences.getSeenAlertIds()
        return alerts.filter { alert -> alert.seenKeys().none { it in seenIds } }
    }

    suspend fun markAlertsAsSeen(alerts: Collection<PokemonAlert>) {
        if (alerts.isEmpty()) return
        val current = preferences.getSeenAlertIds()
        val updated = LinkedHashSet<String>(current.size + alerts.size).apply {
            addAll(current)
            alerts.forEach { alert -> addAll(alert.seenKeys()) }
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

    companion object {
        private val fetchMutex = Mutex()

        internal fun PokemonAlert.seenKeys(): Set<String> {
            return buildSet {
                id?.let { add("server:$it") }
                add(uniqueId)
            }
        }

        private fun sameCachedAlerts(
            currentEntities: List<AlertEntity>,
            remoteEntities: List<AlertEntity>
        ): Boolean {
            if (currentEntities.size != remoteEntities.size) return false

            val currentSignatures = currentEntities.associate { entity ->
                entity.uniqueId to entity.stableContentHash()
            }
            return remoteEntities.all { entity ->
                currentSignatures[entity.uniqueId] == entity.stableContentHash()
            }
        }

        private fun AlertEntity.stableContentHash(): Int {
            var result = id ?: 0
            result = 31 * result + name.hashCode()
            result = 31 * result + description.hashCode()
            result = 31 * result + imageUrl.hashCode()
            result = 31 * result + longitude.hashCode()
            result = 31 * result + latitude.hashCode()
            result = 31 * result + endTime.hashCode()
            result = 31 * result + type.hashCode()
            result = 31 * result + thumbnailUrl.hashCode()
            result = 31 * result + pokemon.hashCode()
            result = 31 * result + pokemonForm.hashCode()
            result = 31 * result + pokedexId.hashCode()
            result = 31 * result + iv.hashCode()
            result = 31 * result + ivAttack.hashCode()
            result = 31 * result + ivDefense.hashCode()
            result = 31 * result + ivStamina.hashCode()
            result = 31 * result + gender.hashCode()
            result = 31 * result + isShiny.hashCode()
            result = 31 * result + cp.hashCode()
            result = 31 * result + level.hashCode()
            result = 31 * result + isWeatherBoosted.hashCode()
            result = 31 * result + currentWeather.hashCode()
            result = 31 * result + pokemonLocation.hashCode()
            result = 31 * result + gym.hashCode()
            result = 31 * result + pokestop.hashCode()
            result = 31 * result + movesFast.hashCode()
            result = 31 * result + movesCharged.hashCode()
            result = 31 * result + hundoCPL20.hashCode()
            result = 31 * result + hundoCPL25.hashCode()
            result = 31 * result + pvpRankingsJson.hashCode()
            result = 31 * result + gruntType.hashCode()
            result = 31 * result + pokemonRewardsJson.hashCode()
            result = 31 * result + questTask.hashCode()
            result = 31 * result + questReward.hashCode()
            result = 31 * result + requiresAR.hashCode()
            result = 31 * result + newCp.hashCode()
            result = 31 * result + newIv.hashCode()
            result = 31 * result + oldSpecies.hashCode()
            result = 31 * result + oldIv.hashCode()
            result = 31 * result + oldCp.hashCode()
            result = 31 * result + newSpecies.hashCode()
            result = 31 * result + area.hashCode()
            result = 31 * result + alertCreatedAt.hashCode()
            return result
        }

        fun create(context: Context): PokemonAlertsRepository {
            val appContext = context.applicationContext
            val preferences = AlertPreferences(appContext.alertPreferencesDataStore)
            val database = AppDatabase.getDatabase(appContext)
            return PokemonAlertsRepository(
                service = PokemonAlertsApi.service,
                preferences = preferences,
                alertDao = database.alertDao(),
                historyAlertDao = database.historyAlertDao()
            )
        }
    }
}
