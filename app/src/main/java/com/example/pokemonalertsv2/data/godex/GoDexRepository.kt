package com.example.pokemonalertsv2.data.godex

import android.content.Context
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.data.database.AppDatabase
import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import com.example.pokemonalertsv2.work.GoDexSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.example.pokemonalertsv2.work.GoDexWriteWorker

class GoDexRepository private constructor(private val appContext: Context) {
    private val dao = AppDatabase.getDatabase(appContext).goDexEntryDao()
    private val preferences = GoDexPreferences(appContext.alertPreferencesDataStore)
    private val importer = GoDexImporter()
    private val evolutionGraph = GoDexEvolutionGraph.load(appContext)
    private val formChangeGraph = GoDexFormChangeGraph.load(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    val entries: StateFlow<List<GoDexEntryEntity>> = dao.observeAll()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val config: StateFlow<GoDexConfig> = preferences.config
        .stateIn(scope, SharingStarted.Eagerly, GoDexConfig())
    private val syncOperationState = MutableStateFlow(GoDexSyncUiState())
    val pendingEntryKeys: StateFlow<Set<String>> = dao.observePendingEntryKeys()
        .combine(config) { keys, _ -> keys.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())
    val syncUiState: StateFlow<GoDexSyncUiState> = combine(
        syncOperationState,
        config,
        dao.observePendingCount()
    ) { operation, cfg, pendingCount ->
        operation.copy(
            pendingCount = pendingCount,
            lastSuccessfulWriteMillis = cfg.lastSuccessfulWriteMillis,
            sessionState = cfg.sessionState,
            errorMessage = operation.errorMessage ?: cfg.lastWriteError
        )
    }.stateIn(scope, SharingStarted.Eagerly, GoDexSyncUiState())

    suspend fun connect(url: String) = synchronize(url, schedulePeriodicRefresh = true)

    private suspend fun synchronize(url: String, schedulePeriodicRefresh: Boolean) = syncMutex.withLock {
        syncOperationState.value = GoDexSyncUiState(isSyncing = true)
        val cookies = preferences.config.first().sessionCookies
        runCatching { importer.import(url, cookies) }
            .onSuccess { result ->
                dao.replaceAllPreservingPending(result.entries)
                persistRefreshedCookies(result.refreshedCookies)
                preferences.saveSuccessfulSync(
                    url = result.normalizedUrl,
                    title = result.collectionTitle,
                    timestamp = System.currentTimeMillis()
                )
                if (schedulePeriodicRefresh) GoDexSyncWorker.schedule(appContext)
                syncOperationState.value = GoDexSyncUiState()
            }
            .onFailure { error ->
                if (error is GoDexAuthenticationException) {
                    persistRefreshedCookies(error.refreshedCookies)
                    preferences.markReauthenticationRequired(
                        error.message ?: "Your GoDex session expired."
                    )
                }
                syncOperationState.value = GoDexSyncUiState(
                    errorMessage = error.message ?: "GoDex synchronization failed"
                )
                throw error
            }
    }

    suspend fun syncConfigured() {
        val cfg = preferences.config.first()
        val url = cfg.url.ifBlank { cfg.writeBackUrl }
        if (url.isBlank()) return
        synchronize(url, schedulePeriodicRefresh = false)
    }

    suspend fun setNotificationFilterEnabled(enabled: Boolean) {
        preferences.setNotificationFilterEnabled(enabled)
    }

    suspend fun saveSessionCookies(cookies: String) {
        preferences.saveSessionCookies(cookies)
        if (cookies.isBlank()) {
            GoDexWriteWorker.cancel(appContext)
            syncOperationState.value = GoDexSyncUiState()
        }
    }

    suspend fun saveAuthenticatedSession(cookies: String, writeBackUrl: String) {
        preferences.saveAuthenticatedSession(cookies, writeBackUrl)
        syncOperationState.value = GoDexSyncUiState()
        GoDexWriteWorker.enqueue(appContext)
        GoDexSyncWorker.enqueueImmediate(appContext)
        GoDexSyncWorker.schedule(appContext)
    }

    suspend fun saveWriteBackUrl(url: String) {
        preferences.saveWriteBackUrl(url)
    }

    suspend fun disconnect() = syncMutex.withLock {
        dao.clearGoDexData()
        preferences.clear()
        syncOperationState.value = GoDexSyncUiState()
        GoDexSyncWorker.cancel(appContext)
        GoDexWriteWorker.cancel(appContext)
    }

    fun match(
        alert: PokemonAlert,
        snapshot: List<GoDexEntryEntity> = entries.value,
        configured: Boolean = config.value.isConnected
    ): GoDexMatchResult = GoDexMatcher.match(
        alert = alert,
        entries = snapshot,
        configured = configured,
        evolutionGraph = evolutionGraph,
        formChangeGraph = formChangeGraph
    )

    fun getCaughtEntries(
        alert: PokemonAlert,
        snapshot: List<GoDexEntryEntity> = entries.value
    ): List<GoDexEntryEntity> = GoDexMatcher.getCaughtEntries(
        alert = alert,
        entries = snapshot,
        evolutionGraph = evolutionGraph,
        formChangeGraph = formChangeGraph
    )

    fun debugEntries(snapshot: List<GoDexEntryEntity> = entries.value): List<GoDexDebugEntry> =
        snapshot.map { entry ->
            val alert = PokemonAlert(
                name = entry.displayName,
                pokedexId = entry.pokedexId,
                pokemonForm = entry.formSlug,
                gender = entry.gender.takeUnless { it == "none" }
            )
            GoDexDebugEntry(
                entryKey = entry.entryKey,
                pokedexId = entry.pokedexId,
                displayName = entry.displayName,
                formSlug = entry.formSlug,
                gender = entry.gender,
                result = match(alert, snapshot, configured = true)
            )
        }.sortedWith(compareBy(GoDexDebugEntry::pokedexId, GoDexDebugEntry::entryKey))

    suspend fun notificationSnapshot(): Pair<GoDexConfig, List<GoDexEntryEntity>> =
        preferences.config.first() to dao.getAll()

    suspend fun currentConfig(): GoDexConfig = preferences.config.first()

    suspend fun refreshIfStale(nowMillis: Long = System.currentTimeMillis()) {
        val current = preferences.config.first()
        if (current.isConnected) {
            GoDexSyncWorker.schedule(appContext)
        }
        if (current.hasSession && dao.getNextPendingUpdate() != null) {
            GoDexWriteWorker.enqueue(appContext)
        }
        if (current.isConnected && nowMillis - current.lastSuccessfulSyncMillis >= STALE_REFRESH_MILLIS) {
            GoDexSyncWorker.enqueueImmediate(appContext)
        }
    }

    suspend fun markAsCaught(entryKey: String, caught: Boolean) = withContext(Dispatchers.IO) {
        dao.setDesiredState(entryKey, caught, System.currentTimeMillis())
        GoDexWriteWorker.enqueue(appContext)
    }

    suspend fun persistRefreshedCookies(cookies: String) {
        if (cookies.isNotBlank() && cookies != preferences.config.first().sessionCookies) {
            preferences.saveSessionCookies(cookies)
        }
    }

    suspend fun markReauthenticationRequired(message: String) {
        preferences.markReauthenticationRequired(message)
    }

    suspend fun recordWriteSuccess(timestamp: Long = System.currentTimeMillis()) {
        preferences.saveWriteSuccess(timestamp)
        syncOperationState.value = GoDexSyncUiState()
    }

    suspend fun recordWriteError(message: String) {
        preferences.saveWriteError(message)
    }

    companion object {
        const val STALE_WARNING_MILLIS = 48L * 60L * 60L * 1000L
        private const val STALE_REFRESH_MILLIS = 4L * 60L * 60L * 1000L

        @Volatile private var instance: GoDexRepository? = null

        fun getInstance(context: Context): GoDexRepository = instance ?: synchronized(this) {
            instance ?: GoDexRepository(context.applicationContext).also { instance = it }
        }
    }
}
