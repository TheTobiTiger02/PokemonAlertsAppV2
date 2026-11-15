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
import com.example.pokemonalertsv2.data.WHATS_NEW_SEEN_KEY
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.data.backup.SettingsBackup
import com.example.pokemonalertsv2.data.counters.PokebattlerAccount
import com.example.pokemonalertsv2.data.counters.PokebattlerAuth
import com.example.pokemonalertsv2.data.counters.RaidCounterPreferences
import com.example.pokemonalertsv2.data.database.AppDatabase
import com.example.pokemonalertsv2.data.godex.GoDexPreferences
import com.example.pokemonalertsv2.data.godex.GoDexSessionState
import com.example.pokemonalertsv2.fcm.FcmTopicSubscriptionManager
import com.example.pokemonalertsv2.hunt.HuntRepository
import com.example.pokemonalertsv2.util.KeystoreCipher
import com.example.pokemonalertsv2.work.DeviceSyncWorker
import com.example.pokemonalertsv2.work.PushTopicSyncWorker
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Device sync: settings, filters, hunt and catch-route data, the Poke Genie roster and
 * sign-ins kept the same on every device of one owner, through the backend as an end-to-end
 * encrypted relay. This object wires the engine to the app; the cycle itself is
 * [DeviceSyncEngine], the server protocol DEVICE_SYNC.md in the backend repo.
 */
object DeviceSync {
    private const val TAG = "DeviceSync"

    const val SETTINGS = "settings"
    const val HUNTS = "hunts"
    const val ROUTES = "routes"
    const val POKEGENIE = "pokegenie"
    const val SIGNINS = "signins"
    const val DEVICES = "devices"
    val COLLECTIONS = listOf(SETTINGS, HUNTS, ROUTES, POKEGENIE, SIGNINS, DEVICES)

    const val NOTICE_DELETED_ELSEWHERE = "deleted_elsewhere"

    /**
     * Settings that describe this device rather than the owner's choices: overlay window
     * geometry, onboarding and What's New, and the last catch. Together with
     * [SettingsBackup.EXCLUDED_KEYS] (sync cursors, silence, Raid Watch, sign-in keys that
     * travel in `signins`, the Poke Genie summary that travels in `pokegenie`) they never sync.
     * Names come from the key constants, as in [SettingsBackup.EXCLUDED_KEYS].
     */
    val LOCAL_ONLY_KEYS: Set<String> = setOf(
        FLOATING_MAP_OPACITY_KEY, FLOATING_MAP_MINIMIZED_KEY, FLOATING_BUBBLE_X_KEY, FLOATING_BUBBLE_Y_KEY,
        FLOATING_MAP_X_KEY, FLOATING_MAP_Y_KEY, FLOATING_MAP_WIDTH_KEY, FLOATING_MAP_HEIGHT_KEY,
        CATCH_WINDOW_X_KEY, CATCH_WINDOW_Y_KEY, CATCH_WINDOW_WIDTH_KEY, CATCH_WINDOW_HEIGHT_KEY,
        ONBOARDING_COMPLETED_KEY, WHATS_NEW_SEEN_KEY,
        LAST_CAUGHT_ID_KEY, LAST_CAUGHT_NAME_KEY, LAST_CAUGHT_AT_KEY, LAST_CAUGHT_RAID_WATCHED_KEY
    ).mapTo(mutableSetOf()) { it.name }

    fun isSyncedSetting(name: String): Boolean =
        name !in SettingsBackup.EXCLUDED_KEYS && name !in LOCAL_ONLY_KEYS

    /** A `pokemonalerts://sync/join?code=…` link waiting for the sync page to confirm it. */
    val pendingJoinCode = MutableStateFlow<String?>(null)

    /** What an FCM data message means for sync. */
    enum class PingAction { NOT_A_PING, OWN_WRITE, SYNC }

    fun pingAction(data: Map<String, String>, selfId: String): PingAction = when {
        data["devsync"] != "1" -> PingAction.NOT_A_PING
        data["origin"] == selfId -> PingAction.OWN_WRITE
        else -> PingAction.SYNC
    }

    @Volatile
    private var instance: Wiring? = null

    private class Wiring(val engine: DeviceSyncEngine, val state: SyncStateStore, val adapters: List<SyncAdapter>)

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
            SettingsSyncAdapter(app.alertPreferencesDataStore) {
                // Filters decide the push topics; the app's filter observer also covers widgets.
                PushTopicSyncWorker.triggerSync(app)
            },
            HuntRepository.getInstance(app).let { hunts ->
                FunctionSyncAdapter(HUNTS, hunts::syncSnapshot, hunts::applySync, hunts.syncChanges.map { })
            },
            RoutesSyncAdapter(CatchRouteStore.get(app).dao),
            PokeGenieSyncAdapter(AppDatabase.getDatabase(app).pokeGenieDao(), app.alertPreferencesDataStore),
            signInsAdapter(app),
            DevicesSyncAdapter(deviceId, state) { if (engine.leaving) null else ownRow(state) }
        )
        engine = DeviceSyncEngine(
            transport = RetrofitSyncTransport(PokemonAlertsApi.syncService),
            state = state,
            adapters = adapters,
            deviceId = deviceId,
            onGroupChanged = { old, new ->
                old?.let { runCatching { FirebaseMessaging.getInstance().unsubscribeFromTopic(it.topic).await() } }
                state.setTopicSubscribed(false)
                if (new != null) {
                    subscribeTopic(state, new)
                    DeviceSyncWorker.schedulePeriodic(app)
                } else {
                    DeviceSyncWorker.cancelPeriodic(app)
                }
            }
        )
        return Wiring(engine, state, adapters)
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
                pokebattler.account.map { }
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
     * Local-change triggers, started once from the Application: each adapter's [SyncAdapter.changes]
     * after its first emission, debounced, schedules a sync of that collection. A change made
     * by applying the group's data diffs empty and uploads nothing.
     */
    @OptIn(FlowPreview::class)
    fun startLocalChangeTriggers(context: Context, scope: CoroutineScope) {
        val app = context.applicationContext
        wiring(app).adapters.forEach { adapter ->
            scope.launch(Dispatchers.IO) {
                runCatching {
                    adapter.changes.drop(1).debounce(LOCAL_CHANGE_DEBOUNCE_MILLIS).collect {
                        if (state(app).group() != null) DeviceSyncWorker.trigger(app)
                    }
                }.onFailure { Log.w(TAG, "Change trigger for ${adapter.name} stopped", it) }
            }
        }
        scope.launch(Dispatchers.IO) {
            runCatching { if (state(app).group() != null) DeviceSyncWorker.schedulePeriodic(app) }
        }
    }

    private const val LOCAL_CHANGE_DEBOUNCE_MILLIS = 5_000L
    private const val FOREGROUND_THROTTLE_MILLIS = 60_000L

    @Volatile
    private var lastForegroundSync = 0L

    /** The app came to the foreground: sync everything, at most once a minute. */
    fun onForeground(context: Context, scope: CoroutineScope) {
        val now = System.currentTimeMillis()
        if (now - lastForegroundSync < FOREGROUND_THROTTLE_MILLIS) return
        lastForegroundSync = now
        scope.launch(Dispatchers.IO) {
            runCatching { if (state(context).group() != null) DeviceSyncWorker.trigger(context) }
        }
    }

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
