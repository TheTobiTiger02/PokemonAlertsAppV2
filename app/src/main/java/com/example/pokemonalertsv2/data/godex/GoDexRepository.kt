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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    val syncUiState = MutableStateFlow(GoDexSyncUiState())

    suspend fun connect(url: String) = synchronize(url, schedulePeriodicRefresh = true)

    private suspend fun synchronize(url: String, schedulePeriodicRefresh: Boolean) = syncMutex.withLock {
        syncUiState.value = GoDexSyncUiState(isSyncing = true)
        runCatching { importer.import(url) }
            .onSuccess { result ->
                dao.replaceAll(result.entries)
                preferences.saveSuccessfulSync(
                    url = result.normalizedUrl,
                    title = result.collectionTitle,
                    timestamp = System.currentTimeMillis()
                )
                if (schedulePeriodicRefresh) GoDexSyncWorker.schedule(appContext)
                syncUiState.value = GoDexSyncUiState()
            }
            .onFailure { error ->
                syncUiState.value = GoDexSyncUiState(errorMessage = error.message ?: "GoDex synchronization failed")
                throw error
            }
    }

    suspend fun syncConfigured() {
        val url = preferences.config.first().url
        if (url.isBlank()) return
        synchronize(url, schedulePeriodicRefresh = false)
    }

    suspend fun setNotificationFilterEnabled(enabled: Boolean) {
        preferences.setNotificationFilterEnabled(enabled)
    }

    suspend fun disconnect() = syncMutex.withLock {
        dao.clear()
        preferences.clear()
        syncUiState.value = GoDexSyncUiState()
        GoDexSyncWorker.cancel(appContext)
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

    suspend fun refreshIfStale(nowMillis: Long = System.currentTimeMillis()) {
        val current = preferences.config.first()
        if (current.isConnected && nowMillis - current.lastSuccessfulSyncMillis >= STALE_REFRESH_MILLIS) {
            GoDexSyncWorker.enqueueImmediate(appContext)
        }
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
