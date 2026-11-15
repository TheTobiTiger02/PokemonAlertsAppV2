package com.example.pokemonalertsv2.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Why a join did not happen. */
class SyncJoinException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { INVALID_CODE, NOT_FOUND }
}

/**
 * The sync cycle. Each collection: diff local data against the shadow, fetch the group's
 * blob when its version moved, merge per item (newer wins), apply what the group won, and
 * upload when the merged document differs from the server's. One [Mutex] keeps cycles from
 * overlapping; the server's compare-and-set keeps devices from overwriting each other.
 */
class DeviceSyncEngine(
    private val transport: SyncTransport,
    private val state: SyncStateStore,
    private val adapters: List<SyncAdapter>,
    private val deviceId: String,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Called with the group after connecting and with null after leaving (topic subscription). */
    private val onGroupChanged: suspend (old: SyncGroup?, new: SyncGroup?) -> Unit = { _, _ -> }
) {
    private val mutex = Mutex()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Set while leaving, so the devices collection uploads this device's row as deleted. */
    @Volatile
    internal var leaving = false
        private set

    private val adapterByName = adapters.associateBy { it.name }

    /** Creates a group from this device's data: every item is stamped now, so it all uploads. */
    suspend fun create(): SyncGroup = mutex.withLock {
        val group = SyncCrypto.newGroup()
        state.connect(group, shadows = emptyMap())
        onGroupChanged(null, group)
        group
    }.also { syncNow() }

    /**
     * Joins the group behind [code]. The join rule: everything already here is recorded at
     * `t = 0`, so the group's values win where both exist and items only this device has are
     * uploaded and kept.
     */
    suspend fun join(code: String): SyncGroup {
        val secret = SyncCrypto.decodePairingCode(code) ?: throw SyncJoinException(SyncJoinException.Reason.INVALID_CODE)
        val group = SyncGroup(secret)
        mutex.withLock {
            try {
                transport.list(group)
            } catch (gone: SyncGroupGoneException) {
                throw SyncJoinException(SyncJoinException.Reason.NOT_FOUND)
            }
            val baselines = adapters.associate { it.name to SyncMerge.joinBaseline(it.snapshot(), deviceId) }
            state.connect(group, baselines)
            state.setConfirmed()
            onGroupChanged(null, group)
        }
        syncNow()
        return group
    }

    /** Leaves on this device only: local data stays, this device's row is removed from the group. */
    suspend fun leave() {
        val group = state.group() ?: return
        mutex.withLock {
            leaving = true
            try {
                runCatching { syncCollection(group, DeviceSync.DEVICES, remoteVersion = null) }
            } finally {
                leaving = false
            }
            state.disconnect()
            onGroupChanged(group, null)
        }
    }

    /** Deletes the group on the server, then leaves. Other devices notice on their next sync. */
    suspend fun deleteEverywhere() {
        val group = state.group() ?: return
        mutex.withLock {
            transport.delete(group)
            state.disconnect()
            onGroupChanged(group, null)
        }
    }

    /**
     * Syncs [collections] (all by default). Returns false when not connected. Network and
     * server errors are recorded per collection and rethrown as the result of the call, so a
     * worker can retry.
     */
    suspend fun syncNow(collections: Collection<String> = DeviceSync.COLLECTIONS): Boolean {
        val group = state.group() ?: return false
        mutex.withLock {
            if (state.group()?.groupId != group.groupId) return false
            _running.value = true
            try {
                val versions = try {
                    transport.list(group).also { if (!state.confirmed()) state.setConfirmed() }
                } catch (gone: SyncGroupGoneException) {
                    if (state.confirmed()) {
                        // Deleted from another device (or purged after months idle): stop here
                        // instead of re-creating it with the next upload.
                        state.disconnect(notice = DeviceSync.NOTICE_DELETED_ELSEWHERE)
                        onGroupChanged(group, null)
                        return false
                    }
                    emptyMap() // created here and not uploaded yet: the first upload creates it
                }
                var failure: Exception? = null
                adapters.filter { it.name in collections }.forEach { adapter ->
                    try {
                        syncCollection(group, adapter.name, versions[adapter.name] ?: 0L)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        failure = failure ?: error
                        state.writeStatus(adapter.name, state.status(adapter.name).copy(error = error.message ?: error.javaClass.simpleName))
                    }
                }
                failure?.let { throw it }
            } finally {
                _running.value = false
            }
        }
        return true
    }

    /** `max(now, newest change seen + 1)`, so a device whose clock is behind still orders after what it has seen. */
    private suspend fun nextStamp(): Long = maxOf(clock(), state.highestStamp() + 1)

    /**
     * One collection. [remoteVersion] is the server's version from the listing (null: unknown,
     * always fetch). Up to [MAX_ROUNDS] rounds when another device writes in between.
     */
    private suspend fun syncCollection(group: SyncGroup, name: String, remoteVersion: Long?) {
        val adapter = adapterByName.getValue(name)
        val aad = SyncCrypto.aad(group.groupId, name)
        var knownRemote = remoteVersion
        repeat(MAX_ROUNDS) {
            val shadow = state.shadow(name)
            val snapshot = adapter.snapshot()
            // One stamp per pass: every item changed here since the last sync shares it.
            val now = nextStamp()
            val diff = SyncMerge.diffAgainstShadow(snapshot, shadow, deviceId) { now }
            if (diff.changed) state.raiseHighestStamp(now)
            val local = diff.document

            // Nothing changed here or there: no request at all.
            if (!diff.changed && !shadow.pendingUpload && knownRemote != null && knownRemote == shadow.version) {
                markSynced(name)
                return
            }

            val fetched = if (knownRemote != null && knownRemote == shadow.version) {
                FetchResult.NotModified
            } else {
                transport.fetch(group, name, shadow.version.takeIf { it > 0 })
            }
            val (remoteDoc, baseVersion) = when (fetched) {
                FetchResult.NotModified -> null to shadow.version
                FetchResult.Empty -> SyncDocument() to 0L
                is FetchResult.Blob -> {
                    val document = SyncMerge.decode(SyncCrypto.open(fetched.bytes, group.encKey, aad))
                    if (document.v > SyncMerge.FORMAT_VERSION) {
                        // Written by a newer app: never overwrite what this one cannot read.
                        state.writeStatus(name, state.status(name).copy(unsupported = true, error = null))
                        return
                    }
                    state.raiseHighestStamp(SyncMerge.highestStamp(document))
                    document to fetched.version
                }
            }

            val merged = SyncMerge.pruneTombstones(
                if (remoteDoc == null) local else SyncMerge.merge(local, remoteDoc),
                clock()
            )
            val needsUpload = if (remoteDoc == null) {
                diff.changed || shadow.pendingUpload
            } else {
                !SyncMerge.sameContent(merged, remoteDoc)
            }
            val (upserts, deletes) = SyncMerge.changesToApply(snapshot, merged)

            withContext(NonCancellable) {
                // Shadow first: applied values must not read back as local changes.
                state.writeShadow(name, SyncMerge.shadowOf(merged, baseVersion, pendingUpload = needsUpload))
                if (upserts.isNotEmpty() || deletes.isNotEmpty()) adapter.apply(upserts, deletes)
            }

            if (!needsUpload) {
                markSynced(name)
                return
            }
            val sealed = SyncCrypto.seal(SyncMerge.encode(merged), group.encKey, aad)
            when (val result = transport.put(group, name, sealed, baseVersion, deviceId)) {
                is PutResult.Stored -> {
                    state.writeShadow(name, SyncMerge.shadowOf(merged, result.version, pendingUpload = false))
                    if (!state.confirmed()) state.setConfirmed()
                    markSynced(name)
                    return
                }
                is PutResult.Conflict -> knownRemote = null // someone wrote first: fetch and merge again
            }
        }
    }

    private suspend fun markSynced(name: String) {
        state.writeStatus(name, CollectionStatus(lastSyncAtMillis = clock(), error = null, unsupported = false))
    }

    companion object {
        const val MAX_ROUNDS = 3
    }
}
