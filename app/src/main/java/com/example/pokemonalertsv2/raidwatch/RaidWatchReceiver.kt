package com.example.pokemonalertsv2.raidwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.pokemonalertsv2.hunt.HuntRepository
import com.example.pokemonalertsv2.tracking.ArrivalTrackingNotifications
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
                    ACTION_STOP -> {
                        RaidWatchController.stop(appContext)
                        if (intent.getBooleanExtra(EXTRA_FROM_HUNT_SUMMARY, false)) {
                            HuntRepository.getInstance(appContext).forgetSummaryRaid()?.let {
                                ArrivalTrackingNotifications.postHuntSummary(appContext, it)
                            }
                        }
                    }
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
        const val EXTRA_FROM_HUNT_SUMMARY = "com.example.pokemonalertsv2.extra.FROM_HUNT_SUMMARY"
        const val ACTION_TICK = "com.example.pokemonalertsv2.action.RAID_WATCH_TICK"
        const val ACTION_STOP = "com.example.pokemonalertsv2.action.RAID_WATCH_STOP"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
