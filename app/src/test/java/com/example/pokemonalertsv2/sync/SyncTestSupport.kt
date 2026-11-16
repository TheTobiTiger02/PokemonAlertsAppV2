package com.example.pokemonalertsv2.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

internal val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

internal fun testDataStore(folder: File, name: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(scope = testScope) { File(folder, "$name.preferences_pb") }

internal object PlainSealer : SecretSealer {
    override fun seal(plain: String) = plain
    override fun open(sealed: String) = sealed
}

/** An adapter over an in-memory map: the "local data" of one fake device. */
internal class MapAdapter(override val name: String, initial: Map<String, String> = emptyMap()) : SyncAdapter {
    val data = LinkedHashMap(initial)
    var applyCount = 0
    /** Values this device cannot read: apply reports them held. */
    val unreadable = HashSet<String>()
    /** Keys kept to this device ("only on this device"). */
    val local = HashSet<String>()
    /** Schema this device writes per key. */
    val schemas = HashMap<String, Int>()
    override suspend fun snapshot(): Map<String, String> = LinkedHashMap(data)
    override suspend fun apply(upserts: Map<String, String>, deletes: Set<String>): Set<String> {
        applyCount++
        deletes.filterNot { it in local }.forEach { data.remove(it) }
        val held = upserts.keys.filterTo(HashSet()) { it in unreadable || it in local }
        data.putAll(upserts.filterKeys { it !in held })
        return held
    }
    override fun isLocal(key: String): Boolean = key in local
    override fun schemaOf(key: String): Int = schemas[key] ?: 0
    override val changes: Flow<Unit> = emptyFlow()
}

/** The backend in memory: compare-and-set per collection, as device_sync_store.js does. */
internal class FakeServer {
    val blobs = HashMap<String, Pair<Long, ByteArray>>()
    var groupId: String? = null
    var deleted = false
    val puts = AtomicInteger()
    val lists = AtomicInteger()
    val fetches = AtomicInteger()

    fun transport(beforePut: suspend () -> Unit = {}): SyncTransport = object : SyncTransport {
        override suspend fun list(group: SyncGroup): Map<String, Long> {
            lists.incrementAndGet()
            if (deleted || groupId != group.groupId) throw SyncGroupGoneException()
            return blobs.mapValues { it.value.first }
        }

        override suspend fun fetch(group: SyncGroup, collection: String, knownVersion: Long?): FetchResult {
            fetches.incrementAndGet()
            if (deleted || groupId != group.groupId) throw SyncGroupGoneException()
            val blob = blobs[collection] ?: return FetchResult.Empty
            return if (knownVersion == blob.first) FetchResult.NotModified else FetchResult.Blob(blob.first, blob.second)
        }

        override suspend fun put(group: SyncGroup, collection: String, sealed: ByteArray, expectedVersion: Long, deviceId: String): PutResult {
            beforePut()
            // As the server: only a first write may create a group.
            if (deleted || (groupId == null && expectedVersion > 0)) throw SyncGroupGoneException()
            if (groupId == null) groupId = group.groupId
            check(groupId == group.groupId)
            val current = blobs[collection]?.first ?: 0L
            if (current != expectedVersion) return PutResult.Conflict(current)
            puts.incrementAndGet()
            blobs[collection] = (current + 1) to sealed
            return PutResult.Stored(current + 1)
        }

        override suspend fun delete(group: SyncGroup) {
            deleted = true
            blobs.clear()
        }
    }

    fun document(group: SyncGroup, collection: String): SyncDocument =
        SyncMerge.decode(SyncCrypto.open(blobs.getValue(collection).second, group.encKey, SyncCrypto.aad(group.groupId, collection)))
}

/** One fake device: its own state store, adapters and clock. */
internal class FakeDevice(
    folder: File,
    val id: String,
    server: FakeServer,
    var now: Long,
    beforePut: suspend () -> Unit = {},
    collections: List<String> = listOf(DeviceSync.SETTINGS)
) {
    val adapters = collections.map { MapAdapter(it) }
    val adapter get() = adapters.first()
    val state = SyncStateStore(testDataStore(folder, "state-$id"), PlainSealer)
    var appVersion = 1L
    val engine = DeviceSyncEngine(server.transport(beforePut), state, adapters, id, clock = { now }, appVersion = { appVersion })
}
