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
 * deletion (a tombstone, then without [value]).
 */
@Serializable
data class SyncEntry(
    val t: Long,
    val d: String,
    val x: Boolean = false,
    @SerialName("val") val value: String? = null
) {
    val isTombstone: Boolean get() = x || value == null
}

/**
 * What this device last agreed with the group, per item: a fingerprint of the value instead
 * of the value, plus its (t, d, x). [version] is the server version the entries match;
 * [pendingUpload] says they hold something the server has not got yet.
 */
@Serializable
data class SyncShadow(
    val version: Long = 0,
    val pendingUpload: Boolean = false,
    val entries: Map<String, ShadowEntry> = emptyMap()
)

@Serializable
data class ShadowEntry(val h: String = "", val t: Long, val d: String, val x: Boolean = false)

/** A local snapshot compared with the shadow: the document to merge, and whether anything changed here. */
data class LocalDiff(val document: SyncDocument, val changed: Boolean)

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
        stamp: () -> Long
    ): LocalDiff {
        var changed = false
        val entries = LinkedHashMap<String, SyncEntry>()
        snapshot.forEach { (key, value) ->
            val known = shadow.entries[key]
            entries[key] = if (known == null || known.x || known.h != SyncCrypto.fingerprint(value)) {
                changed = true
                SyncEntry(t = stamp(), d = self, value = value)
            } else {
                SyncEntry(t = known.t, d = known.d, value = value)
            }
        }
        shadow.entries.forEach { (key, known) ->
            if (key in snapshot) return@forEach
            entries[key] = if (known.x) {
                SyncEntry(t = known.t, d = known.d, x = true)
            } else {
                changed = true
                SyncEntry(t = stamp(), d = self, x = true)
            }
        }
        return LocalDiff(SyncDocument(entries = entries), changed)
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

    /** Drops tombstones older than [TOMBSTONE_TTL_MILLIS]: by then every device has seen them. */
    fun pruneTombstones(document: SyncDocument, nowMillis: Long): SyncDocument =
        document.copy(entries = document.entries.filterValues { !it.isTombstone || it.t >= nowMillis - TOMBSTONE_TTL_MILLIS })

    /** The shadow that records [document] as agreed at server [version]. */
    fun shadowOf(document: SyncDocument, version: Long, pendingUpload: Boolean): SyncShadow =
        SyncShadow(
            version = version,
            pendingUpload = pendingUpload,
            entries = document.entries.mapValues { (_, entry) ->
                ShadowEntry(
                    h = entry.value?.takeUnless { entry.isTombstone }?.let(SyncCrypto::fingerprint).orEmpty(),
                    t = entry.t,
                    d = entry.d,
                    x = entry.isTombstone
                )
            }
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
                (entry.isTombstone || entry.value == other.value)
        }

    fun highestStamp(document: SyncDocument): Long = document.entries.values.maxOfOrNull { it.t } ?: 0L

    fun encode(document: SyncDocument): ByteArray =
        json.encodeToString(SyncDocument.serializer(), document).toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): SyncDocument =
        json.decodeFromString(SyncDocument.serializer(), bytes.toString(Charsets.UTF_8))
}
