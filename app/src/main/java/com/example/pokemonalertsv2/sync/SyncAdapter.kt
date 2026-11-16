package com.example.pokemonalertsv2.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.example.pokemonalertsv2.catchroutes.CATCH_ROUTE_HISTORY_CAP
import com.example.pokemonalertsv2.catchroutes.CatchAreaEntity
import com.example.pokemonalertsv2.catchroutes.CatchHistoryEntity
import com.example.pokemonalertsv2.catchroutes.CatchRouteDao
import com.example.pokemonalertsv2.catchroutes.CatchSetupEntity
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.backup.SettingsBackup
import com.example.pokemonalertsv2.data.counters.RaidCounterPreferences
import com.example.pokemonalertsv2.data.database.PokeGenieDao
import com.example.pokemonalertsv2.data.database.PokeGenieMonEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * One collection's view of local data for device sync: every item as a string, keyed by a
 * stable item key. The engine diffs [snapshot] against what it last agreed with the group and
 * hands back what the group changed through [apply]. [changes] emits when the local data may
 * have changed, so the app can schedule a sync.
 */
interface SyncAdapter {
    val name: String
    suspend fun snapshot(): Map<String, String>

    /**
     * Writes what the group changed. Returns the keys of [upserts] it could not apply (an
     * unreadable value, or one this device keeps to itself): the engine then keeps the group's
     * copy instead of reading their absence here as a deletion, and offers them again on the
     * next sync.
     */
    suspend fun apply(upserts: Map<String, String>, deletes: Set<String>): Set<String>
    val changes: Flow<Unit>

    /**
     * The schema this app writes for [key]. Raise it when an item gains fields: an older app
     * then leaves the newer item alone instead of applying it and re-encoding it without them.
     */
    fun schemaOf(key: String): Int = 0

    /** Keys this device keeps to itself: never uploaded, never overwritten by the group's value. */
    fun isLocal(key: String): Boolean = false

    /** How long a deletion is kept in the group's document. */
    val tombstoneTtlMillis: Long get() = SyncMerge.TOMBSTONE_TTL_MILLIS

    /** How long local changes are gathered before uploading; 0 sends at once, the newest state after each upload. */
    val debounceMillis: Long get() = 1_000L
}

/** An adapter made of functions, for collections whose storage already exposes them. */
class FunctionSyncAdapter(
    override val name: String,
    private val read: suspend () -> Map<String, String>,
    private val write: suspend (Map<String, String>, Set<String>) -> Set<String>,
    override val changes: Flow<Unit>,
    override val debounceMillis: Long = 1_000L,
    override val tombstoneTtlMillis: Long = SyncMerge.TOMBSTONE_TTL_MILLIS,
    private val schemas: (String) -> Int = { 0 }
) : SyncAdapter {
    override suspend fun snapshot(): Map<String, String> = read()
    override suspend fun apply(upserts: Map<String, String>, deletes: Set<String>) = write(upserts, deletes)
    override fun schemaOf(key: String): Int = schemas(key)
}

/**
 * A preference store whose keys the settings collection carries. Item keys are
 * `prefix + preference name`; [syncs] decides which preference names travel.
 */
class SettingsSource(
    val prefix: String,
    val dataStore: DataStore<Preferences>,
    val syncs: (String) -> Boolean
)

/**
 * Every key of the settings store except the device-local ones, plus the journey preferences
 * of the arrival store. Values use the settings backup's typed encoding, so an Int stays an
 * Int on the other device.
 *
 * [localKeys] names settings this device keeps to itself ("only on this device"): they are
 * neither uploaded nor overwritten, and their absence here is not a deletion.
 */
class SettingsSyncAdapter(
    private val sources: List<SettingsSource>,
    /** Read at the start of every cycle, so a sync never runs on a stale list. */
    private val localKeys: suspend () -> Set<String> = { emptySet() },
    private val onApplied: suspend () -> Unit = {}
) : SyncAdapter {
    /** The settings store alone, as before journey preferences joined it. */
    constructor(dataStore: DataStore<Preferences>, onApplied: suspend () -> Unit = {}) :
        this(listOf(SettingsSource("", dataStore, DeviceSync::isSyncedSetting)), onApplied = onApplied)

    override val name = DeviceSync.SETTINGS
    override val debounceMillis: Long = 300L
    private val json = SyncMerge.json

    @Volatile
    private var local: Set<String> = emptySet()
    private fun isLocalSetting(key: String) = key in local

    /**
     * Kept to this device: an "only on this device" setting, or one that no longer travels
     * (a device-local key an older version synced). Either way the group's copy stays as it
     * is: deleting it would reset the setting on devices still running that older version.
     */
    override fun isLocal(key: String): Boolean = isLocalSetting(key) || sourceOf(key) == null

    override suspend fun snapshot(): Map<String, String> {
        local = localKeys()
        return sources.fold(LinkedHashMap()) { items, source -> items.apply { putAll(encode(source, source.dataStore.data.first())) } }
    }

    private fun encode(source: SettingsSource, preferences: Preferences): Map<String, String> =
        preferences.asMap()
            .filterKeys { source.syncs(it.name) }
            .mapNotNull { (key, value) -> SettingsBackup.encodeEntry(key.name, value) }
            .associate { (name, entry) -> source.prefix + name to json.encodeToString(SettingsBackup.Entry.serializer(), entry) }

    private fun sourceOf(key: String): Pair<SettingsSource, String>? =
        // Longest prefix first: "" matches everything.
        sources.sortedByDescending { it.prefix.length }
            .firstOrNull { key.startsWith(it.prefix) }
            ?.let { it to key.removePrefix(it.prefix) }
            ?.takeIf { (source, name) -> source.syncs(name) }

    override suspend fun apply(upserts: Map<String, String>, deletes: Set<String>): Set<String> {
        local = localKeys()
        val held = LinkedHashSet<String>()
        val bySource = LinkedHashMap<SettingsSource, Pair<MutableMap<String, String>, MutableSet<String>>>()
        upserts.forEach { (key, value) ->
            val target = sourceOf(key)
            if (target == null || isLocalSetting(key)) held += key
            else bySource.getOrPut(target.first) { LinkedHashMap<String, String>() to LinkedHashSet() }.first[target.second] = value
        }
        deletes.forEach { key ->
            val target = sourceOf(key) ?: return@forEach
            if (isLocalSetting(key)) return@forEach
            bySource.getOrPut(target.first) { LinkedHashMap<String, String>() to LinkedHashSet() }.second += target.second
        }
        bySource.forEach { (source, change) ->
            val (sourceUpserts, sourceDeletes) = change
            source.dataStore.edit { preferences ->
                // Keys are typed; removing by name also clears one that changed type.
                val existing = preferences.asMap().keys.associateBy { it.name }
                sourceUpserts.forEach { (name, value) ->
                    val entry = runCatching { json.decodeFromString(SettingsBackup.Entry.serializer(), value) }.getOrNull()
                    if (entry == null) {
                        held += source.prefix + name
                        return@forEach
                    }
                    // Unreadable here (an unknown type): keep the local value, hold the group's copy.
                    if (!SettingsBackup.decodeInto(mutablePreferencesOf(), name, entry)) {
                        held += source.prefix + name
                        return@forEach
                    }
                    existing[name]?.let { preferences.remove(it) }
                    SettingsBackup.decodeInto(preferences, name, entry)
                }
                sourceDeletes.forEach { name -> existing[name]?.let { preferences.remove(it) } }
            }
        }
        if (bySource.keys.any { it.prefix.isEmpty() }) {
            // As a backup restore does: a document written by an older build is migrated once.
            AlertPreferences(sources.first { it.prefix.isEmpty() }.dataStore).updateFilterStateDocument { it }
        }
        onApplied()
        return held
    }

    override val changes: Flow<Unit> = merge(
        *sources.map { source -> source.dataStore.data.map { encode(source, it) }.distinctUntilChanged().map { } }.toTypedArray()
    )
}

/** Saved catch-route setups, drawn areas and finished routes. The running route stays on its device. */
class RoutesSyncAdapter(private val dao: CatchRouteDao) : SyncAdapter {
    override val name = DeviceSync.ROUTES
    private val json = SyncMerge.json

    override suspend fun snapshot(): Map<String, String> {
        val items = LinkedHashMap<String, String>()
        dao.allSetups().forEach { items[SETUP + it.id] = json.encodeToString(CatchSetupEntity.serializer(), it) }
        dao.allAreas().forEach { items[AREA + it.id] = json.encodeToString(CatchAreaEntity.serializer(), it) }
        dao.allHistory().forEach { items[HISTORY + it.id] = json.encodeToString(CatchHistoryEntity.serializer(), it) }
        return items
    }

    override suspend fun apply(upserts: Map<String, String>, deletes: Set<String>): Set<String> {
        deletes.forEach { key ->
            when {
                key.startsWith(SETUP) -> dao.delete(key.removePrefix(SETUP))
                key.startsWith(AREA) -> dao.deleteArea(key.removePrefix(AREA))
                key.startsWith(HISTORY) -> dao.deleteHistory(key.removePrefix(HISTORY))
            }
        }
        val held = LinkedHashSet<String>()
        upserts.forEach { (key, value) ->
            val applied = runCatching {
                when {
                    key.startsWith(SETUP) -> dao.save(json.decodeFromString(CatchSetupEntity.serializer(), value))
                    key.startsWith(AREA) -> dao.saveArea(json.decodeFromString(CatchAreaEntity.serializer(), value))
                    key.startsWith(HISTORY) -> dao.addHistory(json.decodeFromString(CatchHistoryEntity.serializer(), value))
                    else -> error("unknown key")
                }
            }.isSuccess
            if (!applied) held += key
        }
        // The cap drops the oldest rows the same way on every device; the deletion that
        // follows only repeats what each device's own trim does.
        if (upserts.keys.any { it.startsWith(HISTORY) }) dao.trimHistory(CATCH_ROUTE_HISTORY_CAP)
        return held
    }

    override val changes: Flow<Unit> = merge(dao.setups(), dao.areas(), dao.history()).map { }

    private companion object {
        const val SETUP = "setup/"
        const val AREA = "area/"
        const val HISTORY = "history/"
    }
}

/**
 * The Poke Genie roster as one item, and the import summary shown beside it as another.
 * Both are replaced whole, as an import does.
 */
class PokeGenieSyncAdapter(
    private val dao: PokeGenieDao,
    private val dataStore: DataStore<Preferences>
) : SyncAdapter {
    override val name = DeviceSync.POKEGENIE
    private val json = SyncMerge.json
    private val roster = ListSerializer(PokeGenieMonEntity.serializer())

    @Serializable
    data class Summary(
        val count: Int? = null,
        val matched: Int? = null,
        val fileName: String? = null,
        val importedAt: Long? = null,
        val expansionVersion: Int? = null
    )

    private fun summaryOf(preferences: Preferences): Summary? = Summary(
        count = preferences[RaidCounterPreferences.PG_COUNT_KEY],
        matched = preferences[RaidCounterPreferences.PG_MATCHED_KEY],
        fileName = preferences[RaidCounterPreferences.PG_FILE_KEY],
        importedAt = preferences[RaidCounterPreferences.PG_IMPORTED_AT_KEY],
        expansionVersion = preferences[RaidCounterPreferences.PG_EXPANSION_KEY]
    ).takeUnless { it == Summary() }

    override suspend fun snapshot(): Map<String, String> {
        val items = LinkedHashMap<String, String>()
        val mons = dao.getAll().sortedBy { it.id }
        if (mons.isNotEmpty()) items[ROSTER] = json.encodeToString(roster, mons)
        summaryOf(dataStore.data.first())?.let { items[SUMMARY] = json.encodeToString(Summary.serializer(), it) }
        return items
    }

    override suspend fun apply(upserts: Map<String, String>, deletes: Set<String>): Set<String> {
        val held = LinkedHashSet<String>()
        if (ROSTER in deletes) dao.clear()
        upserts[ROSTER]?.let { value ->
            val mons = runCatching { json.decodeFromString(roster, value) }.getOrNull()
            if (mons == null) held += ROSTER else dao.replaceAll(mons)
        }
        val summaryUpsert = upserts[SUMMARY]?.let { value ->
            runCatching { json.decodeFromString(Summary.serializer(), value) }.getOrNull().also { if (it == null) held += SUMMARY }
        }
        if (SUMMARY in deletes || summaryUpsert != null) {
            val summary = summaryUpsert ?: Summary()
            dataStore.edit { preferences ->
                preferences.putOrRemove(RaidCounterPreferences.PG_COUNT_KEY, summary.count)
                preferences.putOrRemove(RaidCounterPreferences.PG_MATCHED_KEY, summary.matched)
                preferences.putOrRemove(RaidCounterPreferences.PG_FILE_KEY, summary.fileName)
                preferences.putOrRemove(RaidCounterPreferences.PG_IMPORTED_AT_KEY, summary.importedAt)
                preferences.putOrRemove(RaidCounterPreferences.PG_EXPANSION_KEY, summary.expansionVersion)
            }
        }
        return held
    }

    override val changes: Flow<Unit> = merge(
        dao.countFlow().distinctUntilChanged().map { },
        dataStore.data.map(::summaryOf).distinctUntilChanged().map { }
    )

    private companion object {
        const val ROSTER = "roster"
        const val SUMMARY = "summary"
    }
}

/** A GoDex session as it travels: cookies decrypted, re-encrypted by the receiving device. */
@Serializable
data class GoDexSignIn(val cookies: String, val writeBackUrl: String, val state: String)

/** A Pokebattler sign-in as it travels; [trainer] is the legacy trainer number, if any. */
@Serializable
data class PokebattlerSignIn(val token: String, val userId: String, val displayName: String? = null, val trainer: String? = null)

/**
 * The GoDex session and the Pokebattler token, one item each. Stored ciphertext never travels:
 * the reader decrypts with this device's Keystore key, the writer encrypts with the receiving
 * device's. Signing out removes the item, and the tombstone signs the other devices out.
 */
class SignInsSyncAdapter(
    private val readGoDex: suspend () -> GoDexSignIn?,
    private val writeGoDex: suspend (GoDexSignIn?) -> Unit,
    private val readPokebattler: suspend () -> PokebattlerSignIn?,
    private val writePokebattler: suspend (PokebattlerSignIn?) -> Unit,
    override val changes: Flow<Unit>
) : SyncAdapter {
    override val name = DeviceSync.SIGNINS
    private val json = SyncMerge.json

    override suspend fun snapshot(): Map<String, String> = buildMap {
        readGoDex()?.let { put(GODEX, json.encodeToString(GoDexSignIn.serializer(), it)) }
        readPokebattler()?.let { put(POKEBATTLER, json.encodeToString(PokebattlerSignIn.serializer(), it)) }
    }

    override suspend fun apply(upserts: Map<String, String>, deletes: Set<String>): Set<String> {
        val held = LinkedHashSet<String>()
        if (GODEX in deletes) writeGoDex(null)
        upserts[GODEX]?.let { value ->
            val signIn = runCatching { json.decodeFromString(GoDexSignIn.serializer(), value) }.getOrNull()
            if (signIn == null) held += GODEX else writeGoDex(signIn)
        }
        if (POKEBATTLER in deletes) writePokebattler(null)
        upserts[POKEBATTLER]?.let { value ->
            val signIn = runCatching { json.decodeFromString(PokebattlerSignIn.serializer(), value) }.getOrNull()
            if (signIn == null) held += POKEBATTLER else writePokebattler(signIn)
        }
        return held
    }

    private companion object {
        const val GODEX = "godex"
        const val POKEBATTLER = "pokebattler"
    }
}

private fun <T> androidx.datastore.preferences.core.MutablePreferences.putOrRemove(key: Preferences.Key<T>, value: T?) {
    if (value == null) remove(key) else set(key, value)
}

/**
 * This device's own row plus the rows last merged from the others. Only the own row ever
 * changes here; the others are kept so they read back unchanged and are never re-claimed.
 * [ownRow] is null while leaving the group, which turns this device's row into a tombstone.
 */
class DevicesSyncAdapter(
    private val selfId: String,
    private val state: SyncStateStore,
    private val ownRow: suspend () -> DeviceRow?
) : SyncAdapter {
    override val name = DeviceSync.DEVICES
    private val json = SyncMerge.json

    override suspend fun snapshot(): Map<String, String> {
        val items = state.devices().filterKeys { it != selfId }
            .mapValues { (_, row) -> json.encodeToString(DeviceRow.serializer(), row) }
            .toMutableMap()
        ownRow()?.let { items[selfId] = json.encodeToString(DeviceRow.serializer(), it) }
        return items
    }

    override suspend fun apply(upserts: Map<String, String>, deletes: Set<String>): Set<String> {
        val rows = state.devices().toMutableMap()
        val held = LinkedHashSet<String>()
        deletes.forEach { rows.remove(it) }
        upserts.filterKeys { it != selfId }.forEach { (id, value) ->
            val row = runCatching { json.decodeFromString(DeviceRow.serializer(), value) }.getOrNull()
            if (row == null) held += id else rows[id] = row
        }
        state.writeDevices(rows)
        return held
    }

    /** The own row only changes with an app update or a rename: the app syncs it once at start. */
    override val changes: Flow<Unit> = kotlinx.coroutines.flow.emptyFlow()
}
