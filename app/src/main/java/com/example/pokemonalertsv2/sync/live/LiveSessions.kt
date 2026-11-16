package com.example.pokemonalertsv2.sync.live

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.catchroutes.CatchRouteController
import com.example.pokemonalertsv2.catchroutes.CatchSession
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.hunt.HuntCatch
import com.example.pokemonalertsv2.hunt.HuntRepository
import com.example.pokemonalertsv2.hunt.HuntSession
import com.example.pokemonalertsv2.hunt.HuntStats
import com.example.pokemonalertsv2.hunt.HuntSummary
import com.example.pokemonalertsv2.hunt.isUndoOfferLive
import com.example.pokemonalertsv2.hunt.undoLastCatch
import com.example.pokemonalertsv2.raidwatch.RaidWatchController
import com.example.pokemonalertsv2.raidwatch.RaidWatchStore
import com.example.pokemonalertsv2.sync.DeviceSync
import com.example.pokemonalertsv2.tracking.ArrivalTrackingRepository
import com.example.pokemonalertsv2.tracking.ArrivalTrackingService
import com.example.pokemonalertsv2.util.AppText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Running sessions across the group's devices: a hunt, a catch route, an "I'm going" journey
 * and Raid Watch started on one device show on the others, which can act on them.
 *
 * One device runs a session: the **owner**, which has the GPS, the planner and the
 * notification. It publishes the session into [LiveStore] (the `live` collection) from its
 * own stores, and its position as presence. The other devices are **mirrors**: they draw what
 * the owner publishes and send [LiveCommand]s, which the owner carries out as if pressed on its
 * own screen. A mirror never writes a native store, so nothing on it starts tracking. "Take
 * over" moves ownership: the new owner adopts the session into its own stores, and the old
 * owner, seeing a newer [LiveClaim], stops without ending the session.
 */
object LiveSessions {
    private const val TAG = "LiveSessions"

    /** A command waits this long for the owner before the mirror says it is not reachable. */
    private const val COMMAND_TIMEOUT_MILLIS = 6_000L
    /** Commands older than this are dropped by the owner: whatever they were for has moved on. */
    private const val COMMAND_MAX_AGE_MILLIS = 60_000L
    /** Presence at most this often. */
    private const val PRESENCE_INTERVAL_MILLIS = 2_000L
    /** The walked distance and track in [LiveHuntStats] refresh at most this often. */
    private const val STATS_REFRESH_MILLIS = 120_000L
    /** No presence for this long while the stream is open: the owner is offline. */
    const val OWNER_OFFLINE_MILLIS = 45_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    // -- Owner readout ---------------------------------------------------------------------

    /** What the tracking service shows for the hunt it runs here: the in-app map reads the same. */
    data class OwnerReadout(
        val latitude: Double?,
        val longitude: Double?,
        val accuracyMeters: Float?,
        val bearing: Float?,
        val targetId: String?,
        val distanceMeters: Float?,
        val inRange: Boolean,
        val waiting: Boolean,
        val endedNote: String?,
        val atMillis: Long = System.currentTimeMillis()
    )

    private val _ownerReadout = MutableStateFlow<OwnerReadout?>(null)
    val ownerReadout: StateFlow<OwnerReadout?> = _ownerReadout.asStateFlow()

    /** Called by the tracking service on every fix and readout change while it runs a hunt. */
    fun reportOwnerReadout(readout: OwnerReadout?) {
        _ownerReadout.value = readout
    }

    /** A hunt action for the tracking service running here, from this device or another. */
    data class HuntOrder(val action: LiveAction, val alertId: String? = null, val expectedTargetId: String? = null)

    private val _huntOrders = MutableSharedFlow<HuntOrder>(extraBufferCapacity = 16)

    /** Collected by the tracking service while it runs a hunt. */
    val huntOrders: SharedFlow<HuntOrder> = _huntOrders.asSharedFlow()

    // -- Views ------------------------------------------------------------------------------

    /**
     * The running hunt as every screen shows it, whether it runs here ([mirroredFrom] null) or
     * on another device of the group.
     */
    data class HuntView(
        val session: HuntSession,
        val mirroredFrom: LiveOwner?,
        val target: PokemonAlert?,
        val route: List<PokemonAlert>,
        val others: List<PokemonAlert>,
        val outside: List<PokemonAlert>,
        val undo: LiveUndo?,
        val endedNote: String?,
        val catches: List<HuntCatch>,
        val walkedMeters: Double,
        val pausedSinceMillis: Long?,
        val pausedTotalMillis: Long,
        val distanceMeters: Float?,
        val inRange: Boolean,
        val waiting: Boolean,
        /** Where the device running the hunt is (a mirror draws it; the owner has its own dot). */
        val ownerLatitude: Double?,
        val ownerLongitude: Double?,
        val ownerBearing: Float?,
        /** When this device last heard from the owner (its own clock); null before the first time. */
        val ownerHeardAtMillis: Long?
    ) {
        val isMirror: Boolean get() = mirroredFrom != null
        val paused: Boolean get() = session.paused
        val targetId: String? get() = session.targetUniqueId

        fun activeMillis(nowMillis: Long): Long {
            val paused = pausedTotalMillis + (pausedSinceMillis?.let { (nowMillis - it).coerceAtLeast(0) } ?: 0L)
            return (nowMillis - session.startedAtMillis - paused).coerceAtLeast(0)
        }

        /** The owner has gone quiet: offer to take over. */
        fun ownerOffline(nowMillis: Long, streamConnectedAtMillis: Long?): Boolean =
            isMirror && LiveRules.ownerOffline(ownerHeardAtMillis, streamConnectedAtMillis, nowMillis, OWNER_OFFLINE_MILLIS)
    }

    /** A catch route walked on another device. */
    data class CatchMirror(val owner: LiveOwner, val session: CatchSession, val position: LivePresence?, val heardAtMillis: Long?)

    private val _hunt = MutableStateFlow<HuntView?>(null)
    val hunt: StateFlow<HuntView?> = _hunt.asStateFlow()

    private val _catchMirror = MutableStateFlow<CatchMirror?>(null)
    val catchMirror: StateFlow<CatchMirror?> = _catchMirror.asStateFlow()

    private val _journeyMirror = MutableStateFlow<LiveJourney?>(null)
    val journeyMirror: StateFlow<LiveJourney?> = _journeyMirror.asStateFlow()

    private val _raidWatchMirror = MutableStateFlow<LiveRaidWatch?>(null)
    val raidWatchMirror: StateFlow<LiveRaidWatch?> = _raidWatchMirror.asStateFlow()

    private val _mirrorSummary = MutableStateFlow<LiveHuntSummary?>(null)

    /** A hunt that ended on another device, until this one has shown its summary. */
    val mirrorSummary: StateFlow<LiveHuntSummary?> = _mirrorSummary.asStateFlow()

    /** The action a mirror is waiting on the owner for, so its button can show it. */
    private val _pending = MutableStateFlow<LiveAction?>(null)
    val pending: StateFlow<LiveAction?> = _pending.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Things to tell the trainer: the owner did not answer, a take-over needs a permission. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // -- Start --------------------------------------------------------------------------------

    /** Started once from the Application. */
    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        val store = LiveStore.get(app)
        val self = DeviceSync.deviceId(app)
        val owner = LiveOwner(self, Build.MODEL.orEmpty())
        scope.launch { runCatching { publishHunt(app, store, owner) }.onFailure { Log.w(TAG, "Hunt publisher stopped", it) } }
        scope.launch { runCatching { publishCatch(app, store, owner) }.onFailure { Log.w(TAG, "Route publisher stopped", it) } }
        scope.launch { runCatching { publishJourney(app, store, owner) }.onFailure { Log.w(TAG, "Journey publisher stopped", it) } }
        scope.launch { runCatching { publishRaidWatch(app, store, owner) }.onFailure { Log.w(TAG, "Raid Watch publisher stopped", it) } }
        scope.launch { runCatching { publishSummary(app, store, owner) }.onFailure { Log.w(TAG, "Summary publisher stopped", it) } }
        scope.launch { runCatching { publishPresence(app) }.onFailure { Log.w(TAG, "Presence stopped", it) } }
        scope.launch { runCatching { processCommands(app, store, self) }.onFailure { Log.w(TAG, "Command processor stopped", it) } }
        scope.launch { runCatching { buildViews(app, store, self) }.onFailure { Log.w(TAG, "Views stopped", it) } }
        scope.launch { runCatching { holdStream(app) }.onFailure { Log.w(TAG, "Stream holder stopped", it) } }
    }

    private inline fun <reified T> decode(raw: String?): T? =
        raw?.let { runCatching { liveJson.decodeFromString<T>(it) }.getOrNull() }

    private inline fun <reified T> encode(value: T): String = liveJson.encodeToString(value)

    // -- Owner: hunt ------------------------------------------------------------------------

    private fun localClaims(app: Context) = app.getSharedPreferences("live_local", Context.MODE_PRIVATE)

    /** When this device last claimed the session started at [startedAtMillis]; its start when it began it. */
    private fun localClaimedAt(app: Context, kind: String, startedAtMillis: Long): Long {
        val raw = localClaims(app).getString(kind, null) ?: return startedAtMillis
        val (started, claimed) = raw.split(':').mapNotNull { it.toLongOrNull() }.takeIf { it.size == 2 } ?: return startedAtMillis
        return if (started == startedAtMillis) claimed else startedAtMillis
    }

    private fun rememberClaim(app: Context, kind: String, startedAtMillis: Long, claimedAtMillis: Long) {
        localClaims(app).edit { putString(kind, "$startedAtMillis:$claimedAtMillis") }
    }

    private data class HuntInputs(
        val session: HuntSession?,
        val stats: HuntStats,
        val plan: com.example.pokemonalertsv2.hunt.HuntPlan,
        val outside: List<PokemonAlert>,
        val target: PokemonAlert?,
        val caught: com.example.pokemonalertsv2.data.CaughtAlert?,
        val note: String?,
        val claim: LiveClaim?
    )

    private suspend fun publishHunt(app: Context, store: LiveStore, owner: LiveOwner) {
        val hunts = HuntRepository.getInstance(app)
        val arrival = ArrivalTrackingRepository.getInstance(app)
        val preferences = AlertPreferences(app.alertPreferencesDataStore)
        var lastStateKey: String? = null
        var lastStatsKey: String? = null
        var lastStatsAt = 0L
        val ticker = kotlinx.coroutines.flow.flow { while (true) { emit(System.currentTimeMillis()); delay(STATS_REFRESH_MILLIS) } }
        combine(
            combine(hunts.sessionFlow, hunts.statsFlow, hunts.plan, hunts.outside) { session, stats, plan, outside -> arrayOf<Any?>(session, stats, plan, outside) },
            combine(arrival.activeDestination, preferences.lastCaughtAlert, ownerReadout.map { it?.endedNote }.distinctUntilChanged()) { destination, caught, note -> Triple(destination, caught, note) },
            store.items.map { it[LiveKeys.HUNT_OWNER] }.distinctUntilChanged(),
            ticker
        ) { first, second, claimRaw, _ ->
            @Suppress("UNCHECKED_CAST")
            HuntInputs(
                session = first[0] as HuntSession?,
                stats = first[1] as HuntStats,
                plan = first[2] as com.example.pokemonalertsv2.hunt.HuntPlan,
                outside = first[3] as List<PokemonAlert>,
                target = second.first?.alert,
                caught = second.second,
                note = second.third,
                claim = decode<LiveClaim>(claimRaw)
            )
        }.collect { inputs ->
            val session = inputs.session
            val claim = inputs.claim
            if (session == null) {
                // Ended here: take this device's hunt off the other devices.
                if (claim?.owner?.deviceId == owner.deviceId) LiveKeys.HUNT_ITEMS.forEach(store::remove)
                lastStateKey = null
                lastStatsKey = null
                return@collect
            }
            val claimedAt = localClaimedAt(app, "hunt", session.startedAtMillis)
            when (LiveRules.claimVerdict(claim, owner.deviceId, session.startedAtMillis, claimedAt)) {
                LiveRules.ClaimVerdict.KEEP -> Unit
                LiveRules.ClaimVerdict.RELEASE -> {
                    // Taken over by another device: stop here without ending the hunt or keeping a summary.
                    Log.i(TAG, "Hunt taken over by ${claim?.owner?.deviceName}")
                    runCatching {
                        hunts.stop()
                        arrival.stopTracking()
                    }
                    return@collect
                }
                LiveRules.ClaimVerdict.REPLACE -> {
                    // Another device started a hunt of its own since: one hunt at a time, as on one device.
                    Log.i(TAG, "Hunt replaced by one started on ${claim?.owner?.deviceName}")
                    ArrivalTrackingService.stopEverything(app, fromApp = false)
                    return@collect
                }
            }
            if (claim == null || claim.owner.deviceId != owner.deviceId || claim.startedAtMillis != session.startedAtMillis) {
                store.put(LiveKeys.HUNT_OWNER, encode(LiveClaim(owner, session.startedAtMillis, claimedAt)))
            }
            store.put(LiveKeys.HUNT_SESSION, encode(LiveHuntSession(session)))

            val now = System.currentTimeMillis()
            val undo = inputs.caught?.takeIf { isUndoOfferLive(it, now) }?.let { LiveUndo(it.id, it.displayName, it.caughtAtMillis) }
            val route = inputs.plan.route.take(MAX_ROUTE)
            val others = inputs.plan.others.take(MAX_OTHERS)
            val outside = inputs.outside.take(MAX_OUTSIDE)
            // Only what a mirror would draw differently: the plan recomputes far more often.
            val stateKey = listOf(
                session.targetUniqueId, inputs.target?.uniqueId, session.paused, undo?.id, inputs.note,
                route.joinToString { it.uniqueId }, others.joinToString { it.uniqueId }, outside.joinToString { it.uniqueId }
            ).joinToString("|")
            if (stateKey != lastStateKey || LiveKeys.HUNT_STATE !in store.items.value) {
                lastStateKey = stateKey
                store.put(
                    LiveKeys.HUNT_STATE,
                    encode(
                        LiveHuntState(
                            target = inputs.target?.takeIf { it.uniqueId == session.targetUniqueId },
                            targetId = session.targetUniqueId,
                            paused = session.paused,
                            route = route,
                            others = others,
                            outside = outside,
                            undo = undo,
                            endedNote = inputs.note
                        )
                    )
                )
            }
            val stats = inputs.stats
            val statsKey = "${stats.catches.joinToString { it.id }}|${stats.pausedSinceMillis}|${stats.pausedTotalMillis}"
            if (statsKey != lastStatsKey || now - lastStatsAt >= STATS_REFRESH_MILLIS || LiveKeys.HUNT_STATS !in store.items.value) {
                lastStatsKey = statsKey
                lastStatsAt = now
                store.put(
                    LiveKeys.HUNT_STATS,
                    encode(LiveHuntStats(stats.catches, stats.pausedSinceMillis, stats.pausedTotalMillis, stats.distanceMeters, stats.track))
                )
            }
        }
    }

    private const val MAX_ROUTE = 40
    private const val MAX_OTHERS = 20
    private const val MAX_OUTSIDE = 15

    private suspend fun publishSummary(app: Context, store: LiveStore, owner: LiveOwner) {
        HuntRepository.getInstance(app).lastSummary.collect { summary ->
            if (summary == null) return@collect
            val current = decode<LiveHuntSummary>(store.items.value[LiveKeys.HUNT_SUMMARY])
            if (current?.summary?.startedAtMillis == summary.startedAtMillis && current.owner.deviceId == owner.deviceId) return@collect
            store.put(LiveKeys.HUNT_SUMMARY, encode(LiveHuntSummary(owner, summary.copy(raidWatchStillShowing = null))))
        }
    }

    // -- Owner: catch route, journey, Raid Watch ----------------------------------------------

    private suspend fun publishCatch(app: Context, store: LiveStore, owner: LiveOwner) {
        val controller = CatchRouteController.get(app)
        controller.ready()
        var lastKey: String? = null
        var lastAt = 0L
        combine(controller.session, store.items.map { it[LiveKeys.CATCH_OWNER] }.distinctUntilChanged()) { session, claimRaw ->
            session to decode<LiveClaim>(claimRaw)
        }.collect { (session, claim) ->
            if (session == null || session.finished) {
                if (claim?.owner?.deviceId == owner.deviceId) LiveKeys.CATCH_ITEMS.forEach(store::remove)
                lastKey = null
                return@collect
            }
            val claimedAt = localClaimedAt(app, "catch", session.startedAtMillis)
            when (LiveRules.claimVerdict(claim, owner.deviceId, session.startedAtMillis, claimedAt)) {
                LiveRules.ClaimVerdict.KEEP -> Unit
                LiveRules.ClaimVerdict.RELEASE -> {
                    Log.i(TAG, "Route taken over by ${claim?.owner?.deviceName}")
                    runCatching { controller.release() }
                    return@collect
                }
                LiveRules.ClaimVerdict.REPLACE -> {
                    Log.i(TAG, "Route replaced by one started on ${claim?.owner?.deviceName}")
                    runCatching { controller.stop() }
                    return@collect
                }
            }
            if (claim == null || claim.owner.deviceId != owner.deviceId || claim.startedAtMillis != session.startedAtMillis) {
                store.put(LiveKeys.CATCH_OWNER, encode(LiveClaim(owner, session.startedAtMillis, claimedAt)))
            }
            // Progress changes with every fix; the other devices get it as presence.
            val now = System.currentTimeMillis()
            val key = listOf(session.visits.size, session.paused, session.finished, session.replans, session.undoVisits != null,
                session.itinerary.settings.spacialRend, session.itinerary.path.size).joinToString("|")
            if (key != lastKey || now - lastAt >= STATS_REFRESH_MILLIS || LiveKeys.CATCH_SESSION !in store.items.value) {
                lastKey = key
                lastAt = now
                store.put(LiveKeys.CATCH_SESSION, encode(LiveCatchSession(session)))
            }
        }
    }

    private suspend fun publishJourney(app: Context, store: LiveStore, owner: LiveOwner) {
        val hunts = HuntRepository.getInstance(app)
        combine(ArrivalTrackingRepository.getInstance(app).activeDestination, hunts.activeHunt) { destination, hunt ->
            destination.takeIf { hunt == null }
        }.distinctUntilChanged().collect { destination ->
            val current = decode<LiveJourney>(store.items.value[LiveKeys.JOURNEY])
            if (destination == null) {
                if (current?.owner?.deviceId == owner.deviceId) store.remove(LiveKeys.JOURNEY)
            } else {
                store.put(LiveKeys.JOURNEY, encode(LiveJourney(owner, destination.alert, destination.startedAtMillis)))
            }
        }
    }

    private suspend fun publishRaidWatch(app: Context, store: LiveStore, owner: LiveOwner) {
        RaidWatchStore(app).watched.distinctUntilChanged().collect { watched ->
            val current = decode<LiveRaidWatch>(store.items.value[LiveKeys.RAID_WATCH])
            if (watched == null) {
                if (current?.owner?.deviceId == owner.deviceId) store.remove(LiveKeys.RAID_WATCH)
            } else {
                store.put(LiveKeys.RAID_WATCH, encode(LiveRaidWatch(owner, watched.alert, watched.startedAtMillis, watched.endMillis)))
            }
        }
    }

    // -- Owner: presence and the stream -------------------------------------------------------

    /**
     * Every [PRESENCE_INTERVAL_MILLIS] at most while another device listens, else every
     * [PRESENCE_QUIET_INTERVAL_MILLIS]; and at least every [PRESENCE_HEARTBEAT_MILLIS] even
     * standing still, so a mirror never takes a paused owner for an offline one.
     */
    private suspend fun publishPresence(app: Context) {
        val hunts = HuntRepository.getInstance(app)
        val controller = CatchRouteController.get(app)
        var lastSent = 0L
        var interval = PRESENCE_INTERVAL_MILLIS
        val heartbeat = kotlinx.coroutines.flow.flow { while (true) { emit(Unit); delay(PRESENCE_HEARTBEAT_MILLIS) } }
        combine(
            combine(ownerReadout, hunts.activeHunt, hunts.statsFlow, controller.session, controller.location) { readout, hunt, stats, route, point ->
                arrayOf<Any?>(readout, hunt, stats, route, point)
            },
            heartbeat
        ) { values, _ ->
            @Suppress("UNCHECKED_CAST")
            val readout = values[0] as OwnerReadout?
            val hunt = values[1] as HuntSession?
            val stats = values[2] as HuntStats
            val route = values[3] as CatchSession?
            val point = values[4] as com.example.pokemonalertsv2.catchroutes.CatchPoint?
            when {
                hunt != null && readout != null -> LivePresence(
                    latitude = readout.latitude, longitude = readout.longitude, accuracyMeters = readout.accuracyMeters,
                    bearing = readout.bearing, targetId = readout.targetId, distanceMeters = readout.distanceMeters,
                    inRange = readout.inRange, waiting = readout.waiting, walkedMeters = stats.distanceMeters,
                    atMillis = readout.atMillis
                )
                route != null && !route.finished && point != null -> LivePresence(
                    latitude = point.latitude, longitude = point.longitude, walkedMeters = route.walkedMeters, atMillis = System.currentTimeMillis()
                )
                else -> null
            }
        }.collectLatest { presence ->
            presence ?: return@collectLatest
            val wait = interval - (System.currentTimeMillis() - lastSent)
            if (wait > 0) delay(wait)
            lastSent = System.currentTimeMillis()
            val listeners = DeviceSync.postPresence(app, encode(presence.copy(atMillis = lastSent)).toByteArray())
            // This device's own stream is one of them: nobody else listening, slow down.
            interval = if (listeners != null && listeners <= 1) PRESENCE_QUIET_INTERVAL_MILLIS else PRESENCE_INTERVAL_MILLIS
        }
    }

    private const val PRESENCE_QUIET_INTERVAL_MILLIS = 15_000L
    private const val PRESENCE_HEARTBEAT_MILLIS = 15_000L

    /** The owner of a running hunt or route keeps the stream open to hear commands with the app in the background. */
    private suspend fun holdStream(app: Context) {
        combine(HuntRepository.getInstance(app).activeHunt, CatchRouteController.get(app).session) { hunt, route ->
            (hunt != null) to (route != null && !route.finished)
        }.distinctUntilChanged().collect { (hunting, walking) ->
            if (hunting) DeviceSync.holdLive(app, DeviceSync.HOLDER_HUNT) else DeviceSync.releaseLive(app, DeviceSync.HOLDER_HUNT)
            if (walking) DeviceSync.holdLive(app, DeviceSync.HOLDER_CATCH_ROUTE) else DeviceSync.releaseLive(app, DeviceSync.HOLDER_CATCH_ROUTE)
        }
    }

    // -- Owner: commands ----------------------------------------------------------------------

    private val handled = LinkedHashSet<String>()

    private suspend fun processCommands(app: Context, store: LiveStore, self: String) {
        store.items.collect { items ->
            items.filterKeys { it.startsWith(LiveKeys.COMMAND_PREFIX) }.forEach { (key, raw) ->
                val command = decode<LiveCommand>(raw) ?: return@forEach
                if (command.to != self) return@forEach
                store.remove(key)
                if (!handled.add(command.id)) return@forEach
                while (handled.size > 100) handled.remove(handled.first())
                runCatching { execute(app, command, self) }.onFailure { Log.w(TAG, "Command ${command.action} failed", it) }
            }
        }
    }

    private suspend fun execute(app: Context, command: LiveCommand, self: String) {
        val action = runCatching { LiveAction.valueOf(command.action) }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        when (command.session) {
            LiveCommand.SESSION_HUNT -> {
                val session = HuntRepository.getInstance(app).currentSession() ?: return
                if (!LiveRules.accepts(command, self, session.startedAtMillis, session.targetUniqueId, now, COMMAND_MAX_AGE_MILLIS)) return
                if (action == LiveAction.UPDATE_TARGETS) {
                    val targets = command.targets ?: return
                    com.example.pokemonalertsv2.hunt.updateHunt(app, targets.name, targets.definition, targets.savedHuntId, targets.area)
                    return
                }
                runHuntHere(app, HuntOrder(action, command.alertId, command.expectedTargetId), fromApp = false)
            }
            LiveCommand.SESSION_CATCH -> {
                val controller = CatchRouteController.get(app)
                val session = controller.session.value ?: return
                if (!LiveRules.accepts(command, self, session.startedAtMillis, null, now, COMMAND_MAX_AGE_MILLIS)) return
                runCatchHere(controller, session, action)
            }
            LiveCommand.SESSION_JOURNEY -> {
                val journey = ArrivalTrackingRepository.getInstance(app).currentDestination() ?: return
                if (action == LiveAction.STOP && LiveRules.accepts(command, self, journey.startedAtMillis, null, now, COMMAND_MAX_AGE_MILLIS)) {
                    ArrivalTrackingRepository.getInstance(app).stopTracking()
                }
            }
            LiveCommand.SESSION_RAID_WATCH -> {
                val watched = RaidWatchStore(app).current() ?: return
                if (action == LiveAction.STOP && LiveRules.accepts(command, self, watched.startedAtMillis, null, now, COMMAND_MAX_AGE_MILLIS)) {
                    RaidWatchController.stop(app)
                }
            }
        }
    }

    private suspend fun runHuntHere(app: Context, order: HuntOrder, fromApp: Boolean) {
        when (order.action) {
            // Neither needs the service: they work on the stores it watches.
            LiveAction.STOP -> ArrivalTrackingService.stopEverything(app, fromApp = fromApp)
            LiveAction.UNDO -> undoLastCatch(app)
            else -> _huntOrders.emit(order)
        }
    }

    private fun runCatchHere(controller: CatchRouteController, session: CatchSession, action: LiveAction) {
        when (action) {
            LiveAction.PAUSE -> if (!session.paused) controller.pause()
            LiveAction.RESUME -> if (session.paused) controller.pause()
            LiveAction.SKIP -> controller.skip()
            LiveAction.UNDO -> controller.undo()
            LiveAction.RECALCULATE -> controller.recalculate()
            LiveAction.SPACIAL_REND_ON -> controller.setRadius(true)
            LiveAction.SPACIAL_REND_OFF -> controller.setRadius(false)
            LiveAction.STOP -> scope.launch { controller.stop() }
            else -> Unit
        }
    }

    // -- Sending commands ---------------------------------------------------------------------

    /** A hunt action from any screen: carried out here when the hunt runs here, else sent to its owner. */
    fun hunt(context: Context, action: LiveAction, alertId: String? = null) {
        val app = context.applicationContext
        scope.launch {
            val view = hunt.value
            if (view == null || !view.isMirror) {
                runHuntHere(app, HuntOrder(action, alertId, view?.targetId), fromApp = true)
                return@launch
            }
            if (action == LiveAction.STOP && view.ownerOffline(System.currentTimeMillis(), DeviceSync.streamConnectedAt.value)) {
                // The owner is gone: end the hunt for everyone from here.
                val store = LiveStore.get(app)
                LiveKeys.HUNT_ITEMS.forEach(store::remove)
                return@launch
            }
            send(app, view.mirroredFrom!!, LiveCommand.SESSION_HUNT, action, alertId, view.session.startedAtMillis, view.targetId)
        }
    }

    /** "Edit targets" for a hunt running on another device: the owner applies it as its own edit. */
    fun editMirroredHunt(context: Context, targets: LiveTargets) {
        val app = context.applicationContext
        scope.launch {
            val view = hunt.value?.takeIf { it.isMirror } ?: return@launch
            send(app, view.mirroredFrom!!, LiveCommand.SESSION_HUNT, LiveAction.UPDATE_TARGETS, null, view.session.startedAtMillis, null, targets)
        }
    }

    /** A catch-route action for a route walked on another device. */
    fun catchRoute(context: Context, action: LiveAction) {
        val app = context.applicationContext
        scope.launch {
            val controller = CatchRouteController.get(app)
            controller.session.value?.let { session ->
                runCatchHere(controller, session, action)
                return@launch
            }
            val mirror = catchMirror.value ?: return@launch
            send(app, mirror.owner, LiveCommand.SESSION_CATCH, action, null, mirror.session.startedAtMillis, null)
        }
    }

    fun stopJourney(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val mirror = journeyMirror.value ?: return@launch
            send(app, mirror.owner, LiveCommand.SESSION_JOURNEY, LiveAction.STOP, null, mirror.startedAtMillis, null)
        }
    }

    fun stopRaidWatch(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val mirror = raidWatchMirror.value ?: return@launch
            send(app, mirror.owner, LiveCommand.SESSION_RAID_WATCH, LiveAction.STOP, null, mirror.startedAtMillis, null)
        }
    }

    private suspend fun send(
        app: Context,
        to: LiveOwner,
        session: String,
        action: LiveAction,
        alertId: String?,
        startedAtMillis: Long,
        expectedTargetId: String?,
        targets: LiveTargets? = null
    ) {
        val store = LiveStore.get(app)
        val id = UUID.randomUUID().toString()
        val key = LiveKeys.COMMAND_PREFIX + id
        store.put(
            key,
            encode(LiveCommand(id, to.deviceId, DeviceSync.deviceId(app), session, action.name, alertId, startedAtMillis, expectedTargetId, targets, System.currentTimeMillis()))
        )
        _pending.value = action
        // Done when the owner deletes the command; not done in time, the owner is not listening.
        val answered = runCatching {
            kotlinx.coroutines.withTimeout(COMMAND_TIMEOUT_MILLIS) { store.items.first { key !in it } }
        }.isSuccess
        if (_pending.value == action) _pending.value = null
        if (!answered) {
            store.remove(key)
            _messages.tryEmit(AppText.get(R.string.live_owner_not_reachable, to.deviceName.ifBlank { "?" }))
        }
    }

    // -- Take over ----------------------------------------------------------------------------

    /** Why a take-over did not happen. */
    enum class TakeOverResult { DONE, NOTHING_TO_TAKE, NEEDS_PERMISSION }

    private fun canRunHere(app: Context): Boolean {
        val location = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return location && notifications
    }

    /** Continues the hunt running on another device here: GPS, planner and notification move to this device. */
    suspend fun takeOverHunt(context: Context): TakeOverResult {
        val app = context.applicationContext
        val view = hunt.value?.takeIf { it.isMirror } ?: return TakeOverResult.NOTHING_TO_TAKE
        if (!canRunHere(app)) return TakeOverResult.NEEDS_PERMISSION
        val store = LiveStore.get(app)
        val items = store.items.value
        val stats = decode<LiveHuntStats>(items[LiveKeys.HUNT_STATS]) ?: LiveHuntStats()
        val now = LiveRules.takeOverClaimedAt(System.currentTimeMillis(), decode<LiveClaim>(items[LiveKeys.HUNT_OWNER]))
        val session = view.session
        rememberClaim(app, "hunt", session.startedAtMillis, now)
        store.put(LiveKeys.HUNT_OWNER, encode(LiveClaim(LiveOwner(DeviceSync.deviceId(app), Build.MODEL.orEmpty()), session.startedAtMillis, now)))
        val hunts = HuntRepository.getInstance(app)
        hunts.adopt(
            session.copy(targetUniqueId = null),
            HuntStats(
                catches = stats.catches,
                distanceMeters = maxOf(stats.distanceMeters, view.walkedMeters),
                pausedSinceMillis = stats.pausedSinceMillis,
                pausedTotalMillis = stats.pausedTotalMillis,
                track = stats.track
            )
        )
        // Walk on to the target the other device was walking to, if it is still worth it.
        view.target?.takeIf { !session.paused }?.let { target ->
            runCatching {
                ArrivalTrackingRepository.getInstance(app).startTracking(target)
                hunts.setTarget(target.uniqueId)
            }
        }
        ArrivalTrackingService.startHunt(app)
        return TakeOverResult.DONE
    }

    /** Continues the catch route walked on another device here. */
    suspend fun takeOverCatchRoute(context: Context): TakeOverResult {
        val app = context.applicationContext
        val mirror = catchMirror.value ?: return TakeOverResult.NOTHING_TO_TAKE
        if (!canRunHere(app)) return TakeOverResult.NEEDS_PERMISSION
        val now = LiveRules.takeOverClaimedAt(System.currentTimeMillis(), decode<LiveClaim>(LiveStore.get(app).items.value[LiveKeys.CATCH_OWNER]))
        rememberClaim(app, "catch", mirror.session.startedAtMillis, now)
        LiveStore.get(app).put(
            LiveKeys.CATCH_OWNER,
            encode(LiveClaim(LiveOwner(DeviceSync.deviceId(app), Build.MODEL.orEmpty()), mirror.session.startedAtMillis, now))
        )
        CatchRouteController.get(app).adopt(mirror.session)
        return TakeOverResult.DONE
    }

    // -- Views --------------------------------------------------------------------------------

    private suspend fun buildViews(app: Context, store: LiveStore, self: String) {
        val hunts = HuntRepository.getInstance(app)
        val arrival = ArrivalTrackingRepository.getInstance(app)
        val preferences = AlertPreferences(app.alertPreferencesDataStore)
        val local = combine(
            combine(hunts.sessionFlow, hunts.statsFlow, hunts.plan, hunts.outside) { session, stats, plan, outside -> arrayOf<Any?>(session, stats, plan, outside) },
            arrival.activeDestination,
            preferences.lastCaughtAlert,
            ownerReadout
        ) { first, destination, caught, readout ->
            @Suppress("UNCHECKED_CAST")
            val session = first[0] as HuntSession? ?: return@combine null
            val stats = first[1] as HuntStats
            val plan = first[2] as com.example.pokemonalertsv2.hunt.HuntPlan
            HuntView(
                session = session,
                mirroredFrom = null,
                target = destination?.alert?.takeIf { it.uniqueId == session.targetUniqueId },
                route = plan.route,
                others = plan.others,
                outside = first[3] as List<PokemonAlert>,
                undo = caught?.let { LiveUndo(it.id, it.displayName, it.caughtAtMillis) },
                endedNote = readout?.endedNote,
                catches = stats.catches,
                walkedMeters = stats.distanceMeters,
                pausedSinceMillis = stats.pausedSinceMillis,
                pausedTotalMillis = stats.pausedTotalMillis,
                distanceMeters = readout?.distanceMeters?.takeIf { readout.targetId == session.targetUniqueId },
                inRange = readout?.inRange == true && readout.targetId == session.targetUniqueId,
                waiting = readout?.waiting == true,
                ownerLatitude = null,
                ownerLongitude = null,
                ownerBearing = null,
                ownerHeardAtMillis = null
            )
        }
        val seenSummaries = app.getSharedPreferences("live_local", Context.MODE_PRIVATE)
        combine(local, store.items, DeviceSync.presence, CatchRouteController.get(app).session) { localHunt, items, presence, localRoute ->
            // Mirror state is read only from the live store, never from the native stores.
            val huntClaim = decode<LiveClaim>(items[LiveKeys.HUNT_OWNER])
            val mirrorHunt = if (localHunt == null && huntClaim != null && huntClaim.owner.deviceId != self) {
                mirrorHuntView(items, huntClaim, presence[huntClaim.owner.deviceId])
            } else {
                null
            }
            _hunt.value = localHunt ?: mirrorHunt

            val catchClaim = decode<LiveClaim>(items[LiveKeys.CATCH_OWNER])
            _catchMirror.value = if (localRoute == null && catchClaim != null && catchClaim.owner.deviceId != self) {
                decode<LiveCatchSession>(items[LiveKeys.CATCH_SESSION])?.let { live ->
                    val record = presence[catchClaim.owner.deviceId]
                    CatchMirror(catchClaim.owner, live.session, record?.let { decode<LivePresence>(it.payload.decodeToString()) }, record?.receivedAtMillis)
                }
            } else {
                null
            }
            _journeyMirror.value = decode<LiveJourney>(items[LiveKeys.JOURNEY])?.takeIf { it.owner.deviceId != self }
            _raidWatchMirror.value = decode<LiveRaidWatch>(items[LiveKeys.RAID_WATCH])
                ?.takeIf { it.owner.deviceId != self && it.endAtMillis > System.currentTimeMillis() }
            _mirrorSummary.value = decode<LiveHuntSummary>(items[LiveKeys.HUNT_SUMMARY])?.takeIf {
                it.owner.deviceId != self && !seenSummaries.getBoolean("summary_${it.summary.startedAtMillis}", false)
            }
            // A command the owner has answered: the mirror's button stops waiting.
            if (_pending.value != null && items.keys.none { it.startsWith(LiveKeys.COMMAND_PREFIX) }) _pending.value = null
        }.collect { }
    }

    private fun mirrorHuntView(items: Map<String, String>, claim: LiveClaim, presence: DeviceSync.PresenceRecord?): HuntView? {
        val session = decode<LiveHuntSession>(items[LiveKeys.HUNT_SESSION])?.session ?: return null
        if (session.startedAtMillis != claim.startedAtMillis) return null
        val state = decode<LiveHuntState>(items[LiveKeys.HUNT_STATE]) ?: LiveHuntState()
        val stats = decode<LiveHuntStats>(items[LiveKeys.HUNT_STATS]) ?: LiveHuntStats()
        val position = presence?.let { decode<LivePresence>(it.payload.decodeToString()) }
        val targetMatches = position?.targetId == session.targetUniqueId
        return HuntView(
            session = session,
            mirroredFrom = claim.owner,
            target = state.target,
            route = state.route,
            others = state.others,
            outside = state.outside,
            undo = state.undo,
            endedNote = state.endedNote,
            catches = stats.catches,
            walkedMeters = position?.walkedMeters ?: stats.distanceMeters,
            pausedSinceMillis = stats.pausedSinceMillis,
            pausedTotalMillis = stats.pausedTotalMillis,
            distanceMeters = position?.distanceMeters?.takeIf { targetMatches },
            inRange = position?.inRange == true && targetMatches,
            waiting = position?.waiting == true,
            ownerLatitude = position?.latitude,
            ownerLongitude = position?.longitude,
            ownerBearing = position?.bearing,
            ownerHeardAtMillis = presence?.receivedAtMillis
        )
    }

    /** The summary of a hunt that ended on another device has been shown here. */
    fun markMirrorSummarySeen(context: Context, summary: HuntSummary) {
        context.applicationContext.getSharedPreferences("live_local", Context.MODE_PRIVATE)
            .edit { putBoolean("summary_${summary.startedAtMillis}", true) }
        _mirrorSummary.value = null
    }
}
