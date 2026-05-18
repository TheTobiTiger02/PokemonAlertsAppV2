package com.example.pokemonalertsv2.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build
import com.example.pokemonalertsv2.data.PokemonAlert
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.app.AlarmManagerCompat

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
            ACTION_SNOOZE -> {
                val alertJson = intent.getStringExtra(EXTRA_ALERT_JSON) ?: return
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

                // Cancel current notification
                if (notificationId != -1) {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                }

                // Schedule snooze alarm
                val prefs = AlertPreferences(context.alertPreferencesDataStore)
                CoroutineScope(Dispatchers.IO).launch {
                    val durationMinutes = prefs.snoozeDuration.first()
                    val triggerAt = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
                    
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@launch
                    val triggerIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                        action = ACTION_TRIGGER_SNOOZE
                        putExtra(EXTRA_ALERT_JSON, alertJson)
                    }
                    
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or 
                               if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                    
                    // Use alertId hash as request code for the alarm
                    val alert = try { Json.decodeFromString<PokemonAlert>(alertJson) } catch(e: Exception) { null }
                    val requestCode = alert?.uniqueId?.hashCode() ?: alertJson.hashCode()
                    
                    val pendingIntent = PendingIntent.getBroadcast(context, requestCode, triggerIntent, flags)
                    
                    AlarmManagerCompat.setExactAndAllowWhileIdle(
                        alarmManager,
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                }
            }
            ACTION_TRIGGER_SNOOZE -> {
                val alertJson = intent.getStringExtra(EXTRA_ALERT_JSON) ?: return
                val alert = try { Json.decodeFromString<PokemonAlert>(alertJson) } catch(e: Exception) { return }
                
                CoroutineScope(Dispatchers.Main).launch {
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
