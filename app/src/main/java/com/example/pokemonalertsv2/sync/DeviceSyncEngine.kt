package com.example.pokemonalertsv2.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/** Why a join did not happen. */
class SyncJoinException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { INVALID_CODE, NOT_FOUND }
}

/**
 * A failure retrying will not fix: a blob that does not authenticate with the group key (tampered,
 * or a different key) or does not decode. The worker gives up instead of retrying forever.
 */
class SyncPermanentException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The sync cycle. Each collection: diff local data against the shadow, fetch the group's
 * blob when its version moved, merge per item (newer wins), apply what the group won, and
 * upload when the merged document differs from the server's.
 *
 * One [Mutex] per collection keeps cycles of the same collection from overlapping while
 * different collections sync side by side (a running hunt is not held up by a settings
 * upload). Creating, joining and leaving take every collection's lock. The server's
 * compare-and-set keeps devices from overwriting each other.
 */
class DeviceSyncEngine(
    private val transport: SyncTransport,
    private val state: SyncStateStore,
    private val adapters: List<SyncAdapter>,
    private val deviceId: String,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Called with the group after connecting and with null after leaving (topic subscription). */
    private val onGroupChanged: suspend (old: SyncGroup?, new: SyncGroup?) -> Unit = { _, _ -> },
    /** A sync right after creating the group failed (offline): the app retries it in the background. */
    private val onDeferredSync: () -> Unit = {},
    /** This app's version: items an older version could not apply are tried again after an update. */
    private val appVersion: () -> Long = { 0L }
) {
    private val locks: Map<String, Mutex> = adapters.associate { it.name to Mutex() }
    private val active = AtomicInteger()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Set while leaving, so the devices collection uploads this device's row as deleted. */
    @Volatile
    internal var leaving = false
        private set

    private val adapterByName = adapters.associateBy { it.name }

    /** Every collection lock, always in the same order, for changes to the group itself. */
    private suspend fun <T> withAllLocks(action: suspend () -> T): T {
        val ordered = locks.entries.sortedBy { it.key }.map { it.value }
        val held = ArrayList<Mutex>(ordered.size)
        try {
            ordered.forEach { lock ->
                lock.lock()
                held += lock
            }
            return action()
        } finally {
            held.asReversed().forEach { it.unlock() }
        }
    }

    /**
     * Creates a group from this device's data: every item is stamped now, so it all uploads.
     * The group exists here once this returns; a first upload that fails (offline) is left
     * to [onDeferredSync] instead of reporting the set-up as failed.
     */
    suspend fun create(): SyncGroup {
        val group = withAllLocks {
            val group = SyncCrypto.newGroup()
            state.connect(group, shadows = emptyMap())
            onGroupChanged(null, group)
            group
        }
        try {
            syncNow()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            onDeferredSync()
        }
        return group
    }

    /**
     * Joins the group behind [code]. The join rule: everything already here is recorded at
     * `t = 0`, so the group's values win where both exist and items only this device has are
     * uploaded and kept.
     */
    suspend fun join(code: String): SyncGroup {
        val secret = SyncCrypto.decodePairingCode(code) ?: throw SyncJoinException(SyncJoinException.Reason.INVALID_CODE)
        val group = SyncGroup(secret)
        withAllLocks {
            try {
                transport.list(group)
            } catch (gone: SyncGroupGoneException) {
                throw SyncJoinException(SyncJoinException.Reason.NOT_FOUND)
            }
            val baselines = adapters.associate { adapter ->
                adapter.name to SyncMerge.joinBaseline(adapter.snapshot().filterKeys { !adapter.isLocal(it) }, deviceId)
            }
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
        withAllLocks {
            leaving = true
            try {
                runCatching { syncCollection(group, DeviceSync.DEVICES, remoteVersion = null, inline = null) }
            } finally {
                leaving = false
            }
            state.disconnect()
            onGroupChanged(group, null)
        }
    }

    /** Deletes the group on the server, then leaves. Other devices hear it at once (live event, ping). */
    suspend fun deleteEverywhere() {
        val group = state.group() ?: return
        withAllLocks {
            transport.delete(group)
            state.disconnect()
            onGroupChanged(group, null)
        }
    }

    /**
     * Another device deleted the group (live event or ping): stop here instead of re-creating it.
     * [onlyIfConfirmed] for a plain "not found": a group just created here is unknown to the
     * server until its first upload, which is not a deletion.
     */
    suspend fun onDeletedElsewhere(onlyIfConfirmed: Boolean = false) {
        val group = state.group() ?: return
        withAllLocks {
            if (state.group()?.groupId != group.groupId) return@withAllLocks
            if (onlyIfConfirmed && !state.confirmed()) return@withAllLocks
            state.disconnect(notice = DeviceSync.NOTICE_DELETED_ELSEWHERE)
            onGroupChanged(group, null)
        }
    }

    /**
     * Syncs [collections] (all by default). Returns false when not connected. Network and
     * server errors are recorded per collection and rethrown as the result of the call, so a
     * worker can retry.
     *
     * [knownVersions] are server versions the caller already has, from a live event, an FCM
     * ping, or this device's own shadow for a local change: then the listing request is
     * skipped, and a version this device wrote or already has costs no request at all.
     * [inline] are sealed blobs that came with a live event: used instead of fetching.
     */
    suspend fun syncNow(
        collections: Collection<String> = DeviceSync.COLLECTIONS,
        knownVersions: Map<String, Long>? = null,
        inline: Map<String, FetchResult.Blob> = emptyMap()
    ): Boolean {
        val group = state.group() ?: return false
        begin()
        try {
            val versions = knownVersions ?: try {
                transport.list(group).also { if (!state.confirmed()) state.setConfirmed() }
            } catch (gone: SyncGroupGoneException) {
                if (disconnectIfDeleted(group)) return false
                emptyMap() // created here and not uploaded yet: the first upload creates it
            }
            var failure: Exception? = null
            adapters.filter { it.name in collections }.forEach { adapter ->
                try {
                    locks.getValue(adapter.name).withLock {
                        if (state.group()?.groupId != group.groupId) return false
                        syncCollection(group, adapter.name, versions[adapter.name] ?: 0L, inline[adapter.name])
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (gone: SyncGroupGoneException) {
                    if (disconnectIfDeleted(group)) return false
                    throw gone
                } catch (unknown: SyncCollectionUnknownException) {
                    // The backend predates this collection: the rest syncs, this one waits for a server update.
                    state.writeStatus(adapter.name, state.status(adapter.name).copy(error = SERVER_LACKS_COLLECTION))
                } catch (error: Exception) {
                    failure = failure ?: error
                    state.writeStatus(adapter.name, state.status(adapter.name).copy(error = error.message ?: error.javaClass.simpleName))
                }
            }
            failure?.let { throw it }
        } finally {
            end()
        }
        return true
    }

    /**
     * A local change to [collection]: uploads against the version this device last agreed
     * with, without listing the group first. Costs nothing when the change diffs empty (an
     * applied change reading back, a restart); a write by another device in between answers
     * 412, and the cycle fetches and merges.
     */
    suspend fun syncLocalChange(collection: String): Boolean =
        syncNow(listOf(collection), knownVersions = mapOf(collection to state.shadow(collection).version))

    /**
     * Makes the group's value win for [key] the next time [collection] syncs, as on joining:
     * used when a setting stops being "only on this device".
     */
    suspend fun adoptGroupValue(collection: String, key: String) {
        val adapter = adapterByName[collection] ?: return
        if (state.group() == null) return
        locks.getValue(collection).withLock {
            val shadow = state.shadow(collection)
            val local = adapter.snapshot()[key]
            val entries = shadow.entries.toMutableMap()
            if (local == null) entries.remove(key) else entries[key] = ShadowEntry(SyncCrypto.fingerprint(local), 0L, deviceId)
            // Version 0: the next listed cycle fetches the group's document instead of trusting its own.
            state.writeShadow(collection, shadow.copy(version = 0, entries = entries, held = shadow.held - key))
        }
        syncNow(listOf(collection))
    }

    private fun begin() {
        if (active.incrementAndGet() > 0) _running.value = true
    }

    private fun end() {
        if (active.decrementAndGet() <= 0) _running.value = false
    }

    /**
     * The server does not know a group this device has already synced with: deleted from
     * another device (or purged after months idle). Stops here instead of re-creating it with
     * the next upload. False for a group created here and not uploaded yet.
     */
    private suspend fun disconnectIfDeleted(group: SyncGroup): Boolean {
        if (!state.confirmed()) return false
        if (state.group()?.groupId != group.groupId) return true
        state.disconnect(notice = DeviceSync.NOTICE_DELETED_ELSEWHERE)
        onGroupChanged(group, null)
        return true
    }

    /** `max(now, newest change seen + 1)`, so a device whose clock is behind still orders after what it has seen. */
    private suspend fun nextStamp(): Long = maxOf(clock(), state.highestStamp() + 1)

    private fun openDocument(group: SyncGroup, name: String, bytes: ByteArray): SyncDocument {
        val plain = try {
            SyncCrypto.open(bytes, group.encKey, SyncCrypto.aad(group.groupId, name))
        } catch (error: Exception) {
            throw SyncPermanentException("$name: blob does not authenticate", error)
        }
        return try {
            SyncMerge.decode(plain)
        } catch (error: Exception) {
            throw SyncPermanentException("$name: blob does not decode", error)
        }
    }

    /**
     * One collection. [remoteVersion] is the server's version from the listing (null: unknown,
     * always fetch). Up to [MAX_ROUNDS] rounds when another device writes in between.
     */
    private suspend fun syncCollection(group: SyncGroup, name: String, remoteVersion: Long?, inline: FetchResult.Blob?) {
        val adapter = adapterByName.getValue(name)
        val aad = SyncCrypto.aad(group.groupId, name)
        var knownRemote = remoteVersion
        var inlineBlob = inline
        repeat(MAX_ROUNDS) {
            val shadow = state.shadow(name)
            val snapshot = adapter.snapshot()
            // One stamp per pass: every item changed here since the last sync shares it.
            val now = nextStamp()
            val diff = SyncMerge.diffAgainstShadow(snapshot, shadow, deviceId, adapter::schemaOf, adapter::isLocal) { now }
            if (diff.changed) state.raiseHighestStamp(now)
            val local = diff.document
            val wantsUpload = diff.changed || shadow.pendingUpload
            // An updated app may read what an older one could not.
            val retryHeld = shadow.held.isNotEmpty() && shadow.heldBy != appVersion()

            // Nothing changed here or there: no request at all. (An item just kept to this
            // device costs one fetch, which records the group's copy of it.)
            if (!wantsUpload && !retryHeld && !diff.omitted && knownRemote != null && knownRemote == shadow.version) {
                markSynced(name)
                return
            }

            // Items left out locally (held or kept to this device) only exist in the group's
            // copy: the document has to be merged with it before it is uploaded or recorded,
            // so 304 is not good enough then.
            val mustFetch = diff.omitted || retryHeld
            val fetched = when {
                !mustFetch && knownRemote != null && knownRemote == shadow.version -> FetchResult.NotModified
                inlineBlob != null && inlineBlob.version == knownRemote && inlineBlob.version > shadow.version -> inlineBlob
                else -> transport.fetch(group, name, shadow.version.takeIf { it > 0 && !mustFetch })
            }
            val (remoteDoc, baseVersion) = when (fetched) {
                FetchResult.NotModified -> null to shadow.version
                FetchResult.Empty -> SyncDocument() to 0L
                is FetchResult.Blob -> {
                    val document = openDocument(group, name, fetched.bytes)
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
                clock(),
                adapter.tombstoneTtlMillis
            )
            val needsUpload = if (remoteDoc == null) wantsUpload else !SyncMerge.sameContent(merged, remoteDoc)
            val (allUpserts, deletes) = SyncMerge.changesToApply(snapshot, merged)
            // Items written in a newer schema than this app knows stay the group's.
            val tooNew = allUpserts.keys.filterTo(LinkedHashSet()) { key -> (merged.entries[key]?.s ?: 0) > adapter.schemaOf(key) }
            val upserts = allUpserts.filterKeys { it !in tooNew }

            withContext(NonCancellable) {
                // Shadow first: applied values must not read back as local changes.
                val version = appVersion()
                state.writeShadow(name, SyncMerge.shadowOf(merged, baseVersion, needsUpload, shadow.held + tooNew, version, adapter::isLocal))
                val held = if (upserts.isNotEmpty() || deletes.isNotEmpty()) adapter.apply(upserts, deletes) else emptySet()
                // Held: what still is not here. Earlier held items that applied now drop out.
                val stillHeld = tooNew + held + shadow.held.filter { it !in upserts.keys || it in held }
                state.writeShadow(name, SyncMerge.shadowOf(merged, baseVersion, needsUpload, stillHeld, version, adapter::isLocal))
            }

            if (!needsUpload) {
                markSynced(name)
                return
            }
            val sealed = SyncCrypto.seal(SyncMerge.encode(merged), group.encKey, aad)
            when (val result = transport.put(group, name, sealed, baseVersion, deviceId)) {
                is PutResult.Stored -> {
                    val current = state.shadow(name)
                    state.writeShadow(name, current.copy(version = result.version, pendingUpload = false))
                    if (!state.confirmed()) state.setConfirmed()
                    markSynced(name)
                    return
                }
                is PutResult.Conflict -> {
                    // Someone wrote first: fetch and merge again.
                    knownRemote = null
                    inlineBlob = null
                }
            }
        }
    }

    private suspend fun markSynced(name: String) {
        state.writeStatus(name, CollectionStatus(lastSyncAtMillis = clock(), error = null, unsupported = false))
    }

    companion object {
        const val MAX_ROUNDS = 3

        /** A collection's error while the server does not carry it yet (the sync page says so). */
        const val SERVER_LACKS_COLLECTION = "server_lacks_collection"
    }
}
