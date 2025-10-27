package com.example.pokemonalertsv2.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.location.Location
import android.location.LocationManager
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
        val userLocation = getLastKnownLocation(context)

        alerts.forEachIndexed { index, alert ->
            val notificationIntent = AlertDetailActivity.createIntent(context, alert)
            val pendingIntent = PendingIntent.getActivity(
                context,
                index,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
            )

            val distanceText = userLocation?.let { loc ->
                val results = FloatArray(1)
                runCatching {
                    Location.distanceBetween(loc.latitude, loc.longitude, alert.latitude, alert.longitude, results)
                }.getOrNull()
                val meters = results.getOrNull(0) ?: Float.NaN
                if (meters.isNaN()) null else formatDistance(meters)
            }

            val baseText = alert.type ?: context.getString(R.string.notification_default_body)
            val contentText = if (!distanceText.isNullOrBlank()) "$distanceText • $baseText" else baseText

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_poke_notification)
                .setContentTitle(alert.name)
                .setContentText(contentText)
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        buildString {
                            if (!distanceText.isNullOrBlank()) {
                                append(distanceText)
                                append(" • ")
                            }
                            if (alert.description.isNotBlank()) append(alert.description) else append(baseText)
                        }
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(alert.uniqueId.hashCode(), notification)
        }
    }

    private fun immutableFlag(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0

    private fun getLastKnownLocation(context: Context): Location? {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val providers = lm.getProviders(true)
            var best: Location? = null
            for (p in providers) {
                val l = try { lm.getLastKnownLocation(p) } catch (_: SecurityException) { null }
                if (l != null && (best == null || (l.accuracy < best!!.accuracy))) {
                    best = l
                }
            }
            best
        } catch (_: Throwable) { null }
    }

    private fun formatDistance(meters: Float): String {
        return if (meters >= 1000f) String.format(java.util.Locale.getDefault(), "%.1f km", meters / 1000f)
        else String.format(java.util.Locale.getDefault(), "%.0f m", meters)
    }
}
