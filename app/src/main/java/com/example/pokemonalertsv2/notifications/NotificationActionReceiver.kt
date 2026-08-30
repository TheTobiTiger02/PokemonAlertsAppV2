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
import com.example.pokemonalertsv2.widget.AlertsWidgetProvider

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
                    AlertsWidgetProvider.requestUpdate(context)
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
            ACTION_DISMISS_GROUP -> {
                // The summary carries the ids of everything it stands for: NotificationManager
                // has no "cancel this group" call, and cancelling the summary alone would
                // leave the children behind.
                val alertIds = intent.getStringArrayExtra(EXTRA_ALERT_UNIQUE_IDS) ?: return
                val summaryId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                val manager = NotificationManagerCompat.from(context)
                alertIds.forEach { manager.cancel(it.hashCode()) }
                if (summaryId != -1) manager.cancel(summaryId)

                val prefs = AlertPreferences(context.alertPreferencesDataStore)
                CoroutineScope(Dispatchers.IO).launch {
                    alertIds.forEach { prefs.addDismissedAlert(it) }
                    AlertsWidgetProvider.requestUpdate(context)
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
        const val ACTION_DISMISS_GROUP = "com.example.pokemonalertsv2.ACTION_DISMISS_ALERT_GROUP"
        
        const val EXTRA_ALERT_UNIQUE_ID = "extra_alert_unique_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_ALERT_JSON = "extra_alert_json"
        const val EXTRA_ALERT_UNIQUE_IDS = "extra_alert_unique_ids"
    }
}
