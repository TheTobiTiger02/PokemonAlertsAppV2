package com.example.pokemonalertsv2.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles notification action buttons (Dismiss).
 * Triggered when the user taps "Dismiss" on a notification.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DISMISS -> {
                val alertId = intent.getStringExtra(EXTRA_ALERT_UNIQUE_ID) ?: return
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

                // Cancel the notification
                if (notificationId != -1) {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                }

                // Persist the dismissal
                val prefs = AlertPreferences(context.alertPreferencesDataStore)
                CoroutineScope(Dispatchers.IO).launch {
                    prefs.addDismissedAlert(alertId)
                }
            }
        }
    }

    companion object {
        const val ACTION_DISMISS = "com.example.pokemonalertsv2.ACTION_DISMISS_ALERT"
        const val EXTRA_ALERT_UNIQUE_ID = "extra_alert_unique_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
