package com.example.pokemonalertsv2.data.checklists

import android.content.Context
import com.example.pokemonalertsv2.data.database.AppDatabase
import com.example.pokemonalertsv2.data.database.ChecklistEntity
import com.example.pokemonalertsv2.data.database.ChecklistEntryEntity
import com.example.pokemonalertsv2.data.database.isGoDexAccount
import com.example.pokemonalertsv2.data.godex.GoDexAccountCollection
import com.example.pokemonalertsv2.data.godex.GoDexAccountCollections
import com.example.pokemonalertsv2.data.godex.GoDexAuthenticationException
import com.example.pokemonalertsv2.data.godex.GoDexImportResult
import com.example.pokemonalertsv2.data.godex.GoDexImporter
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.work.ChecklistRefreshWorker
import com.example.pokemonalertsv2.work.GoDexWriteWorker
import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import com.example.pokemonalertsv2.data.database.GoDexMarkUndo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import com.example.pokemonalertsv2.data.dextracker.DexTrackerCollection
import com.example.pokemonalertsv2.data.dextracker.DexTrackerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Checklists besides the GoDex Hundo collection ([GoDexRepository] keeps that one): other goDex
 * collections, refreshed from goDex (read-only when added by share link, marked and written
 * back when they belong to the signed-in account), and checklists made in the app.
 */
class ChecklistRepository private constructor(private val appContext: Context) {
    private val dao = AppDatabase.getDatabase(appContext).checklistDao()
    private val speciesDao = AppDatabase.getDatabase(appContext).pokemonSpeciesDao()
    private val pendingDao = AppDatabase.getDatabase(appContext).checklistPendingDao()
    private val importer = GoDexImporter()
    private val accountCollections = GoDexAccountCollections()
    private val goDex get() = GoDexRepository.getInstance(appContext)

    /** checklistId -> entry keys with a mark not yet written back to goDex. */
    val pendingEntryKeys: Flow<Map<String, Set<String>>> =
        pendingDao.observeAll().map { rows -> rows.groupBy({ it.checklistId }, { it.entryKey }).mapValues { it.value.toSet() } }

    val checklists: Flow<List<ChecklistEntity>> = dao.observeChecklists()

    /** checklistId -> (needed, total). */
    val progress: Flow<Map<String, Pair<Int, Int>>> =
        dao.observeProgress().map { rows -> rows.associate { it.checklistId to (it.needed to it.total) } }

    fun entries(checklistId: String): Flow<List<ChecklistEntryEntity>> = dao.observeEntries(checklistId)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The checklists that badge alerts with their entries, for the alert cards. Dex Tracker
     * collections are read from Dex Tracker; their listeners run only while a screen shows
     * badges, so a background start (a notification) opens none.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val alertData: StateFlow<ChecklistAlertData> = dao.observeChecklists()
        .map { all -> all.filter { it.useForAlerts } }
        .distinctUntilChanged()
        .flatMapLatest { checklists ->
            val dexTrackerIds = checklists.filter { it.source == ChecklistEntity.SOURCE_DEXTRACKER }
                .mapTo(LinkedHashSet()) { it.id.removePrefix(DEXTRACKER_ID_PREFIX) }
            val dexTracker = if (dexTrackerIds.isEmpty() || !DexTrackerRepository.isConfigured) flowOf(emptyMap())
            else DexTrackerRepository.getInstance(appContext).alertEntries(dexTrackerIds)
            combine(dao.observeAlertEntries(), dexTracker) { rows, fromDexTracker ->
                ChecklistAlertData(
                    checklists,
                    rows.groupBy({ it.checklistId }, { it.asGoDexEntry() }) +
                        fromDexTracker.mapKeys { (collectionId, _) -> dexTrackerChecklistId(collectionId) }
                )
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), ChecklistAlertData())

    /** A Dex Tracker collection's settings row, made the first time they are opened. */
    suspend fun dexTrackerSettings(collection: DexTrackerCollection): ChecklistEntity = withContext(Dispatchers.IO) {
        val id = dexTrackerChecklistId(collection.id)
        dao.checklist(id)?.let { existing ->
            if (existing.title == collection.title) existing
            else existing.copy(title = collection.title).also { dao.upsertChecklist(it) }
        } ?: ChecklistEntity(
            id = id,
            source = ChecklistEntity.SOURCE_DEXTRACKER,
            title = collection.title,
            kind = ChecklistKind.ANY.name,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        ).also { dao.upsertChecklist(it) }
    }

    /**
     * Adds a goDex collection by its link and imports it. Its kind comes from the collection
     * image; the user can change it. Throws with a readable message when the link is not a
     * collection, is already added, or goDex refuses. A link to one of the account's own
     * collections is read with the session, as [addGoDexAccount] does.
     */
    suspend fun addGoDex(url: String): ChecklistEntity = withContext(Dispatchers.IO) {
        val normalized = GoDexImporter.validateAnyCollectionUrl(url)
        val primary = goDex.currentConfig()
        require(normalized != primary.url && normalized != primary.writeBackUrl && dao.checklists().none { it.url == normalized }) {
            "This collection is already in your checklists"
        }
        val result = importGoDex(normalized)
        val accountId = GoDexAccountCollections.collectionIdOf(result.normalizedUrl)
        save(accountId?.let(::accountChecklistId) ?: "godex-${UUID.randomUUID()}", settingsFrom = null, result = result)
    }

    /**
     * Adds a collection of the signed-in account: imported with the session, and marks made in
     * the editor are written back to goDex. One already added by its share link becomes this
     * one, keeping its settings.
     */
    suspend fun addGoDexAccount(collection: GoDexAccountCollection): ChecklistEntity = withContext(Dispatchers.IO) {
        val primary = goDex.currentConfig()
        require(collection.url != primary.writeBackUrl && collection.url != primary.url && (collection.publicUrl == null || collection.publicUrl != primary.url)) {
            "This is your Hundo collection"
        }
        val existing = dao.checklists().firstOrNull { it.url == collection.url || (collection.publicUrl != null && it.url == collection.publicUrl) }
        val result = importGoDex(collection.url)
        val id = accountChecklistId(collection.collectionId)
        if (existing != null && existing.id != id) {
            pendingDao.clear(existing.id)
            dao.deleteChecklist(existing.id)
        }
        save(id, existing, result)
    }

    private suspend fun save(id: String, settingsFrom: ChecklistEntity?, result: GoDexImportResult): ChecklistEntity {
        val now = System.currentTimeMillis()
        val checklist = (settingsFrom ?: ChecklistEntity(
            id = id,
            source = ChecklistEntity.SOURCE_GODEX,
            title = result.collectionTitle,
            kind = result.kind.name,
            sortOrder = (dao.checklists().maxOfOrNull { it.sortOrder } ?: 0) + 1,
            createdAt = now
        )).copy(id = id, url = result.normalizedUrl, lastSyncMillis = now, updatedAt = now)
        dao.upsertChecklist(checklist)
        pendingDao.replaceEntriesPreservingPending(id, result.entries.map { it.toChecklistEntry(id) })
        ChecklistRefreshWorker.schedule(appContext)
        return checklist
    }

    /** The signed-in account's collections, as goDex lists them. */
    suspend fun goDexAccountCollections(): List<GoDexAccountCollection> = withContext(Dispatchers.IO) {
        val cookies = goDex.currentConfig().sessionCookies
        check(cookies.isNotBlank()) { "Sign in to goDex first" }
        withSessionHandling {
            accountCollections.fetch(cookies).also { goDex.persistRefreshedCookies(it.refreshedCookies) }.collections
        }
    }

    /** Imports a goDex checklist again. */
    suspend fun refresh(checklistId: String) = withContext(Dispatchers.IO) {
        val checklist = dao.checklist(checklistId) ?: return@withContext
        val url = checklist.url?.takeIf { checklist.source == ChecklistEntity.SOURCE_GODEX } ?: return@withContext
        val result = importGoDex(url)
        pendingDao.replaceEntriesPreservingPending(checklistId, result.entries.map { it.toChecklistEntry(checklistId) })
        dao.upsertChecklist(checklist.copy(lastSyncMillis = System.currentTimeMillis()))
    }

    /**
     * Every goDex checklist, one after another. The account's collections wait while signed
     * out, and refreshing stops at the first expired session. Returns how many failed.
     */
    suspend fun refreshAll(): Int {
        var failed = 0
        val signedIn = goDex.currentConfig().hasSession
        for (checklist in dao.checklists().filter { it.source == ChecklistEntity.SOURCE_GODEX }) {
            if (checklist.isGoDexAccount && !signedIn) continue
            val error = runCatching { refresh(checklist.id) }.exceptionOrNull() ?: continue
            failed++
            if (error is GoDexAuthenticationException) break
        }
        return failed
    }

    /** One goDex import, with the session for the account's own collections. */
    private suspend fun importGoDex(url: String): GoDexImportResult {
        if (GoDexAccountCollections.collectionIdOf(url) == null) return goDex.withImportLock { importer.import(url) }
        val config = goDex.currentConfig()
        check(config.hasSession) { "Sign in to goDex to read this collection" }
        return withSessionHandling {
            goDex.withImportLock { importer.import(url, config.sessionCookies) }
                .also { goDex.persistRefreshedCookies(it.refreshedCookies) }
        }
    }

    private suspend fun <T> withSessionHandling(block: suspend () -> T): T = try {
        block()
    } catch (expired: GoDexAuthenticationException) {
        goDex.persistRefreshedCookies(expired.refreshedCookies)
        goDex.markReauthenticationRequired(expired.message ?: "Your GoDex session expired.")
        throw expired
    }

    /**
     * Marks entries of one of the account's goDex checklists: shown at once, written back to
     * goDex after the undo window. Returns what [undoGoDex] needs.
     */
    suspend fun setGoDexCaught(checklistId: String, entryKeys: Collection<String>, caught: Boolean): List<GoDexMarkUndo> = withContext(Dispatchers.IO) {
        pendingDao.setDesiredStates(checklistId, entryKeys, caught, System.currentTimeMillis()).also {
            if (it.isNotEmpty()) GoDexWriteWorker.enqueue(appContext, afterUndoWindow = true)
        }
    }

    suspend fun undoGoDex(checklistId: String, changes: List<GoDexMarkUndo>) = withContext(Dispatchers.IO) {
        pendingDao.undoDesiredStates(checklistId, changes, System.currentTimeMillis())
        GoDexWriteWorker.enqueue(appContext, afterUndoWindow = true)
    }

    /** Keeps refreshing while any goDex checklist exists; called at app start. */
    suspend fun scheduleRefresh() {
        if (dao.checklists().any { it.source == ChecklistEntity.SOURCE_GODEX }) ChecklistRefreshWorker.schedule(appContext)
        else ChecklistRefreshWorker.cancel(appContext)
    }

    /**
     * Makes a checklist in the app, filled with every species of [dexRange] (all needed), or
     * empty when null. Species come from the PokéAPI list the app keeps.
     */
    suspend fun createLocal(title: String, kind: ChecklistKind, dexRange: IntRange?): ChecklistEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val checklist = ChecklistEntity(
            id = "local-${UUID.randomUUID()}",
            source = ChecklistEntity.SOURCE_LOCAL,
            title = title.trim().ifBlank { "Checklist" },
            kind = kind.name,
            sortOrder = (dao.checklists().maxOfOrNull { it.sortOrder } ?: 0) + 1,
            createdAt = now,
            updatedAt = now
        )
        dao.upsertChecklist(checklist)
        if (dexRange != null) addSpecies(checklist.id, dexRange.toSet())
        checklist
    }

    /** Adds species (base form, needed) to a local checklist; ones already in it stay as they are. */
    suspend fun addSpecies(checklistId: String, dexIds: Set<Int>) = withContext(Dispatchers.IO) {
        val species = speciesDao.getAllSpeciesList().filter { it.id in dexIds && it.id <= MAX_DEX }
        val existing = dao.entries(checklistId).mapTo(HashSet()) { it.entryKey }
        val now = System.currentTimeMillis()
        dao.upsertEntries(
            species.map { localEntry(checklistId, it.id, it.name, it.imageUrl, now) }.filter { it.entryKey !in existing }
        )
    }

    suspend fun removeEntries(checklistId: String, entryKeys: Set<String>) = withContext(Dispatchers.IO) {
        dao.deleteEntries(checklistId, entryKeys)
    }

    /** Marks entries of a local checklist; returns what [undoLocal] needs. */
    suspend fun setLocalCaught(checklistId: String, entryKeys: Set<String>, caught: Boolean): List<GoDexMarkUndo> = withContext(Dispatchers.IO) {
        val changing = dao.entries(checklistId).filter { it.entryKey in entryKeys && it.needed == caught }
        if (changing.isEmpty()) return@withContext emptyList()
        dao.setNeeded(checklistId, changing.map { it.entryKey }, needed = !caught, now = System.currentTimeMillis())
        changing.map { GoDexMarkUndo(it.entryKey, neededBefore = it.needed, previous = null) }
    }

    suspend fun undoLocal(checklistId: String, changes: List<GoDexMarkUndo>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        changes.groupBy { it.neededBefore }.forEach { (needed, group) ->
            dao.setNeeded(checklistId, group.map { it.entryKey }, needed, now)
        }
    }

    suspend fun update(checklist: ChecklistEntity) = withContext(Dispatchers.IO) {
        dao.upsertChecklist(checklist.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(checklistId: String) = withContext(Dispatchers.IO) {
        pendingDao.clear(checklistId)
        dao.deleteChecklist(checklistId)
        scheduleRefresh()
    }

    private fun com.example.pokemonalertsv2.data.database.GoDexEntryEntity.toChecklistEntry(checklistId: String) =
        ChecklistEntryEntity(checklistId, entryKey, pokedexId, formSlug, gender, displayName, needed, spriteUrl)

    companion object {
        /** The national dex so far; the species list also holds alternate forms above 10000. */
        const val MAX_DEX = 1025

        const val DEXTRACKER_ID_PREFIX = "dextracker-"
        fun dexTrackerChecklistId(collectionId: String) = "$DEXTRACKER_ID_PREFIX$collectionId"

        /** The same on every device, so two devices adding one collection make one checklist. */
        const val ACCOUNT_ID_PREFIX = "godex-own-"
        fun accountChecklistId(goDexCollectionId: String) = "$ACCOUNT_ID_PREFIX$goDexCollectionId"

        internal fun localEntry(checklistId: String, dex: Int, name: String, spriteUrl: String?, now: Long) =
            ChecklistEntryEntity(checklistId, "%04d-none".format(dex), dex, null, "none", name, needed = true, spriteUrl = spriteUrl, updatedAt = now)

        @Volatile private var instance: ChecklistRepository? = null

        fun getInstance(context: Context): ChecklistRepository = instance ?: synchronized(this) {
            instance ?: ChecklistRepository(context.applicationContext).also { instance = it }
        }
    }
}

data class ChecklistAlertData(
    val checklists: List<ChecklistEntity> = emptyList(),
    val entries: Map<String, List<GoDexEntryEntity>> = emptyMap()
)
