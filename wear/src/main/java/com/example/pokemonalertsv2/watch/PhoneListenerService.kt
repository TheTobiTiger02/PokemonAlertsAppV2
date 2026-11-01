package com.example.pokemonalertsv2.watch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

/**
 * Hears the phone start, change and end a session. When one starts, a notification offers to
 * track it on the watch (Android does not let a background service start the sensors itself);
 * when it ends, the workout is stopped.
 */
class PhoneListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val before = SessionStore.state.value
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) SessionStore.update(event.dataItem)
            else if (event.type == DataEvent.TYPE_DELETED && event.dataItem.uri.path == WearProtocol.PATH_SESSION) SessionStore.clear()
        }
        val after = SessionStore.state.value
        when {
            !before.active && after.active && !WorkoutService.running -> offerTracking(after)
            !after.active -> {
                cancelOffer()
                if (WorkoutService.running) startService(Intent(this, WorkoutService::class.java).setAction(WorkoutService.ACTION_STOP))
            }
        }
    }

    private fun offerTracking(state: WearSessionState) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Sessions from your phone", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_AUTOSTART, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val what = if (state.isHunt) "Hunt" else "Catch route"
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_poke_notification)
            .setContentTitle("$what started")
            .setContentText("Tap to track heart rate and steps")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(OFFER_ID, notification) }
    }

    private fun cancelOffer() {
        getSystemService(NotificationManager::class.java).cancel(OFFER_ID)
    }

    companion object {
        private const val CHANNEL = "sessions"
        private const val OFFER_ID = 7
    }
}
