package com.example.pokemonalertsv2.sync

import com.example.pokemonalertsv2.data.database.ChecklistDao
import com.example.pokemonalertsv2.data.database.ChecklistEntity
import com.example.pokemonalertsv2.data.database.ChecklistEntryEntity
import com.example.pokemonalertsv2.data.database.ChecklistProgressRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChecklistsSyncAdapterTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** The checklist tables in memory. */
    private class FakeChecklistDao : ChecklistDao {
        val lists = MutableStateFlow<Map<String, ChecklistEntity>>(emptyMap())
        val rows = MutableStateFlow<Map<Pair<String, String>, ChecklistEntryEntity>>(emptyMap())

        override fun observeChecklists(): Flow<List<ChecklistEntity>> = lists.map { it.values.sortedBy(ChecklistEntity::id) }
        override suspend fun checklists() = lists.value.values.sortedBy(ChecklistEntity::id)
        override suspend fun checklist(id: String) = lists.value[id]
        override suspend fun upsertChecklist(checklist: ChecklistEntity) { lists.value = lists.value + (checklist.id to checklist) }
        override fun observeEntries(checklistId: String) = rows.map { all -> all.values.filter { it.checklistId == checklistId } }
        override suspend fun entries(checklistId: String) = rows.value.values.filter { it.checklistId == checklistId }.sortedBy { it.entryKey }
        override fun observeAlertEntries() = rows.map { all -> all.values.filter { lists.value[it.checklistId]?.useForAlerts == true } }
        override fun observeLocalEntries() = rows.map { all -> all.values.filter { lists.value[it.checklistId]?.source == ChecklistEntity.SOURCE_LOCAL } }
        override fun observeProgress(): Flow<List<ChecklistProgressRow>> = rows.map { emptyList() }
        override suspend fun upsertEntries(entries: List<ChecklistEntryEntity>) { rows.value = rows.value + entries.associateBy { it.checklistId to it.entryKey } }
        override suspend fun clearEntries(checklistId: String) { rows.value = rows.value.filterKeys { it.first != checklistId } }
        override suspend fun deleteEntries(checklistId: String, entryKeys: Collection<String>) {
            rows.value = rows.value.filterKeys { !(it.first == checklistId && it.second in entryKeys) }
        }
        override suspend fun deleteChecklistRow(id: String) { lists.value = lists.value - id }
        override suspend fun setNeeded(checklistId: String, entryKeys: Collection<String>, needed: Boolean, now: Long): Int {
            var changed = 0
            rows.value = rows.value.mapValues { (key, row) ->
                if (key.first == checklistId && key.second in entryKeys && row.needed != needed) { changed++; row.copy(needed = needed, updatedAt = now) } else row
            }
            return changed
        }
    }

    private fun local(id: String) = ChecklistEntity(id = id, source = ChecklistEntity.SOURCE_LOCAL, title = "Shinies", kind = "SHINY", useForAlerts = true)
    private fun item(id: String, dex: Int, needed: Boolean) =
        ChecklistEntryEntity(id, "%04d-none".format(dex), dex, null, "none", "#$dex", needed)

    @Test
    fun localChecklistsTravelWithTheirPokemonAndGoDexLinksAsSettingsOnly() = runBlocking {
        val a = FakeChecklistDao()
        a.upsertChecklist(local("l1"))
        a.upsertEntries(listOf(item("l1", 1, needed = true), item("l1", 4, needed = false)))
        a.upsertChecklist(ChecklistEntity(id = "g1", source = ChecklistEntity.SOURCE_GODEX, title = "Lucky", kind = "LUCKY", url = "https://godex.site/public-collection/X", lastSyncMillis = 99))
        a.upsertEntries(listOf(item("g1", 25, needed = true)))
        val snapshot = ChecklistsSyncAdapter(a).snapshot()
        assertEquals(setOf("m/g1", "m/l1", "c/l1/0001-none", "c/l1/0004-none"), snapshot.keys)

        val b = FakeChecklistDao()
        var imports = 0
        val held = ChecklistsSyncAdapter(b) { imports++ }.apply(snapshot, emptySet())
        assertTrue(held.isEmpty())
        assertEquals("Shinies", b.checklist("l1")?.title)
        assertEquals(listOf(true, false), b.entries("l1").map { it.needed })
        assertEquals("a goDex link is imported on the device itself", 1, imports)
        assertEquals(0L, b.checklist("g1")?.lastSyncMillis)
        assertEquals(emptyList<ChecklistEntryEntity>(), b.entries("g1"))

        ChecklistsSyncAdapter(b).apply(emptyMap(), setOf("c/l1/0004-none", "m/g1"))
        assertEquals(listOf("0001-none"), b.entries("l1").map { it.entryKey })
        assertNull(b.checklist("g1"))
    }

    @Test
    fun aServerWithoutTheCollectionDoesNotFailTheSync() = runBlocking {
        val server = FakeServer()
        val inner = server.transport()
        val transport = object : SyncTransport by inner {
            override suspend fun put(group: SyncGroup, collection: String, sealed: ByteArray, expectedVersion: Long, deviceId: String): PutResult =
                if (collection == DeviceSync.CHECKLISTS) throw SyncCollectionUnknownException(collection)
                else inner.put(group, collection, sealed, expectedVersion, deviceId)
        }
        val state = SyncStateStore(testDataStore(folder.root, "state-old-server"), PlainSealer)
        val adapters = listOf(MapAdapter(DeviceSync.SETTINGS, mapOf("theme" to "dark")), MapAdapter(DeviceSync.CHECKLISTS, mapOf("m/x" to "{}")))
        val engine = DeviceSyncEngine(transport, state, adapters, "A", clock = { 1_000 })
        val group = engine.create()
        assertTrue(engine.syncNow())
        assertEquals("dark", server.document(group, DeviceSync.SETTINGS).entries.getValue("theme").value)
        assertEquals(DeviceSyncEngine.SERVER_LACKS_COLLECTION, state.status(DeviceSync.CHECKLISTS).error)
    }
}
