package com.example.pokemonalertsv2.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Device sync's own state, apart from the settings store so that neither a settings backup
 * nor sync itself ever carries it: the group secret (Keystore-encrypted), the per-collection
 * shadows and statuses, and the device rows last seen from the group.
 *
 * This file is excluded from Auto Backup: a Keystore-encrypted secret restored onto another
 * phone cannot be decrypted there.
 */
internal val Context.deviceSyncDataStore: DataStore<Preferences> by preferencesDataStore(name = "device_sync")

/** Per collection, what the sync page shows. */
@Serializable
data class CollectionStatus(
    val lastSyncAtMillis: Long = 0,
    val error: String? = null,
    /** The group holds this collection in a newer format than this app understands. */
    val unsupported: Boolean = false
)

/** One device of the group, as the devices collection carries it. */
@Serializable
data class DeviceRow(
    val name: String,
    val model: String = "",
    val appVersion: String = "",
    val seenAtMillis: Long = 0
)

/** What the UI needs, read in one go. */
data class SyncStateSnapshot(
    val connected: Boolean,
    val statuses: Map<String, CollectionStatus>,
    /** Other devices of the group, by device id. */
    val devices: Map<String, DeviceRow>,
    val ownRow: DeviceRow?,
    val notice: String?
)

/** Encrypts the group secret at rest. Production uses the Android Keystore; tests use an identity. */
interface SecretSealer {
    fun seal(plain: String): String
    fun open(sealed: String): String?
}

class SyncStateStore(
    private val dataStore: DataStore<Preferences>,
    private val sealer: SecretSealer
) {
    private val json = SyncMerge.json
    private val deviceMap = MapSerializer(String.serializer(), DeviceRow.serializer())

    /** The group, or null when not connected or when the stored secret can no longer be decrypted. */
    suspend fun group(): SyncGroup? = decodeGroup(dataStore.data.first())

    private fun decodeGroup(preferences: Preferences): SyncGroup? {
        val sealed = preferences[SECRET_KEY] ?: return null
        return runCatching {
            val code = sealer.open(sealed) ?: return null
            SyncCrypto.decodePairingCode(code)?.let(::SyncGroup)
        }.getOrNull()
    }

    val snapshots: Flow<SyncStateSnapshot> = dataStore.data.map { preferences ->
        SyncStateSnapshot(
            connected = decodeGroup(preferences) != null,
            statuses = DeviceSync.COLLECTIONS.associateWith { name ->
                preferences[statusKey(name)]?.let { runCatching { json.decodeFromString(CollectionStatus.serializer(), it) }.getOrNull() }
                    ?: CollectionStatus()
            },
            devices = preferences.devices(),
            ownRow = preferences[OWN_ROW_KEY]?.let { runCatching { json.decodeFromString(DeviceRow.serializer(), it) }.getOrNull() },
            notice = preferences[NOTICE_KEY]
        )
    }

    /**
     * Starts a group. Shadows are written before the secret, so a crash in between leaves
     * this device not connected rather than connected with a half-made baseline.
     */
    suspend fun connect(group: SyncGroup, shadows: Map<String, SyncShadow>) {
        dataStore.edit { preferences ->
            clearGroupState(preferences)
            shadows.forEach { (name, shadow) -> preferences[shadowKey(name)] = json.encodeToString(SyncShadow.serializer(), shadow) }
            preferences[SECRET_KEY] = sealer.seal(group.pairingCode)
        }
    }

    /** Forgets the group. Local data stays; [notice] stays on the sync page until the next set-up or join. */
    suspend fun disconnect(notice: String? = null) {
        dataStore.edit { preferences ->
            clearGroupState(preferences)
            if (notice != null) preferences[NOTICE_KEY] = notice
        }
    }

    private fun clearGroupState(preferences: MutablePreferences) {
        preferences.remove(SECRET_KEY)
        preferences.remove(NOTICE_KEY)
        preferences.remove(DEVICES_KEY)
        preferences.remove(OWN_ROW_KEY)
        preferences.remove(TOPIC_SUBSCRIBED_KEY)
        preferences.remove(CONFIRMED_KEY)
        DeviceSync.COLLECTIONS.forEach { name ->
            preferences.remove(shadowKey(name))
            preferences.remove(statusKey(name))
        }
    }

    suspend fun shadow(collection: String): SyncShadow =
        dataStore.data.first()[shadowKey(collection)]
            ?.let { runCatching { json.decodeFromString(SyncShadow.serializer(), it) }.getOrNull() }
            ?: SyncShadow()

    suspend fun writeShadow(collection: String, shadow: SyncShadow) {
        dataStore.edit { it[shadowKey(collection)] = json.encodeToString(SyncShadow.serializer(), shadow) }
    }

    suspend fun writeStatus(collection: String, status: CollectionStatus) {
        dataStore.edit { it[statusKey(collection)] = json.encodeToString(CollectionStatus.serializer(), status) }
    }

    suspend fun status(collection: String): CollectionStatus =
        dataStore.data.first()[statusKey(collection)]
            ?.let { runCatching { json.decodeFromString(CollectionStatus.serializer(), it) }.getOrNull() }
            ?: CollectionStatus()

    /** The newest change time this device has seen anywhere; see [DeviceSyncEngine.stamp]. */
    suspend fun highestStamp(): Long = dataStore.data.first()[HIGHEST_STAMP_KEY] ?: 0L

    suspend fun raiseHighestStamp(stamp: Long) {
        dataStore.edit { preferences ->
            if (stamp > (preferences[HIGHEST_STAMP_KEY] ?: 0L)) preferences[HIGHEST_STAMP_KEY] = stamp
        }
    }

    /** Other devices' rows as last merged, keyed by device id. */
    suspend fun devices(): Map<String, DeviceRow> = dataStore.data.first().devices()

    suspend fun writeDevices(devices: Map<String, DeviceRow>) {
        dataStore.edit { it[DEVICES_KEY] = json.encodeToString(deviceMap, devices) }
    }

    private fun Preferences.devices(): Map<String, DeviceRow> =
        this[DEVICES_KEY]?.let { runCatching { json.decodeFromString(deviceMap, it) }.getOrNull() } ?: emptyMap()

    suspend fun ownRow(): DeviceRow? =
        dataStore.data.first()[OWN_ROW_KEY]?.let { runCatching { json.decodeFromString(DeviceRow.serializer(), it) }.getOrNull() }

    suspend fun writeOwnRow(row: DeviceRow) {
        dataStore.edit { it[OWN_ROW_KEY] = json.encodeToString(DeviceRow.serializer(), row) }
    }

    /**
     * Whether the server is known to hold this group. A group just created here exists only
     * after its first upload, so "not found" means "not yet" until then, and "deleted" after.
     */
    suspend fun confirmed(): Boolean = dataStore.data.first()[CONFIRMED_KEY] ?: false

    suspend fun setConfirmed() {
        dataStore.edit { it[CONFIRMED_KEY] = true }
    }

    /**
     * Settings this device keeps to itself ("only on this device"), by item key. A choice about
     * this device, not about the group: it survives leaving and rejoining.
     */
    val localOverrides: Flow<Set<String>> = dataStore.data.map { it[LOCAL_OVERRIDES_KEY] ?: emptySet() }

    suspend fun localOverrides(): Set<String> = dataStore.data.first()[LOCAL_OVERRIDES_KEY] ?: emptySet()

    suspend fun setLocalOverride(key: String, local: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[LOCAL_OVERRIDES_KEY] ?: emptySet()
            preferences[LOCAL_OVERRIDES_KEY] = if (local) current + key else current - key
        }
    }

    suspend fun topicSubscribed(): Boolean = dataStore.data.first()[TOPIC_SUBSCRIBED_KEY] ?: false

    suspend fun setTopicSubscribed(subscribed: Boolean) {
        dataStore.edit { it[TOPIC_SUBSCRIBED_KEY] = subscribed }
    }

    private fun shadowKey(collection: String) = stringPreferencesKey("shadow_$collection")
    private fun statusKey(collection: String) = stringPreferencesKey("status_$collection")

    private companion object {
        val SECRET_KEY = stringPreferencesKey("secret")
        val NOTICE_KEY = stringPreferencesKey("notice")
        val DEVICES_KEY = stringPreferencesKey("devices")
        val OWN_ROW_KEY = stringPreferencesKey("own_device_row")
        val HIGHEST_STAMP_KEY = longPreferencesKey("highest_stamp")
        val TOPIC_SUBSCRIBED_KEY = booleanPreferencesKey("topic_subscribed")
        val CONFIRMED_KEY = booleanPreferencesKey("confirmed")
        val LOCAL_OVERRIDES_KEY = androidx.datastore.preferences.core.stringSetPreferencesKey("local_overrides")
    }
}
