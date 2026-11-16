package com.example.pokemonalertsv2.sync.live

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.pokemonalertsv2.sync.DeviceSync
import com.example.pokemonalertsv2.sync.SyncAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map

/**
 * The `live` collection's items on this device, as raw strings: the ones this device writes as
 * the owner of a session, and the ones the other devices wrote, exactly as they came. Kept apart
 * from the native stores (the hunt store, the catch-route database), so a session mirrored from
 * another device can never be taken for one running here: nothing that starts location
 * tracking, an overlay or a notification ever reads this store.
 *
 * Persisted, so a mirror shows the last known state after a restart until the stream catches up.
 */
class LiveStore(private val prefs: SharedPreferences) {
    private val _items = MutableStateFlow(read())
    val items: StateFlow<Map<String, String>> = _items.asStateFlow()

    private fun read(): Map<String, String> =
        prefs.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()

    @Synchronized
    fun put(key: String, value: String) {
        if (_items.value[key] == value) return
        prefs.edit { putString(key, value) }
        _items.value = _items.value + (key to value)
    }

    @Synchronized
    fun remove(key: String) {
        if (key !in _items.value) return
        prefs.edit { remove(key) }
        _items.value = _items.value - key
    }

    @Synchronized
    fun applyRemote(upserts: Map<String, String>, deletes: Set<String>) {
        if (upserts.isEmpty() && deletes.isEmpty()) return
        prefs.edit {
            deletes.forEach { remove(it) }
            upserts.forEach { (key, value) -> putString(key, value) }
        }
        _items.value = _items.value - deletes + upserts
    }

    /** Forgets everything (leaving the group): another group's sessions are not this one's. */
    @Synchronized
    fun clear() {
        prefs.edit { clear() }
        _items.value = emptyMap()
    }

    companion object {
        @Volatile
        private var instance: LiveStore? = null

        fun get(context: Context): LiveStore = instance ?: synchronized(this) {
            instance ?: LiveStore(context.applicationContext.getSharedPreferences("live_sync", Context.MODE_PRIVATE)).also { instance = it }
        }
    }
}

/**
 * The `live` collection. Items round-trip verbatim through [LiveStore], so another device's item
 * reads back byte for byte and is never re-claimed by this one. Sent at once (no debounce), and a
 * deletion is kept only an hour: a finished session or a done command needs no long memory.
 */
class LiveSyncAdapter(private val store: LiveStore) : SyncAdapter {
    override val name = DeviceSync.LIVE
    override val debounceMillis: Long = 0L
    override val tombstoneTtlMillis: Long = 60L * 60 * 1000

    override suspend fun snapshot(): Map<String, String> = store.items.value

    override suspend fun apply(upserts: Map<String, String>, deletes: Set<String>): Set<String> {
        store.applyRemote(upserts, deletes)
        return emptySet()
    }

    override val changes: Flow<Unit> = store.items.drop(1).distinctUntilChanged().map { }
}
