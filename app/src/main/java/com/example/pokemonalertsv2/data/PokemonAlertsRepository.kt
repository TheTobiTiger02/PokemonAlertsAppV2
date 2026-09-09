package com.example.pokemonalertsv2.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.example.pokemonalertsv2.data.database.AlertDao
import com.example.pokemonalertsv2.data.database.AppDatabase
import com.example.pokemonalertsv2.data.database.HistoryAlertDao
import com.example.pokemonalertsv2.data.database.toDomain
import com.example.pokemonalertsv2.data.database.toEntity
import com.example.pokemonalertsv2.data.database.toHistoryEntity
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.data.insights.InsightsHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        entities.map { it.toDomain() }.filterNot { it.isInvalidated }
    }.flowOn(Dispatchers.Default)

    /**
     * Returns the current list of alerts from the local database without triggering a network call.
     */
    suspend fun getLocalAlerts(): List<PokemonAlert> {
        return alertDao.getAllAlerts().map { it.toDomain() }.filterNot { it.isInvalidated }
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
            val cursor = preferences.getAlertSyncCursor()
            // Cursor 0 on a first run: the server answers with everything, and
            // [firstSync] below makes that a replace rather than an upsert, so
            // a cache of unknown provenance cannot survive.
            val response = service.getPokemonAlerts(
                since = cursor.revision ?: 0L,
                etag = cursor.etag
            )

            if (response.code() == NOT_MODIFIED) {
                // Nothing changed server-side: no body to parse and no cache to
                // rewrite. This is what a scheduled poll almost always gets.
                // The local expiry sweep still runs — it guards against clock
                // skew between this device and the server's own timers, which a
                // 304 says nothing about.
                clearExpiredAlertsLocked()
                return getLocalAlerts()
            }

            val body = response.body()
            if (!response.isSuccessful || body == null) {
                throw IllegalStateException("Alert sync failed with HTTP ${response.code()}")
            }

            val firstSync = cursor.revision == null
            val upserts = body.alerts.filterNot { it.isInvalidated }
            if (body.full || firstSync) {
                alertDao.replaceAll(upserts.map { it.toEntity() })
            } else {
                if (upserts.isNotEmpty()) alertDao.insertAlerts(upserts.map { it.toEntity() })
                // Invalidated alerts arrive as upserts, not removals: the server
                // still holds them, it has just marked them dead.
                val goneUniqueIds = body.removed.map { it.uniqueId } +
                    body.alerts.filter { it.isInvalidated }.map { it.uniqueId }
                if (goneUniqueIds.isNotEmpty()) alertDao.deleteByUniqueIds(goneUniqueIds.distinct())
            }

            clearExpiredAlertsLocked()
            // Written only after the cache has actually been updated: a crash
            // between the two would otherwise advance the cursor past changes
            // that were never applied.
            preferences.updateAlertSyncCursor(
                AlertSyncCursor(revision = body.revision, etag = response.headers()[ETAG_HEADER])
            )
            getLocalAlerts()
        } finally {
            fetchMutex.unlock()
        }
    }

    /** Reads durable weather for [area], including the server freshness flag. */
    suspend fun getCurrentWeather(area: String): CurrentWeatherResponse {
        val normalizedArea = area.trim()
        require(normalizedArea.isNotEmpty()) { "Area must not be blank" }
        return service.getCurrentWeather(normalizedArea)
    }

    /**
     * Stores a pushed alert. Weather changes remove their affected active alerts
     * in the same Room transaction as the weather alert upsert.
     *
     * Serialized with [fetchAlerts] on [fetchMutex]: a sync's read-then-replaceAll
     * must never run between this method's read of the cache and its write, or the
     * freshly pushed alert would be wiped.
     *
     * @return unique IDs removed from the active cache.
     */
    suspend fun processIncomingAlert(alert: PokemonAlert): Set<String> = fetchMutex.withLock {
        processIncomingAlertLocked(alert)
    }

    private suspend fun processIncomingAlertLocked(alert: PokemonAlert): Set<String> {
        if (alert.isInvalidated) {
            alertDao.deleteAlert(alert.uniqueId, alert.id)
            return emptySet()
        }

        if (!alert.isWeatherChange || alert.affectedAlerts.isEmpty()) {
            alertDao.insertAlerts(listOf(alert.toEntity()))
            return emptySet()
        }

        val affectedUniqueIds = alertDao.replaceAffectedWithWeather(
            weatherAlert = alert.toEntity(),
            affectedAlerts = alert.affectedAlerts
        )
        return affectedUniqueIds.toSet()
    }

    suspend fun getHistory(q: String? = null): List<PokemonAlert> {
        val response = service.getHistory(q = normalizedHistoryQuery(q))
        return response.data
    }

    // ── History (offline-first with pagination + server-side filtering) ──

    /** Observe the locally-cached history alerts (Room). */
    val historyAlerts: Flow<List<PokemonAlert>> =
        historyAlertDao.observeAll().map { entities -> entities.map { it.toDomain() } }.flowOn(Dispatchers.Default)

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

    /**
     * Reads a date range of history for aggregation, in memory only.
     *
     * Deliberately does **not** touch [historyAlertDao]: that table is the History
     * tab's list, and [refreshHistory] replaces it wholesale, so caching an
     * insights query here would silently rewrite what the user is looking at.
     *
     * Paged rather than hitting `api/history/all` because the answer is unbounded
     * — a month of quests is tens of thousands of rows — and a cap the caller can
     * see beats an unbounded response it cannot.
     */
    suspend fun fetchInsightsHistory(
        startDate: String,
        endDate: String,
        type: String? = null,
        q: String? = null,
        maxRows: Int = INSIGHTS_MAX_ROWS,
        pageSize: Int = INSIGHTS_PAGE_SIZE
    ): InsightsHistory {
        val rows = mutableListOf<PokemonAlert>()
        var offset = 0
        var total = Int.MAX_VALUE
        while (rows.size < maxRows && offset < total) {
            val response = service.getHistoryPaged(
                limit = minOf(pageSize, maxRows - rows.size),
                offset = offset,
                type = type,
                startDate = startDate,
                endDate = endDate,
                q = normalizedHistoryQuery(q)
            )
            if (response.data.isEmpty()) break
            rows += response.data
            total = response.total ?: rows.size
            offset = (response.offset ?: offset) + response.data.size
        }
        return InsightsHistory(
            alerts = rows,
            hitCap = rows.size >= maxRows && rows.size < total
        )
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
     * Clears expired alerts from the database. Serialized on [fetchMutex] so a
     * concurrent push or sync cannot be wiped by the read-then-replace below.
     */
    suspend fun clearExpiredAlerts(nowMillis: Long = System.currentTimeMillis()) {
        fetchMutex.withLock { clearExpiredAlertsLocked(nowMillis) }
    }

    private suspend fun clearExpiredAlertsLocked(nowMillis: Long = System.currentTimeMillis()) {
        val cachedAlerts = alertDao.getAllAlerts()
        val activeAlerts = cachedAlerts.filter { entity ->
            val endMillis = TimeUtils.parseEndTimeToMillis(entity.endTime)
            !entity.toDomain().isInvalidated && (endMillis == null || endMillis > nowMillis)
        }
        if (activeAlerts.size != cachedAlerts.size) {
            alertDao.replaceAll(activeAlerts)
        }
    }

    /**
     * Returns alerts not yet marked as seen. Guarded by [seenMutex] together with
     * [markAlertsAsSeen]: both are read-modify-write cycles over the same DataStore
     * set, and the FCM path and poll worker can run concurrently on different
     * repository instances.
     */
    suspend fun detectNewAlerts(alerts: List<PokemonAlert>): List<PokemonAlert> {
        if (alerts.isEmpty()) return emptyList()
        return seenMutex.withLock {
            val seenIds = preferences.getSeenAlertIds()
            alerts.filter { alert -> alert.seenKeys().none { it in seenIds } }
        }
    }

    suspend fun markAlertsAsSeen(alerts: Collection<PokemonAlert>) {
        if (alerts.isEmpty()) return
        seenMutex.withLock {
            val current = preferences.getSeenAlertIds()
            val updated = LinkedHashSet<String>(current.size + alerts.size).apply {
                addAll(current)
                alerts.forEach { alert -> addAll(alert.seenKeys()) }
            }
            preferences.updateSeenAlertIds(updated)
        }
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
        private const val NOT_MODIFIED = 304

        /**
         * Enough rows for a month of a single species without letting a broad
         * query (every quest, all areas) turn into an unbounded download.
         */
        const val INSIGHTS_MAX_ROWS = 3_000
        const val INSIGHTS_PAGE_SIZE = 500
        private const val ETAG_HEADER = "ETag"

        private val fetchMutex = Mutex()

        // Serializes seen-set read-modify-write across repository instances
        // (FCM worker, poll worker, and UI each build their own instance).
        private val seenMutex = Mutex()

        internal fun PokemonAlert.seenKeys(): Set<String> {
            return buildSet {
                id?.let { add("server:$it") }
                add(uniqueId)
            }
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
