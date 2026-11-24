package com.example.pokemonalertsv2.sync

import com.example.pokemonalertsv2.data.database.ChecklistDao
import com.example.pokemonalertsv2.data.database.ChecklistEntity
import com.example.pokemonalertsv2.data.database.ChecklistEntryEntity
import com.example.pokemonalertsv2.data.database.ChecklistPendingDao
import com.example.pokemonalertsv2.data.database.ChecklistPendingEntity
import com.example.pokemonalertsv2.data.checklists.ChecklistRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/** A checklist's settings as they travel; when it last refreshed stays with each device. */
@Serializable
data class ChecklistMeta(
    val source: String,
    val title: String,
    val kind: String,
    val url: String? = null,
    val useForAlerts: Boolean = false,
    val includeEvolutions: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0
)

/** A mark on one of the goDex account's collections, not yet written back to goDex. */
@Serializable
data class ChecklistMark(val caught: Boolean, val revision: Long, val timestamp: Long)

/** One Pokémon of a checklist made in the app, and whether it is still needed. */
@Serializable
data class ChecklistItem(val dex: Int, val name: String, val sprite: String? = null, val needed: Boolean = true)

/**
 * Checklists besides the GoDex Hundo collection. `m/<id>` is a checklist's settings: a goDex
 * collection added by link travels as its link, and each device imports it itself
 * ([onGoDexAdded]). `c/<id>/<entryKey>` is one Pokémon of a checklist made in the app.
 * `p/<id>/<entryKey>` is a mark on one of the goDex account's collections that goDex has not
 * got yet: the other device shows it at once and either may send it (sending it twice is
 * answered "already applied"). The account's collections and their marks are schema 1: an app
 * from before them would import the owner link without the session.
 */
class ChecklistsSyncAdapter(
    private val dao: ChecklistDao,
    private val pending: ChecklistPendingDao? = null,
    private val onGoDexAdded: () -> Unit = {}
) : SyncAdapter {
    override val name = DeviceSync.CHECKLISTS
    private val json = SyncMerge.json

    override suspend fun snapshot(): Map<String, String> {
        val checklists = dao.checklists()
        val local = checklists.filter { it.source == ChecklistEntity.SOURCE_LOCAL }.map { it.id }.toSet()
        return buildMap {
            checklists.forEach { put(META + it.id, json.encodeToString(ChecklistMeta.serializer(), it.meta())) }
            local.forEach { id ->
                dao.entries(id).forEach { entry ->
                    put(itemKey(id, entry.entryKey), json.encodeToString(ChecklistItem.serializer(), ChecklistItem(entry.pokedexId, entry.displayName, entry.spriteUrl, entry.needed)))
                }
            }
            val ids = checklists.mapTo(HashSet()) { it.id }
            pending?.all()?.filter { it.checklistId in ids }?.forEach { mark ->
                put(markKey(mark.checklistId, mark.entryKey), json.encodeToString(ChecklistMark.serializer(), ChecklistMark(mark.caught, mark.revision, mark.timestamp)))
            }
        }
    }

    override fun schemaOf(key: String): Int =
        if (key.startsWith(MARK) || key.startsWith(META + ChecklistRepository.ACCOUNT_ID_PREFIX)) 1 else 0

    override suspend fun apply(upserts: Map<String, String>, deletes: Set<String>): Set<String> {
        val held = LinkedHashSet<String>()
        var goDexAdded = false
        // Settings first, so a checklist exists before its Pokémon arrive.
        upserts.filterKeys { it.startsWith(META) }.forEach { (key, value) ->
            val meta = runCatching { json.decodeFromString(ChecklistMeta.serializer(), value) }.getOrNull()
            if (meta == null) {
                held += key
                return@forEach
            }
            val id = key.removePrefix(META)
            val current = dao.checklist(id)
            if (current == null && meta.source == ChecklistEntity.SOURCE_GODEX) goDexAdded = true
            dao.upsertChecklist(
                ChecklistEntity(
                    id = id, source = meta.source, title = meta.title, kind = meta.kind, url = meta.url,
                    useForAlerts = meta.useForAlerts, includeEvolutions = meta.includeEvolutions, sortOrder = meta.sortOrder,
                    lastSyncMillis = current?.lastSyncMillis ?: 0, createdAt = meta.createdAt, updatedAt = System.currentTimeMillis()
                )
            )
        }
        upserts.filterKeys { it.startsWith(ITEM) }.forEach { (key, value) ->
            val (id, entryKey) = splitItem(key) ?: run { held += key; return@forEach }
            val item = runCatching { json.decodeFromString(ChecklistItem.serializer(), value) }.getOrNull()
            if (item == null || dao.checklist(id) == null) {
                held += key
                return@forEach
            }
            dao.upsertEntries(listOf(ChecklistEntryEntity(id, entryKey, item.dex, null, "none", item.name, item.needed, item.sprite)))
        }
        upserts.filterKeys { it.startsWith(MARK) }.forEach { (key, value) ->
            val (id, entryKey) = split(key, MARK) ?: run { held += key; return@forEach }
            val mark = runCatching { json.decodeFromString(ChecklistMark.serializer(), value) }.getOrNull()
            if (pending == null || mark == null || dao.checklist(id) == null) {
                held += key
                return@forEach
            }
            val current = pending.get(id, entryKey)
            if (current == null || current.revision < mark.revision) {
                pending.applySynced(ChecklistPendingEntity(id, entryKey, mark.caught, mark.revision, mark.timestamp))
            }
        }
        deletes.filter { it.startsWith(ITEM) }.forEach { key ->
            splitItem(key)?.let { (id, entryKey) -> dao.deleteEntries(id, listOf(entryKey)) }
        }
        // Written back on the other device; this one need not send it again.
        deletes.filter { it.startsWith(MARK) }.forEach { key ->
            split(key, MARK)?.let { (id, entryKey) -> pending?.delete(id, entryKey) }
        }
        deletes.filter { it.startsWith(META) }.forEach { dao.deleteChecklist(it.removePrefix(META)) }
        if (goDexAdded) onGoDexAdded()
        return held
    }

    override val changes: Flow<Unit> = combine(
        dao.observeChecklists().map { list -> list.map { it.id to it.meta() } },
        dao.observeLocalEntries().map { rows -> rows.map { Triple(it.checklistId, it.entryKey, it.needed) } },
        pending?.observeAll()?.map { rows -> rows.map { Triple(it.checklistId, it.entryKey, it.revision) } } ?: flowOf(emptyList())
    ) { meta, items, marks -> Triple(meta, items, marks) }
        .distinctUntilChanged()
        .map { }

    private fun ChecklistEntity.meta() = ChecklistMeta(source, title, kind, url, useForAlerts, includeEvolutions, sortOrder, createdAt)

    companion object {
        private const val META = "m/"
        private const val ITEM = "c/"
        private const val MARK = "p/"

        fun itemKey(checklistId: String, entryKey: String) = "$ITEM$checklistId/$entryKey"
        fun markKey(checklistId: String, entryKey: String) = "$MARK$checklistId/$entryKey"

        private fun splitItem(key: String) = split(key, ITEM)

        private fun split(key: String, prefix: String): Pair<String, String>? {
            val rest = key.removePrefix(prefix)
            val slash = rest.indexOf('/')
            return if (slash <= 0 || slash == rest.length - 1) null else rest.substring(0, slash) to rest.substring(slash + 1)
        }
    }
}
