package com.example.pokemonalertsv2.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.example.pokemonalertsv2.widget.AlertsWidgetProvider

/**
 * Handles notification action buttons.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        // goAsync keeps the receiver alive across the suspend work; without it the
        // process can be torn down before the dismissal/snooze write lands and the
        // alert resurfaces on the next sync.
        val pendingResult = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_DISMISS -> handleDismiss(appContext, intent)
                    ACTION_SNOOZE -> handleSnooze(appContext, intent)
                    ACTION_DISMISS_GROUP -> handleDismissGroup(appContext, intent)
                    ACTION_TRIGGER_SNOOZE -> handleTriggerSnooze(appContext, intent)
                }
            } catch (throwable: Throwable) {
                Log.w(TAG, "Notification action failed: ${intent.action}", throwable)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleDismiss(context: Context, intent: Intent) {
        val alertId = intent.getStringExtra(EXTRA_ALERT_UNIQUE_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        // Cancel the notification
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }

        // Persist the dismissal
        AlertPreferences(context.alertPreferencesDataStore).addDismissedAlert(alertId)
        AlertsWidgetProvider.requestUpdate(context)
    }

    private suspend fun handleSnooze(context: Context, intent: Intent) {
        val alertJson = intent.getStringExtra(EXTRA_ALERT_JSON) ?: return
        val alert = AlertSnoozeScheduler.decodeAlert(alertJson) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        // Cancel current notification
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }

        // Schedule snooze alarm
        val durationMinutes = AlertPreferences(context.alertPreferencesDataStore).snoozeDuration.first()
        AlertSnoozeScheduler.schedule(context, alert, durationMinutes)
    }

    private suspend fun handleDismissGroup(context: Context, intent: Intent) {
        // The summary carries the ids of everything it stands for: NotificationManager
        // has no "cancel this group" call, and cancelling the summary alone would
        // leave the children behind.
        val alertIds = intent.getStringArrayExtra(EXTRA_ALERT_UNIQUE_IDS) ?: return
        val summaryId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val manager = NotificationManagerCompat.from(context)
        alertIds.forEach { manager.cancel(AlertNotificationIds.forAlert(it)) }
        if (summaryId != -1) manager.cancel(summaryId)

        val prefs = AlertPreferences(context.alertPreferencesDataStore)
        alertIds.forEach { prefs.addDismissedAlert(it) }
        AlertsWidgetProvider.requestUpdate(context)
    }

    private suspend fun handleTriggerSnooze(context: Context, intent: Intent) {
        val alertJson = intent.getStringExtra(EXTRA_ALERT_JSON) ?: return
        val alert = AlertSnoozeScheduler.decodeAlert(alertJson) ?: return
        if (!AlertSnoozeScheduler.isAlertActive(alert)) return

        AlertNotifier.notifyAlerts(context, listOf(alert))
    }

    companion object {
        private const val TAG = "NotificationAction"

        const val ACTION_DISMISS = "com.example.pokemonalertsv2.ACTION_DISMISS_ALERT"
        const val ACTION_SNOOZE = "com.example.pokemonalertsv2.ACTION_SNOOZE_ALERT"
        const val ACTION_TRIGGER_SNOOZE = "com.example.pokemonalertsv2.ACTION_TRIGGER_SNOOZE_ALERT"
        const val ACTION_DISMISS_GROUP = "com.example.pokemonalertsv2.ACTION_DISMISS_ALERT_GROUP"

        const val EXTRA_ALERT_UNIQUE_ID = "extra_alert_unique_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_ALERT_JSON = "extra_alert_json"
        const val EXTRA_ALERT_UNIQUE_IDS = "extra_alert_unique_ids"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
