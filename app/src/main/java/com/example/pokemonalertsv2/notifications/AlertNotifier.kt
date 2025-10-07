package com.example.pokemonalertsv2.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity

object AlertNotifier {
    const val CHANNEL_ID = "pokemon_alerts_channel"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_name)
            val description = context.getString(R.string.notification_channel_description)
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH).apply {
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                this.description = description
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
            }
            val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun notifyAlerts(context: Context, alerts: List<PokemonAlert>) {
        if (alerts.isEmpty()) return
        ensureChannel(context)
        val notificationManager = NotificationManagerCompat.from(context)

        alerts.forEachIndexed { index, alert ->
            val notificationIntent = AlertDetailActivity.createIntent(context, alert)
            val pendingIntent = PendingIntent.getActivity(
                context,
                index,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_poke_notification)
                .setContentTitle(alert.name)
                .setContentText(alert.type ?: context.getString(R.string.notification_default_body))
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(alert.description)
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(alert.uniqueId.hashCode(), notification)
        }
    }

    private fun mutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
    }
}
