package com.example.pokemonalertsv2.wear

import android.content.Context
import android.util.Log
import com.example.pokemonalertsv2.catchroutes.CatchRouteController
import com.example.pokemonalertsv2.catchroutes.catchDistance
import com.example.pokemonalertsv2.hunt.HuntRepository
import com.example.pokemonalertsv2.tracking.ArrivalTrackingRepository
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToInt

/**
 * Keeps a connected watch up to date with the running hunt or catch route.
 *
 * One data item, rewritten when what the watch shows changes (not on every GPS fix: distance is
 * rounded to 10 m and the writes are debounced), so the Bluetooth link stays quiet. Without a
 * watch, or without Google Play services' Wearable API, the writes fail locally and are ignored.
 */
object WearBridge {
    private const val TAG = "WearBridge"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var started = false

    @OptIn(FlowPreview::class)
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        scope.launch {
            sessionStates(app)
                .distinctUntilChanged()
                .debounce(1_500)
                .collect { state -> publish(app, state) }
        }
    }

    private fun sessionStates(context: Context): Flow<WearSessionState> {
        val hunts = HuntRepository.getInstance(context)
        val destinations = ArrivalTrackingRepository.getInstance(context).destinationFlow.onStart { emit(null) }
        val routes = CatchRouteController.get(context)
        val hunt = combine(hunts.sessionFlow, hunts.statsFlow, destinations) { session, stats, destination ->
            if (session == null) null else {
                val now = System.currentTimeMillis()
                val pausedNow = stats.pausedSinceMillis?.let { now - it } ?: 0L
                WearSessionState(
                    kind = WearSessionState.KIND_HUNT,
                    name = session.name,
                    startedAtMillis = session.startedAtMillis,
                    snapshotAtMillis = now,
                    activeMillis = (now - session.startedAtMillis - stats.pausedTotalMillis - pausedNow).coerceAtLeast(0L),
                    paused = session.paused,
                    caught = stats.catches.size,
                    distanceMeters = roundTen(stats.distanceMeters),
                    target = destination?.alert?.let { it.pokemon ?: it.name }?.takeIf { !session.paused },
                )
            }
        }
        val route = combine(routes.session, routes.location) { session, location ->
            if (session == null || session.finished) null else {
                val now = System.currentTimeMillis()
                val next = session.nextStopAt(now)
                WearSessionState(
                    kind = WearSessionState.KIND_ROUTE,
                    name = (session.originalSettings ?: session.itinerary.settings).name,
                    startedAtMillis = session.startedAtMillis,
                    snapshotAtMillis = now,
                    activeMillis = session.activeMillis(now),
                    paused = session.paused,
                    caught = session.caught,
                    distanceMeters = roundTen(session.walkedMeters),
                    target = if (next != null && location != null) "Next stop ${roundTen(catchDistance(location, next)).roundToInt()} m" else null,
                    remaining = session.ahead(now).size,
                )
            }
        }
        return combine(hunt, route) { h, r -> r ?: h ?: WearSessionState() }
            // The clock fields change on every emission; compare what the watch actually shows.
            .distinctUntilChanged { a, b -> a.copy(snapshotAtMillis = 0, activeMillis = 0) == b.copy(snapshotAtMillis = 0, activeMillis = 0) }
    }

    private fun roundTen(meters: Double): Double = (meters / 10.0).roundToInt() * 10.0

    private suspend fun publish(context: Context, state: WearSessionState) {
        runCatching {
            val request = PutDataMapRequest.create(WearProtocol.PATH_SESSION).apply {
                dataMap.putString(WearProtocol.KEY_STATE, WearProtocol.encodeState(state))
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
        }.onFailure { Log.d(TAG, "No watch to update: ${it.message}") }
    }

    /** Whether a paired watch has the companion app installed, for the settings screen. */
    suspend fun watchName(context: Context): String? = runCatching {
        Wearable.getCapabilityClient(context)
            .getCapability(WearProtocol.CAPABILITY_WATCH, com.google.android.gms.wearable.CapabilityClient.FILTER_REACHABLE)
            .await().nodes.firstOrNull()?.displayName
    }.getOrNull()
}
