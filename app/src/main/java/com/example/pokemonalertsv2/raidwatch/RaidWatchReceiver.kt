package com.example.pokemonalertsv2.raidwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Drives the raid Live Update from outside the app process: the tick alarm and the
 * notification's own Dismiss action both land here.
 */
class RaidWatchReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        // goAsync keeps the receiver alive across the suspend work; without it the process
        // can be torn down mid-refresh and the notification is left stale.
        val pendingResult = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_TICK -> RaidWatchController.refresh(appContext)
                    ACTION_STOP -> RaidWatchController.stop(appContext)
                    else -> Unit
                }
            } catch (throwable: Throwable) {
                Log.w(TAG, "Raid watch broadcast failed: ${intent.action}", throwable)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "RaidWatchReceiver"
        const val ACTION_TICK = "com.example.pokemonalertsv2.action.RAID_WATCH_TICK"
        const val ACTION_STOP = "com.example.pokemonalertsv2.action.RAID_WATCH_STOP"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
