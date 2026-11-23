package com.example.pokemonalertsv2.data.checklists

import android.content.Context
import com.example.pokemonalertsv2.data.database.AppDatabase
import com.example.pokemonalertsv2.data.database.ChecklistEntity
import com.example.pokemonalertsv2.data.database.ChecklistEntryEntity
import com.example.pokemonalertsv2.data.godex.GoDexImporter
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.work.ChecklistRefreshWorker
import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import com.example.pokemonalertsv2.data.database.GoDexMarkUndo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Checklists besides the GoDex Hundo collection ([GoDexRepository] keeps that one): other goDex
 * collections added by their public link, read-only and refreshed from goDex, and checklists
 * made in the app.
 */
class ChecklistRepository private constructor(private val appContext: Context) {
    private val dao = AppDatabase.getDatabase(appContext).checklistDao()
    private val speciesDao = AppDatabase.getDatabase(appContext).pokemonSpeciesDao()
    private val importer = GoDexImporter()
    // One import at a time: each already sends about a dozen requests to godex.site at once.
    private val importMutex = Mutex()

    val checklists: Flow<List<ChecklistEntity>> = dao.observeChecklists()

    /** checklistId -> (needed, total). */
    val progress: Flow<Map<String, Pair<Int, Int>>> =
        dao.observeProgress().map { rows -> rows.associate { it.checklistId to (it.needed to it.total) } }

    fun entries(checklistId: String): Flow<List<ChecklistEntryEntity>> = dao.observeEntries(checklistId)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The checklists that badge alerts with their entries, kept ready for the alert cards. */
    val alertData: StateFlow<ChecklistAlertData> = combine(
        dao.observeChecklists().map { all -> all.filter { it.useForAlerts } },
        dao.observeAlertEntries()
    ) { checklists, rows ->
        ChecklistAlertData(checklists, rows.groupBy({ it.checklistId }, { it.asGoDexEntry() }))
    }.stateIn(scope, SharingStarted.Eagerly, ChecklistAlertData())

    /**
     * Adds a goDex collection by its link and imports it. Its kind comes from the collection
     * image; the user can change it. Throws with a readable message when the link is not a
     * collection, is already added, or goDex refuses.
     */
    suspend fun addGoDex(url: String): ChecklistEntity = withContext(Dispatchers.IO) {
        val normalized = GoDexImporter.validateAnyCollectionUrl(url)
        val primary = GoDexRepository.getInstance(appContext).currentConfig()
        require(normalized != primary.url && normalized != primary.writeBackUrl && dao.checklists().none { it.url == normalized }) {
            "This collection is already in your checklists"
        }
        val result = importMutex.withLock { importer.import(normalized) }
        val now = System.currentTimeMillis()
        val checklist = ChecklistEntity(
            id = "godex-${UUID.randomUUID()}",
            source = ChecklistEntity.SOURCE_GODEX,
            title = result.collectionTitle,
            kind = result.kind.name,
            url = result.normalizedUrl,
            sortOrder = (dao.checklists().maxOfOrNull { it.sortOrder } ?: 0) + 1,
            lastSyncMillis = now,
            createdAt = now,
            updatedAt = now
        )
        dao.upsertChecklist(checklist)
        dao.replaceEntries(checklist.id, result.entries.map { it.toChecklistEntry(checklist.id) })
        ChecklistRefreshWorker.schedule(appContext)
        checklist
    }

    /** Imports a goDex checklist again. */
    suspend fun refresh(checklistId: String) = withContext(Dispatchers.IO) {
        val checklist = dao.checklist(checklistId) ?: return@withContext
        val url = checklist.url?.takeIf { checklist.source == ChecklistEntity.SOURCE_GODEX } ?: return@withContext
        val result = importMutex.withLock { importer.import(url) }
        dao.replaceEntries(checklistId, result.entries.map { it.toChecklistEntry(checklistId) })
        dao.upsertChecklist(checklist.copy(lastSyncMillis = System.currentTimeMillis()))
    }

    /** Every goDex checklist, one after another. Returns how many failed. */
    suspend fun refreshAll(): Int {
        var failed = 0
        dao.checklists().filter { it.source == ChecklistEntity.SOURCE_GODEX }.forEach { checklist ->
            runCatching { refresh(checklist.id) }.onFailure { failed++ }
        }
        return failed
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
        dao.deleteChecklist(checklistId)
        scheduleRefresh()
    }

    private fun com.example.pokemonalertsv2.data.database.GoDexEntryEntity.toChecklistEntry(checklistId: String) =
        ChecklistEntryEntity(checklistId, entryKey, pokedexId, formSlug, gender, displayName, needed, spriteUrl)

    companion object {
        /** The national dex so far; the species list also holds alternate forms above 10000. */
        const val MAX_DEX = 1025

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
