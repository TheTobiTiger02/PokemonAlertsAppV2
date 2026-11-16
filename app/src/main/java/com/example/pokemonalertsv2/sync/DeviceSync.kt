package com.example.pokemonalertsv2.sync

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.example.pokemonalertsv2.BuildConfig
import com.example.pokemonalertsv2.catchroutes.CatchRouteStore
import com.example.pokemonalertsv2.data.CATCH_WINDOW_HEIGHT_KEY
import com.example.pokemonalertsv2.data.CATCH_WINDOW_WIDTH_KEY
import com.example.pokemonalertsv2.data.CATCH_WINDOW_X_KEY
import com.example.pokemonalertsv2.data.CATCH_WINDOW_Y_KEY
import com.example.pokemonalertsv2.data.FLOATING_BUBBLE_X_KEY
import com.example.pokemonalertsv2.data.FLOATING_BUBBLE_Y_KEY
import com.example.pokemonalertsv2.data.FLOATING_MAP_HEIGHT_KEY
import com.example.pokemonalertsv2.data.FLOATING_MAP_MINIMIZED_KEY
import com.example.pokemonalertsv2.data.FLOATING_MAP_OPACITY_KEY
import com.example.pokemonalertsv2.data.FLOATING_MAP_WIDTH_KEY
import com.example.pokemonalertsv2.data.FLOATING_MAP_X_KEY
import com.example.pokemonalertsv2.data.FLOATING_MAP_Y_KEY
import com.example.pokemonalertsv2.data.LAST_CAUGHT_AT_KEY
import com.example.pokemonalertsv2.data.LAST_CAUGHT_ID_KEY
import com.example.pokemonalertsv2.data.LAST_CAUGHT_NAME_KEY
import com.example.pokemonalertsv2.data.LAST_CAUGHT_RAID_WATCHED_KEY
import com.example.pokemonalertsv2.data.ONBOARDING_COMPLETED_KEY
import com.example.pokemonalertsv2.data.PokemonAlertsApi
import com.example.pokemonalertsv2.data.SILENCE_UNTIL_KEY
import com.example.pokemonalertsv2.data.WHATS_NEW_SEEN_KEY
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.data.backup.SettingsBackup
import com.example.pokemonalertsv2.data.counters.PokebattlerAccount
import com.example.pokemonalertsv2.data.counters.PokebattlerAuth
import com.example.pokemonalertsv2.data.counters.RaidCounterPreferences
import com.example.pokemonalertsv2.data.database.AppDatabase
import com.example.pokemonalertsv2.data.godex.GoDexPreferences
import com.example.pokemonalertsv2.data.godex.GoDexSessionState
import com.example.pokemonalertsv2.data.net.Http
import com.example.pokemonalertsv2.fcm.FcmTopicSubscriptionManager
import com.example.pokemonalertsv2.hunt.HuntRepository
import com.example.pokemonalertsv2.tracking.ArrivalTrackingRepository
import com.example.pokemonalertsv2.tracking.arrivalTrackingDataStore
import com.example.pokemonalertsv2.util.KeystoreCipher
import com.example.pokemonalertsv2.util.exponentialBackoffMillis
import com.example.pokemonalertsv2.work.DeviceSyncWorker
import com.example.pokemonalertsv2.work.PushTopicSyncWorker
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Device sync: settings, filters, hunt and catch-route data, running sessions, the Poke Genie
 * roster and sign-ins kept the same on every device of one owner, through the backend as an
 * end-to-end encrypted relay. This object wires the engine to the app; the cycle itself is
 * [DeviceSyncEngine], the server protocol DEVICE_SYNC.md in the backend repo.
 *
 * Changes reach the other devices two ways: the live stream ([SyncEventStream]) while the app
 * is open or runs a hunt or route, usually within a fraction of a second, and an FCM ping for a
 * closed app.
 */
object DeviceSync {
    private const val TAG = "DeviceSync"

    const val SETTINGS = "settings"
    const val HUNTS = "hunts"
    const val ROUTES = "routes"
    const val POKEGENIE = "pokegenie"
    const val SIGNINS = "signins"
    const val DEVICES = "devices"
    /** Running hunt, catch route, journey and Raid Watch, and commands to the device running them. */
    const val LIVE = "live"
    /** GoDex caught marks not yet written back. */
    const val GODEX = "godex"
    /** Each device's home screen widget configurations, to copy from. */
    const val WIDGETS = "widgets"
    val COLLECTIONS = listOf(SETTINGS, HUNTS, ROUTES, POKEGENIE, SIGNINS, DEVICES, LIVE, GODEX, WIDGETS)

    /** Presence is relayed, not stored; it has an AAD of its own (DEVICE_SYNC.md, "Presence"). */
    const val PRESENCE = "presence"

    const val NOTICE_DELETED_ELSEWHERE = "deleted_elsewhere"

    /**
     * Settings that describe this device rather than the owner's choices: overlay window
     * geometry, onboarding and What's New, the last catch, and GoDex's own sync status.
     * Together with [SYNC_EXCLUDED_KEYS] (sync cursors, Raid Watch which travels in `live`,
     * sign-in keys that travel in `signins`, the Poke Genie summary that travels in
     * `pokegenie`) they never sync. Names come from the key constants, as in
     * [SettingsBackup.EXCLUDED_KEYS].
     */
    val LOCAL_ONLY_KEYS: Set<String> = setOf(
        FLOATING_MAP_OPACITY_KEY, FLOATING_MAP_MINIMIZED_KEY, FLOATING_BUBBLE_X_KEY, FLOATING_BUBBLE_Y_KEY,
        FLOATING_MAP_X_KEY, FLOATING_MAP_Y_KEY, FLOATING_MAP_WIDTH_KEY, FLOATING_MAP_HEIGHT_KEY,
        CATCH_WINDOW_X_KEY, CATCH_WINDOW_Y_KEY, CATCH_WINDOW_WIDTH_KEY, CATCH_WINDOW_HEIGHT_KEY,
        ONBOARDING_COMPLETED_KEY, WHATS_NEW_SEEN_KEY,
        LAST_CAUGHT_ID_KEY, LAST_CAUGHT_NAME_KEY, LAST_CAUGHT_AT_KEY, LAST_CAUGHT_RAID_WATCHED_KEY,
        GoDexPreferences.LAST_SYNC_KEY, GoDexPreferences.LAST_WRITE_KEY, GoDexPreferences.LAST_WRITE_ERROR_KEY
    ).mapTo(mutableSetOf()) { it.name }

    /** Keyed by widget ids that exist on one device only. */
    private val LOCAL_ONLY_PREFIXES = listOf("events_widget_hidden_", "events_widget_types_")

    /**
     * Backup leaves out "silenced until": a restored phone should start fresh. Sync carries it,
     * so a snooze applies on every device (or, kept to one device, only there).
     *
     * "Already notified" stays per device: it changes with every alert, so syncing it would
     * wake every device on every alert, and it could not stop a double notification anyway,
     * because both devices receive the push at the same moment. Keeping notifications to one
     * device ("only on this device") is what does that.
     */
    private val SYNC_EXCLUDED_KEYS: Set<String> = SettingsBackup.EXCLUDED_KEYS - SILENCE_UNTIL_KEY.name

    fun isSyncedSetting(name: String): Boolean =
        name !in SYNC_EXCLUDED_KEYS && name !in LOCAL_ONLY_KEYS && LOCAL_ONLY_PREFIXES.none { name.startsWith(it) }

    /** Prefix of the journey preferences inside the settings collection. */
    const val ARRIVAL_PREFIX = "arrival/"

    /**
     * Settings a device may keep to itself ("only on this device"), as settings item keys,
     * grouped the way the sync page offers them: whether and how this phone notifies, and how
     * it looks.
     */
    val OVERRIDE_GROUPS: Map<String, List<String>> = linkedMapOf(
        "notifications" to listOf("notifications_enabled"),
        "vibrate" to listOf("notification_vibrate"),
        "content" to listOf("notification_content_v1"),
        "quiet_hours" to listOf("quiet_hours_enabled", "quiet_hours_start_minute", "quiet_hours_end_minute"),
        "silence" to listOf(SILENCE_UNTIL_KEY.name),
        "theme" to listOf("theme_mode"),
        "pip" to listOf("auto_enter_map_pip"),
        "journey_overlay" to listOf("journey_overlay_enabled"),
        "watch" to listOf("health_connect_show_watch_stats")
    )

    /** Keeps the settings of [group] ([OVERRIDE_GROUPS]) to this device, or hands them back to the group's values. */
    suspend fun setLocalOverride(context: Context, group: String, local: Boolean) {
        val keys = OVERRIDE_GROUPS[group] ?: return
        val state = state(context)
        keys.forEach { state.setLocalOverride(it, local) }
        if (!local) keys.forEach { engine(context).adoptGroupValue(SETTINGS, it) }
    }

    /** A `pokemonalerts://sync/join?code=…` link waiting for the sync page to confirm it. */
    val pendingJoinCode = MutableStateFlow<String?>(null)

    /** What an FCM data message means for sync. */
    enum class PingAction { NOT_A_PING, OWN_WRITE, SYNC, DELETED }

    fun pingAction(data: Map<String, String>, selfId: String): PingAction = when {
        data["devsync"] != "1" -> PingAction.NOT_A_PING
        data["origin"] == selfId -> PingAction.OWN_WRITE
        data["deleted"] == "1" -> PingAction.DELETED
        else -> PingAction.SYNC
    }

    @Volatile
    private var instance: Wiring? = null

    private class Wiring(
        val engine: DeviceSyncEngine,
        val state: SyncStateStore,
        val adapters: List<SyncAdapter>,
        val service: SyncService
    )

    private fun wiring(context: Context): Wiring = instance ?: synchronized(this) {
        instance ?: build(context.applicationContext).also { instance = it }
    }

    fun engine(context: Context): DeviceSyncEngine = wiring(context).engine
    fun state(context: Context): SyncStateStore = wiring(context).state
    fun deviceId(context: Context): String = FcmTopicSubscriptionManager.installId(context)

    private fun build(app: Context): Wiring {
        val cipher = KeystoreCipher("device_sync_key")
        val state = SyncStateStore(app.deviceSyncDataStore, object : SecretSealer {
            override fun seal(plain: String) = cipher.encrypt(plain)
            override fun open(sealed: String) = cipher.decrypt(sealed)
        })
        val deviceId = deviceId(app)
        lateinit var engine: DeviceSyncEngine
        val adapters = listOf(
            SettingsSyncAdapter(
                sources = listOf(
                    SettingsSource("", app.alertPreferencesDataStore, ::isSyncedSetting),
                    SettingsSource(ARRIVAL_PREFIX, app.arrivalTrackingDataStore) { it in ArrivalTrackingRepository.SYNCED_KEYS }
                ),
                localKeys = { state.localOverrides() },
                // Filters decide the push topics; the app's filter observer also covers widgets.
                onApplied = { PushTopicSyncWorker.triggerSync(app) }
            ),
            HuntRepository.getInstance(app).let { hunts ->
                FunctionSyncAdapter(HUNTS, hunts::syncSnapshot, hunts::applySync, hunts.syncChanges.map { })
            },
            RoutesSyncAdapter(CatchRouteStore.get(app).dao),
            PokeGenieSyncAdapter(AppDatabase.getDatabase(app).pokeGenieDao(), app.alertPreferencesDataStore),
            signInsAdapter(app),
            DevicesSyncAdapter(deviceId, state) { if (engine.leaving) null else ownRow(state) },
            GoDexSyncAdapter(AppDatabase.getDatabase(app).goDexEntryDao()),
            WidgetsSyncAdapter(app, deviceId),
            com.example.pokemonalertsv2.sync.live.LiveSyncAdapter(com.example.pokemonalertsv2.sync.live.LiveStore.get(app))
        )
        val service = PokemonAlertsApi.syncService
        engine = DeviceSyncEngine(
            transport = RetrofitSyncTransport(service, deviceId),
            state = state,
            adapters = adapters,
            deviceId = deviceId,
            onGroupChanged = { old, new ->
                if (new == null) {
                    // The group's mirrored sessions and other devices' widgets go with it, so a
                    // later join does not upload them as this device's own items. This device's
                    // own running session is published again from its own stores.
                    com.example.pokemonalertsv2.sync.live.LiveStore.get(app).clear()
                    WidgetsSyncAdapter.clearCatalog(app)
                }
                old?.let { runCatching { FirebaseMessaging.getInstance().unsubscribeFromTopic(it.topic).await() } }
                state.setTopicSubscribed(false)
                if (new != null) {
                    subscribeTopic(state, new)
                    DeviceSyncWorker.schedulePeriodic(app)
                } else {
                    DeviceSyncWorker.cancelPeriodic(app)
                }
                groupChanged.value = new?.groupId
            },
            onDeferredSync = { DeviceSyncWorker.trigger(app) },
            appVersion = { BuildConfig.VERSION_CODE.toLong() }
        )
        return Wiring(engine, state, adapters, service)
    }

    /**
     * This device's row. Its time only moves every few hours: the row is uploaded whenever
     * it changes, and a time refreshed on every sync would ping the other devices forever.
     */
    private suspend fun ownRow(state: SyncStateStore): DeviceRow {
        val now = System.currentTimeMillis()
        val current = state.ownRow()
        val fresh = DeviceRow(
            name = Build.MODEL.orEmpty(),
            model = listOf(Build.MANUFACTURER, Build.MODEL).filterNot { it.isNullOrBlank() }.joinToString(" "),
            appVersion = BuildConfig.VERSION_NAME,
            seenAtMillis = current?.seenAtMillis ?: now
        )
        val row = if (current == null || current.copy(seenAtMillis = 0) != fresh.copy(seenAtMillis = 0) ||
            now - current.seenAtMillis > SEEN_REFRESH_MILLIS
        ) fresh.copy(seenAtMillis = now) else current
        if (row != current) state.writeOwnRow(row)
        return row
    }

    private const val SEEN_REFRESH_MILLIS = 6L * 60 * 60 * 1000

    /** Reads and writes the two sign-ins through their own stores, which keep them Keystore-encrypted. */
    private fun signInsAdapter(app: Context): SyncAdapter {
        val goDex = GoDexPreferences(app.alertPreferencesDataStore)
        val pokebattler = PokebattlerAuth.getInstance(app)
        val dataStore = app.alertPreferencesDataStore
        return SignInsSyncAdapter(
            readGoDex = {
                goDex.config.first().takeIf { it.sessionCookies.isNotBlank() }
                    ?.let { GoDexSignIn(it.sessionCookies, it.writeBackUrl, it.sessionState.name) }
            },
            writeGoDex = { signIn ->
                if (signIn == null) {
                    goDex.clearSession()
                } else {
                    val state = runCatching { GoDexSessionState.valueOf(signIn.state) }.getOrDefault(GoDexSessionState.AUTHENTICATED)
                    goDex.applySyncedSession(signIn.cookies, signIn.writeBackUrl, state)
                }
            },
            readPokebattler = {
                val account = pokebattler.account.value
                val token = pokebattler.token()
                if (account == null || token.isNullOrBlank()) null
                else PokebattlerSignIn(token, account.userId, account.displayName, dataStore.data.first()[RaidCounterPreferences.PB_TRAINER_KEY])
            },
            writePokebattler = { signIn ->
                if (signIn == null) {
                    pokebattler.clear()
                    RaidCounterPreferences(dataStore).clearPokebattlerTrainerNumber()
                } else {
                    pokebattler.save(signIn.token, PokebattlerAccount(signIn.userId, signIn.displayName))
                    dataStore.edit { prefs ->
                        if (signIn.trainer != null) prefs[RaidCounterPreferences.PB_TRAINER_KEY] = signIn.trainer
                        else prefs.remove(RaidCounterPreferences.PB_TRAINER_KEY)
                    }
                }
            },
            changes = merge(
                goDex.config.map { it.sessionCookies to it.sessionState }.distinctUntilChanged().map { },
                pokebattler.account.map { },
                // A token refresh for the same account changes nothing the account flow shows.
                dataStore.data.map { it[RaidCounterPreferences.PB_TRAINER_KEY] }.distinctUntilChanged().map { }
            )
        )
    }

    private suspend fun subscribeTopic(state: SyncStateStore, group: SyncGroup) {
        runCatching { FirebaseMessaging.getInstance().subscribeToTopic(group.topic).await() }
            .onSuccess { state.setTopicSubscribed(true) }
            .onFailure { Log.w(TAG, "Could not subscribe to the sync topic; retrying after the next sync", it) }
    }

    /** After a sync: (re)subscribes the group topic when a new FCM token or a failure left it off. */
    suspend fun ensureTopic(context: Context) {
        val state = state(context)
        val group = state.group() ?: return
        if (!state.topicSubscribed()) subscribeTopic(state, group)
    }

    /** Topic subscriptions are per token: a new token is on none of them. */
    fun onNewToken(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                state(context).setTopicSubscribed(false)
                ensureTopic(context)
            }
        }
    }

    /**
     * The other devices' half of "instant" for a closed app: a ping names the collection and
     * its new server version, so this syncs just that collection without listing the group
     * first, right here while FCM keeps the process awake for a high-priority message. A
     * version this device already has (the live stream got it first) costs no request.
     * Anything that fails is left to [DeviceSyncWorker].
     */
    suspend fun syncFromPing(context: Context, data: Map<String, String>) {
        val app = context.applicationContext
        if (data["deleted"] == "1") {
            runCatching { engine(app).onDeletedElsewhere() }
            return
        }
        val collection = data["collection"]?.takeIf { it in COLLECTIONS }
        val version = data["version"]?.toLongOrNull()
        if (collection == null || version == null) {
            DeviceSyncWorker.trigger(app)
            return
        }
        try {
            withTimeout(PING_SYNC_TIMEOUT_MILLIS) {
                engine(app).syncNow(listOf(collection), knownVersions = mapOf(collection to version))
            }
        } catch (cancelled: CancellationException) {
            DeviceSyncWorker.trigger(app)
            if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
        } catch (permanent: SyncPermanentException) {
            Log.w(TAG, "Sync after a ping failed for good", permanent)
        } catch (error: Exception) {
            Log.w(TAG, "Sync after a ping failed; leaving it to the worker", error)
            DeviceSyncWorker.trigger(app)
        }
    }

    /** FCM allows about 20 s for a high-priority message; leave room to hand over to the worker. */
    private const val PING_SYNC_TIMEOUT_MILLIS = 15_000L

    /**
     * Local-change triggers, started once from the Application: each adapter's [SyncAdapter.changes],
     * gathered for [SyncAdapter.debounceMillis], uploads that collection straight away against
     * the version this device last agreed with, so the other devices hear of it within a
     * second. A change made by applying the group's data diffs empty and costs no request.
     * A failure (offline, server error) falls back to [DeviceSyncWorker], which waits for a
     * network.
     */
    @OptIn(FlowPreview::class)
    fun startLocalChangeTriggers(context: Context, scope: CoroutineScope) {
        val app = context.applicationContext
        wiring(app).adapters.forEach { adapter ->
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val changes = if (adapter.debounceMillis <= 0) adapter.changes.conflate() else adapter.changes.debounce(adapter.debounceMillis)
                    changes.collect {
                        if (state(app).group() == null) return@collect
                        syncLocal(app, adapter.name)
                    }
                }.onFailure { Log.w(TAG, "Change trigger for ${adapter.name} stopped", it) }
            }
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                if (state(app).group() != null) {
                    DeviceSyncWorker.schedulePeriodic(app)
                    // An app update or rename changes this device's row.
                    syncLocal(app, DEVICES)
                }
            }
        }
    }

    private suspend fun syncLocal(app: Context, collection: String) {
        try {
            engine(app).syncLocalChange(collection)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (permanent: SyncPermanentException) {
            Log.w(TAG, "Sync of $collection failed for good", permanent)
        } catch (error: Exception) {
            Log.w(TAG, "Sync of $collection failed; leaving it to the worker", error)
            DeviceSyncWorker.trigger(app)
        }
    }

    /** Uploads [collection] now (a running session's own trigger, when it cannot wait for the change flow). */
    fun syncSoon(context: Context, collection: String) {
        val app = context.applicationContext
        liveScope.launch { if (state(app).group() != null) syncLocal(app, collection) }
    }

    private const val FOREGROUND_THROTTLE_MILLIS = 60_000L

    @Volatile
    private var lastForegroundSync = 0L

    /**
     * The app came to the foreground. The live stream's hello catches up on everything; this
     * worker is the fallback for a stream that does not connect, at most once a minute.
     */
    fun onForeground(context: Context, scope: CoroutineScope) {
        val app = context.applicationContext
        scope.launch(Dispatchers.IO) {
            delay(STREAM_GRACE_MILLIS)
            val now = System.currentTimeMillis()
            if (streamConnected.value || now - lastForegroundSync < FOREGROUND_THROTTLE_MILLIS) return@launch
            lastForegroundSync = now
            runCatching { if (state(app).group() != null) DeviceSyncWorker.trigger(app) }
        }
    }

    private const val STREAM_GRACE_MILLIS = 5_000L

    // -- Live stream ------------------------------------------------------------

    const val HOLDER_FOREGROUND = "foreground"
    const val HOLDER_HUNT = "hunt"
    const val HOLDER_CATCH_ROUTE = "catch_route"

    private val liveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val holders = MutableStateFlow<Set<String>>(emptySet())
    private val groupChanged = MutableStateFlow<String?>(null)
    private val _streamConnected = MutableStateFlow(false)

    /** Whether the live stream is open: changes from the other devices arrive within a second. */
    val streamConnected: StateFlow<Boolean> = _streamConnected.asStateFlow()

    /**
     * A device's last presence, decrypted. [receivedAtMillis] is this device's clock: how
     * stale a presence is must not depend on the sender's or the server's.
     */
    class PresenceRecord(val origin: String, val payload: ByteArray, val atMillis: Long, val receivedAtMillis: Long = System.currentTimeMillis())

    private val _presence = MutableStateFlow<Map<String, PresenceRecord>>(emptyMap())

    /** Other devices' presence, by device id. Empty while the stream is closed. */
    val presence: StateFlow<Map<String, PresenceRecord>> = _presence.asStateFlow()

    @Volatile
    private var streamJob: Job? = null

    /**
     * Keeps the live stream open while any holder holds it: the app in the foreground, or a
     * hunt or catch route running on this device (which must hear commands from the others
     * while Pokemon GO is in front or the screen is off).
     */
    fun holdLive(context: Context, holder: String) {
        holders.update { it + holder }
        ensureStream(context.applicationContext)
    }

    fun releaseLive(context: Context, holder: String) {
        holders.update { it - holder }
    }

    private val _streamConnectedAt = MutableStateFlow<Long?>(null)

    /** When the open stream connected (this device's clock); null while closed. */
    val streamConnectedAt: StateFlow<Long?> = _streamConnectedAt.asStateFlow()

    @Synchronized
    private fun ensureStream(app: Context) {
        if (streamJob?.isActive == true) return
        streamJob = liveScope.launch { runStream(app) }
    }

    /** Runs for the life of the process once started: idle while nothing holds the stream. */
    private suspend fun runStream(app: Context) {
        val stream = SyncEventStream(Http.base, BuildConfig.ALERTS_API_BASE_URL)
        val self = deviceId(app)
        var failures = 0
        while (true) {
            // Wait until something holds the stream and a group exists.
            merge(groupChanged.map { }, holders.map { }).first { holders.value.isNotEmpty() && state(app).group() != null }
            val group = state(app).group() ?: continue
            try {
                untilReleased(app, group) {
                    stream.events(group, self).collect { event ->
                        failures = 0
                        if (!_streamConnected.value) _streamConnectedAt.value = System.currentTimeMillis()
                        _streamConnected.value = true
                        handleEvent(app, group, self, event)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (gone: SyncGroupGoneException) {
                // Also what a group created here answers until its first upload lands, so the
                // stream decides nothing: a full sync lists the group and tells a deleted group
                // from one not uploaded yet.
                runSync(app) { engine(app).syncNow() }
            } catch (error: Exception) {
                Log.d(TAG, "Live stream ended: ${error.message}")
            } finally {
                _streamConnected.value = false
                _streamConnectedAt.value = null
                _presence.value = emptyMap()
            }
            // Every connection starts with hello, which resets the count: only connections
            // that fail before it back off.
            failures++
            if (holders.value.isNotEmpty()) delay(exponentialBackoffMillis(failures, STREAM_RETRY_BASE_MILLIS, STREAM_RETRY_MAX_MILLIS))
        }
    }

    /** Runs [block] until it ends, the last holder lets go, or this device leaves [group]. */
    private suspend fun untilReleased(app: Context, group: SyncGroup, block: suspend () -> Unit) {
        kotlinx.coroutines.coroutineScope {
            val work = launch { block() }
            val watcher = launch {
                merge(holders.map { }, groupChanged.map { }).first {
                    holders.value.isEmpty() || state(app).group()?.groupId != group.groupId
                }
                work.cancel()
            }
            work.join()
            watcher.cancel()
        }
    }

    private suspend fun handleEvent(app: Context, group: SyncGroup, self: String, event: SyncEvent) {
        val engine = engine(app)
        when (event) {
            is SyncEvent.Hello -> {
                lastForegroundSync = System.currentTimeMillis()
                _presence.value = event.presence.filter { it.origin != self }.mapNotNull { openPresence(group, it) }.associateBy { it.origin }
                // Every collection against the server's versions: a collection this device
                // already has costs nothing, and one missing on the server reads as version 0.
                runSync(app) { engine.syncNow(COLLECTIONS, knownVersions = COLLECTIONS.associateWith { event.versions[it] ?: 0L }) }
            }
            is SyncEvent.Change -> {
                if (event.origin == self || event.collection !in COLLECTIONS) return
                val inline = event.payload?.let { mapOf(event.collection to FetchResult.Blob(event.version, it)) } ?: emptyMap()
                // Off the stream's collector, so a slow apply does not hold up the next event.
                liveScope.launch {
                    runSync(app) { engine.syncNow(listOf(event.collection), mapOf(event.collection to event.version), inline) }
                }
            }
            is SyncEvent.Presence -> {
                if (event.origin == self) return
                openPresence(group, event)?.let { record -> _presence.value = _presence.value + (record.origin to record) }
            }
            SyncEvent.Deleted -> runCatching { engine.onDeletedElsewhere() }
        }
    }

    private suspend fun runSync(app: Context, block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (permanent: SyncPermanentException) {
            Log.w(TAG, "Live sync failed for good", permanent)
        } catch (error: Exception) {
            Log.w(TAG, "Live sync failed; leaving it to the worker", error)
            DeviceSyncWorker.trigger(app)
        }
    }

    private fun openPresence(group: SyncGroup, event: SyncEvent.Presence): PresenceRecord? = runCatching {
        PresenceRecord(event.origin, SyncCrypto.open(event.payload, group.encKey, SyncCrypto.aad(group.groupId, PRESENCE)), event.atMillis)
    }.getOrNull()

    /**
     * Sends this device's presence (a running session's position and readout) to the other
     * devices with the stream open. Never stored; a failure is dropped, the next one follows
     * within seconds. Returns how many streams heard it (this device's own included), or null
     * when it did not go out.
     */
    suspend fun postPresence(context: Context, plain: ByteArray): Int? {
        val app = context.applicationContext
        val group = state(app).group() ?: return null
        return runCatching {
            val sealed = SyncCrypto.seal(plain, group.encKey, SyncCrypto.aad(group.groupId, PRESENCE))
            val body = sealed.toRequestBody("application/octet-stream".toMediaType())
            val response = wiring(app).service.presence(group.groupId, group.authToken, deviceId(app), body)
            if (response.isSuccessful) response.headers()["X-Sync-Listeners"]?.toIntOrNull() ?: Int.MAX_VALUE else null
        }.getOrNull()
    }

    private const val STREAM_RETRY_BASE_MILLIS = 500L
    private const val STREAM_RETRY_MAX_MILLIS = 30_000L

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            val error = task.exception
            when {
                error != null -> continuation.resumeWithException(error)
                task.isCanceled -> continuation.cancel()
                else -> continuation.resume(task.result)
            }
        }
    }
}
