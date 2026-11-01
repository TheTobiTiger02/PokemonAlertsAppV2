package com.example.pokemonalertsv2.wear

import android.content.Intent
import android.util.Log
import com.example.pokemonalertsv2.catchroutes.CatchRouteController
import com.example.pokemonalertsv2.hunt.HuntRepository
import com.example.pokemonalertsv2.tracking.ArrivalTrackingService
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Receives the watch app's buttons and its heart-rate/step readings. Commands go to whichever
 * session runs: a catch route if one is active, otherwise the hunt. The routing is the same as
 * the notification's buttons, so a press on the watch behaves like one on the phone.
 */
class WearListenerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val nodeNames = HashMap<String, String>()

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearProtocol.PATH_COMMAND -> WearProtocol.Command.fromWire(event.data.decodeToString())?.let(::handle)
            WearProtocol.PATH_SAMPLE -> WearProtocol.decodeSample(event.data)?.let { sample ->
                scope.launch { record(sample, deviceName(event.sourceNodeId)) }
            }
        }
    }

    private fun handle(command: WearProtocol.Command) {
        scope.launch {
            val routes = CatchRouteController.get(applicationContext)
            routes.ready()
            val route = routes.session.value?.takeIf { !it.finished }
            if (route != null) {
                when (command) {
                    WearProtocol.Command.PAUSE -> if (!route.paused) routes.pause()
                    WearProtocol.Command.RESUME -> if (route.paused) routes.pause()
                    WearProtocol.Command.STOP -> routes.stop()
                    WearProtocol.Command.SKIP -> routes.skip()
                    WearProtocol.Command.CAUGHT -> Unit
                }
                return@launch
            }
            if (!HuntRepository.getInstance(applicationContext).isHunting()) return@launch
            val action = when (command) {
                WearProtocol.Command.PAUSE -> ArrivalTrackingService.ACTION_PAUSE_HUNT
                WearProtocol.Command.RESUME -> ArrivalTrackingService.ACTION_RESUME_HUNT
                WearProtocol.Command.STOP -> ArrivalTrackingService.ACTION_STOP
                WearProtocol.Command.CAUGHT -> ArrivalTrackingService.ACTION_GOT_IT
                WearProtocol.Command.SKIP -> return@launch
            }
            // The tracking service is already in the foreground while a hunt runs, so a plain
            // start just delivers the action to it.
            runCatching { startService(Intent(applicationContext, ArrivalTrackingService::class.java).setAction(action)) }
                .onFailure { Log.w(TAG, "Could not pass the watch's $command to the hunt", it) }
        }
    }

    private suspend fun record(sample: WatchSample, deviceName: String?) {
        val routes = CatchRouteController.get(applicationContext)
        routes.ready()
        if (routes.session.value?.finished == false) {
            routes.onWatchSample(sample, deviceName)
        } else {
            HuntRepository.getInstance(applicationContext).recordWatchSample(sample, deviceName)
        }
    }

    private suspend fun deviceName(nodeId: String): String? = nodeNames[nodeId] ?: runCatching {
        Wearable.getNodeClient(applicationContext).connectedNodes.await().firstOrNull { it.id == nodeId }?.displayName
    }.getOrNull()?.also { nodeNames[nodeId] = it }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "WearListener"
    }
}
