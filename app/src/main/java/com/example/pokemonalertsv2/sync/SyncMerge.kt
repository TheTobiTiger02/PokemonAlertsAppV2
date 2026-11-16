package com.example.pokemonalertsv2.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The plaintext of one blob: every item of one collection with who changed it when. */
@Serializable
data class SyncDocument(
    val v: Int = SyncMerge.FORMAT_VERSION,
    val entries: Map<String, SyncEntry> = emptyMap()
)

/**
 * One item. [t] is when it last changed (ms), [d] the device that changed it, [x] marks a
 * deletion (a tombstone, then without [value]). [s] is the item's schema: an app whose
 * adapter knows a lower schema for the key leaves the item alone instead of applying and
 * re-encoding it, which would drop fields it does not know.
 */
@Serializable
data class SyncEntry(
    val t: Long,
    val d: String,
    val x: Boolean = false,
    @SerialName("val") val value: String? = null,
    val s: Int = 0
) {
    val isTombstone: Boolean get() = x || value == null
}

/**
 * What this device last agreed with the group, per item: a fingerprint of the value instead
 * of the value, plus its (t, d, x, s). [version] is the server version the entries match;
 * [pendingUpload] says they hold something the server has not got yet.
 *
 * [held] are items the group holds that this device does not keep locally: one its adapter
 * could not apply (unreadable, or written in a newer schema) or one this device keeps to
 * itself (a device-local or "only on this device" setting). Their absence from the local
 * snapshot is not a deletion.
 */
@Serializable
data class SyncShadow(
    val version: Long = 0,
    val pendingUpload: Boolean = false,
    val entries: Map<String, ShadowEntry> = emptyMap(),
    val held: Set<String> = emptySet(),
    /** The app version that could not apply [held]: an update tries them again. */
    val heldBy: Long = 0,
    /**
     * The group's copy of every held or device-local item, value included: the only copy this
     * device has, so an upload can carry it without fetching the group's document first.
     */
    val kept: Map<String, SyncEntry> = emptyMap()
)

@Serializable
data class ShadowEntry(val h: String = "", val t: Long, val d: String, val x: Boolean = false, val s: Int = 0)

/**
 * A local snapshot compared with the shadow: the document to merge, whether anything changed
 * here, and whether items the group holds were left out (held or local), so that the
 * document is not complete without the group's copy.
 */
data class LocalDiff(val document: SyncDocument, val changed: Boolean, val omitted: Boolean = false)

object SyncMerge {
    const val FORMAT_VERSION = 1
    const val TOMBSTONE_TTL_MILLIS = 90L * 24 * 60 * 60 * 1000

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /**
     * Stamps what changed since the shadow. A new or changed item gets `t = stamp()` and this
     * device; an item in the shadow but no longer in the snapshot becomes a tombstone the same
     * way. Unchanged items keep the shadow's (t, d), so another device's change is not
     * re-claimed by this one.
     */
    fun diffAgainstShadow(
        snapshot: Map<String, String>,
        shadow: SyncShadow,
        self: String,
        /** Schema this device writes for a key. */
        schemaOf: (String) -> Int = { 0 },
        /** Keys this device keeps to itself: never uploaded, their absence is no deletion. */
        isLocal: (String) -> Boolean = { false },
        stamp: () -> Long
    ): LocalDiff {
        var changed = false
        var omitted = false
        val entries = LinkedHashMap<String, SyncEntry>()
        snapshot.forEach { (key, value) ->
            // A held item's local value is older than the group's (which could not be applied
            // here): uploading it would overwrite the newer one.
            if (isLocal(key) || key in shadow.held) return@forEach
            val known = shadow.entries[key]
            entries[key] = if (known == null || known.x || known.h != SyncCrypto.fingerprint(value)) {
                changed = true
                SyncEntry(t = stamp(), d = self, value = value, s = schemaOf(key))
            } else {
                SyncEntry(t = known.t, d = known.d, value = value, s = known.s)
            }
        }
        shadow.entries.forEach { (key, known) ->
            if (key in entries) return@forEach
            when {
                known.x -> entries[key] = SyncEntry(t = known.t, d = known.d, x = true)
                // The group's copy is the only one: carry the kept copy, or leave it out and
                // let a merge with the group's document keep it.
                key in shadow.held || isLocal(key) -> shadow.kept[key]?.let { entries[key] = it } ?: run { omitted = true }
                else -> {
                    changed = true
                    entries[key] = SyncEntry(t = stamp(), d = self, x = true)
                }
            }
        }
        return LocalDiff(SyncDocument(entries = entries), changed, omitted)
    }

    /**
     * The join rule: a device joining an existing group records what it already holds at
     * `t = 0`, so the group's value wins wherever both have an item, while items only this
     * device has are still uploaded and kept.
     */
    fun joinBaseline(snapshot: Map<String, String>, self: String): SyncShadow =
        SyncShadow(
            // Pending, so the first sync merges and uploads even when nothing changes here after joining.
            pendingUpload = snapshot.isNotEmpty(),
            entries = snapshot.mapValues { (_, value) -> ShadowEntry(SyncCrypto.fingerprint(value), 0L, self) }
        )

    /** True when [a] is the newer change: higher `t`, on a tie the greater device id. */
    fun newer(a: SyncEntry, b: SyncEntry): Boolean =
        a.t > b.t || (a.t == b.t && a.d > b.d)

    /** Per item, the newer of the two; on a full tie the remote one. */
    fun merge(local: SyncDocument, remote: SyncDocument): SyncDocument {
        val keys = LinkedHashSet<String>().apply { addAll(local.entries.keys); addAll(remote.entries.keys) }
        val merged = keys.associateWith { key ->
            val mine = local.entries[key]
            val theirs = remote.entries[key]
            when {
                mine == null -> theirs!!
                theirs == null -> mine
                newer(mine, theirs) -> mine
                else -> theirs
            }
        }
        return SyncDocument(entries = merged)
    }

    /** Drops tombstones older than [ttlMillis]: by then every device has seen them. */
    fun pruneTombstones(document: SyncDocument, nowMillis: Long, ttlMillis: Long = TOMBSTONE_TTL_MILLIS): SyncDocument =
        document.copy(entries = document.entries.filterValues { !it.isTombstone || it.t >= nowMillis - ttlMillis })

    /** The shadow that records [document] as agreed at server [version]. */
    fun shadowOf(
        document: SyncDocument,
        version: Long,
        pendingUpload: Boolean,
        held: Set<String> = emptySet(),
        heldBy: Long = 0,
        isLocal: (String) -> Boolean = { false }
    ): SyncShadow =
        SyncShadow(
            heldBy = heldBy,
            kept = document.entries.filter { (key, entry) -> !entry.isTombstone && (key in held || isLocal(key)) },
            version = version,
            pendingUpload = pendingUpload,
            entries = document.entries.mapValues { (_, entry) ->
                ShadowEntry(
                    h = entry.value?.takeUnless { entry.isTombstone }?.let(SyncCrypto::fingerprint).orEmpty(),
                    t = entry.t,
                    d = entry.d,
                    x = entry.isTombstone,
                    s = entry.s
                )
            },
            held = held.filterTo(LinkedHashSet()) { document.entries[it]?.isTombstone == false }
        )

    /** What applying [merged] onto a device holding [snapshot] has to change there. */
    fun changesToApply(snapshot: Map<String, String>, merged: SyncDocument): Pair<Map<String, String>, Set<String>> {
        val upserts = LinkedHashMap<String, String>()
        val deletes = LinkedHashSet<String>()
        merged.entries.forEach { (key, entry) ->
            if (entry.isTombstone) {
                if (key in snapshot) deletes += key
            } else if (snapshot[key] != entry.value) {
                upserts[key] = entry.value!!
            }
        }
        return upserts to deletes
    }

    /** Same items with the same (t, d, x, value): nothing to upload. */
    fun sameContent(a: SyncDocument, b: SyncDocument): Boolean =
        a.entries.size == b.entries.size && a.entries.all { (key, entry) ->
            val other = b.entries[key] ?: return@all false
            entry.t == other.t && entry.d == other.d && entry.isTombstone == other.isTombstone &&
                entry.s == other.s && (entry.isTombstone || entry.value == other.value)
        }

    fun highestStamp(document: SyncDocument): Long = document.entries.values.maxOfOrNull { it.t } ?: 0L

    fun encode(document: SyncDocument): ByteArray =
        json.encodeToString(SyncDocument.serializer(), document).toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): SyncDocument =
        json.decodeFromString(SyncDocument.serializer(), bytes.toString(Charsets.UTF_8))
}
