package com.example.pokemonalertsv2.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles notification action buttons.
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
            ACTION_SNOOZE -> {
                val alertJson = intent.getStringExtra(EXTRA_ALERT_JSON) ?: return
                val alert = AlertSnoozeScheduler.decodeAlert(alertJson) ?: return
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

                // Cancel current notification
                if (notificationId != -1) {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                }

                // Schedule snooze alarm
                val prefs = AlertPreferences(context.alertPreferencesDataStore)
                CoroutineScope(Dispatchers.IO).launch {
                    val durationMinutes = prefs.snoozeDuration.first()
                    AlertSnoozeScheduler.schedule(context, alert, durationMinutes)
                }
            }
            ACTION_TRIGGER_SNOOZE -> {
                val alertJson = intent.getStringExtra(EXTRA_ALERT_JSON) ?: return
                val alert = AlertSnoozeScheduler.decodeAlert(alertJson) ?: return
                if (!AlertSnoozeScheduler.isAlertActive(alert)) return
                
                CoroutineScope(Dispatchers.IO).launch {
                    AlertNotifier.notifyAlerts(context, listOf(alert))
                }
            }
        }
    }

    companion object {
        const val ACTION_DISMISS = "com.example.pokemonalertsv2.ACTION_DISMISS_ALERT"
        const val ACTION_SNOOZE = "com.example.pokemonalertsv2.ACTION_SNOOZE_ALERT"
        const val ACTION_TRIGGER_SNOOZE = "com.example.pokemonalertsv2.ACTION_TRIGGER_SNOOZE_ALERT"
        
        const val EXTRA_ALERT_UNIQUE_ID = "extra_alert_unique_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_ALERT_JSON = "extra_alert_json"
    }
}
