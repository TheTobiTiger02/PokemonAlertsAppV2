package com.example.pokemonalertsv2.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
    suspend fun apply(upserts: Map<String, String>, deletes: Set<String>)
    val changes: Flow<Unit>
}

/** An adapter made of functions, for collections whose storage already exposes them. */
class FunctionSyncAdapter(
    override val name: String,
    private val read: suspend () -> Map<String, String>,
    private val write: suspend (Map<String, String>, Set<String>) -> Unit,
    override val changes: Flow<Unit>
) : SyncAdapter {
    override suspend fun snapshot(): Map<String, String> = read()
    override suspend fun apply(upserts: Map<String, String>, deletes: Set<String>) = write(upserts, deletes)
}

/**
 * Every key of the settings store except the device-local ones. Values use the settings
 * backup's typed encoding, so an Int stays an Int on the other device.
 */
class SettingsSyncAdapter(
    private val dataStore: DataStore<Preferences>,
    private val onApplied: suspend () -> Unit = {}
) : SyncAdapter {
    override val name = DeviceSync.SETTINGS

    override suspend fun snapshot(): Map<String, String> = encode(dataStore.data.first())

    private fun encode(preferences: Preferences): Map<String, String> =
        preferences.asMap()
            .filterKeys { DeviceSync.isSyncedSetting(it.name) }
            .mapNotNull { (key, value) -> SettingsBackup.encodeEntry(key.name, value) }
            .associate { (name, entry) -> name to SyncMerge.json.encodeToString(SettingsBackup.Entry.serializer(), entry) }

    override suspend fun apply(upserts: Map<String, String>, deletes: Set<String>) {
        dataStore.edit { preferences ->
            // Keys are typed; removing by name also clears one that changed type.
            val existing = preferences.asMap().keys.associateBy { it.name }
            (deletes + upserts.keys).filter(DeviceSync::isSyncedSetting).forEach { name ->
                existing[name]?.let { preferences.remove(it) }
            }
            upserts.filterKeys(DeviceSync::isSyncedSetting).forEach { (name, value) ->
                runCatching { SyncMerge.json.decodeFromString(SettingsBackup.Entry.serializer(), value) }
                    .getOrNull()
                    ?.let { SettingsBackup.decodeInto(preferences, name, it) }
            }
        }
        // As a backup restore does: a document written by an older build is migrated once.
        AlertPreferences(dataStore).updateFilterStateDocument { it }
        onApplied()
    }

    override val changes: Flow<Unit> = dataStore.data.map(::encode).distinctUntilChanged().map { }
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

    override suspend fun apply(upserts: Map<String, String>, deletes: Set<String>) {
        deletes.forEach { key ->
            when {
                key.startsWith(SETUP) -> dao.delete(key.removePrefix(SETUP))
                key.startsWith(AREA) -> dao.deleteArea(key.removePrefix(AREA))
                key.startsWith(HISTORY) -> dao.deleteHistory(key.removePrefix(HISTORY))
            }
        }
        upserts.forEach { (key, value) ->
            runCatching {
                when {
                    key.startsWith(SETUP) -> dao.save(json.decodeFromString(CatchSetupEntity.serializer(), value))
                    key.startsWith(AREA) -> dao.saveArea(json.decodeFromString(CatchAreaEntity.serializer(), value))
                    key.startsWith(HISTORY) -> dao.addHistory(json.decodeFromString(CatchHistoryEntity.serializer(), value))
                }
            }
        }
        if (upserts.keys.any { it.startsWith(HISTORY) }) dao.trimHistory(CATCH_ROUTE_HISTORY_CAP)
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

    override suspend fun apply(upserts: Map<String, String>, deletes: Set<String>) {
        if (ROSTER in deletes) dao.clear()
        upserts[ROSTER]?.let { value ->
            runCatching { json.decodeFromString(roster, value) }.getOrNull()?.let { dao.replaceAll(it) }
        }
        if (SUMMARY in deletes || SUMMARY in upserts) {
            val summary = upserts[SUMMARY]?.let { runCatching { json.decodeFromString(Summary.serializer(), it) }.getOrNull() } ?: Summary()
            dataStore.edit { preferences ->
                preferences.putOrRemove(RaidCounterPreferences.PG_COUNT_KEY, summary.count)
                preferences.putOrRemove(RaidCounterPreferences.PG_MATCHED_KEY, summary.matched)
                preferences.putOrRemove(RaidCounterPreferences.PG_FILE_KEY, summary.fileName)
                preferences.putOrRemove(RaidCounterPreferences.PG_IMPORTED_AT_KEY, summary.importedAt)
                preferences.putOrRemove(RaidCounterPreferences.PG_EXPANSION_KEY, summary.expansionVersion)
            }
        }
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

    override suspend fun apply(upserts: Map<String, String>, deletes: Set<String>) {
        if (GODEX in deletes) writeGoDex(null)
        upserts[GODEX]?.let { runCatching { json.decodeFromString(GoDexSignIn.serializer(), it) }.getOrNull() }
            ?.let { writeGoDex(it) }
        if (POKEBATTLER in deletes) writePokebattler(null)
        upserts[POKEBATTLER]?.let { runCatching { json.decodeFromString(PokebattlerSignIn.serializer(), it) }.getOrNull() }
            ?.let { writePokebattler(it) }
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

    override suspend fun apply(upserts: Map<String, String>, deletes: Set<String>) {
        val rows = state.devices().toMutableMap()
        deletes.forEach { rows.remove(it) }
        upserts.filterKeys { it != selfId }.forEach { (id, value) ->
            runCatching { json.decodeFromString(DeviceRow.serializer(), value) }.getOrNull()?.let { rows[id] = it }
        }
        state.writeDevices(rows)
    }

    override val changes: Flow<Unit> = kotlinx.coroutines.flow.emptyFlow()
}
