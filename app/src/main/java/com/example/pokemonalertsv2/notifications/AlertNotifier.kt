package com.example.pokemonalertsv2.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    suspend fun notifyAlerts(context: Context, alerts: List<PokemonAlert>) {
        if (alerts.isEmpty()) return
        ensureChannel(context)
        val notificationManager = NotificationManagerCompat.from(context)
        // Actively try to get a fresh location fix; keep it best-effort with short timeout
        val userLocation = com.example.pokemonalertsv2.util.LocationUtils.getCurrentLocationOrNull(context, timeoutMs = 5000, highAccuracy = false)

        alerts.forEachIndexed { index, alert ->
            val notificationIntent = AlertDetailActivity.createIntent(context, alert)
            val pendingIntent = PendingIntent.getActivity(
                context,
                // Use a stable, unique requestCode per alert to avoid PendingIntent collisions across runs
                alert.uniqueId.hashCode(),
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
            )

            // Create PendingIntent for opening Google Maps
            val mapsIntent = Intent(Intent.ACTION_VIEW, alert.googleMapsUri)
            val mapsPendingIntent = PendingIntent.getActivity(
                context,
                alert.uniqueId.hashCode() + 1, // Different request code
                mapsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
            )

            val distancePair = userLocation?.let { loc ->
                val results = FloatArray(1)
                runCatching {
                    Location.distanceBetween(loc.latitude, loc.longitude, alert.latitude, alert.longitude, results)
                }.getOrNull()
                val meters = results.getOrNull(0) ?: Float.NaN
                if (meters.isNaN()) null else meters to formatDistance(meters)
            }
            val distanceText = distancePair?.second
            val walkingText = distancePair?.first?.let { formatWalkingTime(it) }

            val baseText = alert.type ?: context.getString(R.string.notification_default_body)
            val chips = listOfNotNull(distanceText, walkingText)
            val prefix = if (chips.isNotEmpty()) chips.joinToString(" • ") + " • " else ""
            val contentText = prefix + baseText

            // Load the alert image using Coil
            val bitmap = loadImageBitmap(context, alert.imageUrl ?: alert.thumbnailUrl)

            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_poke_notification)
                .setContentTitle(alert.name)
                .setContentText(contentText)
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        buildString {
                            if (!distanceText.isNullOrBlank() || !walkingText.isNullOrBlank()) {
                                listOfNotNull(distanceText, walkingText).joinTo(this, separator = " • ")
                                append(" • ")
                            }
                            if (alert.description.isNotBlank()) append(alert.description) else append(baseText)
                        }
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setColor(context.getColor(R.color.pokemon_red))
                .addAction(
                    R.drawable.ic_poke_notification,
                    context.getString(R.string.notification_action_directions),
                    mapsPendingIntent
                )

            // Add the image as large icon and big picture if available
            bitmap?.let {
                notificationBuilder.setLargeIcon(it)
                // Use BigPictureStyle if we have an image
                notificationBuilder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(it)
                        .bigLargeIcon(null as Bitmap?) // Hide large icon when expanded
                        .setBigContentTitle(alert.name)
                        .setSummaryText(
                            buildString {
                                if (!distanceText.isNullOrBlank() || !walkingText.isNullOrBlank()) {
                                    listOfNotNull(distanceText, walkingText).joinTo(this, separator = " • ")
                                }
                                if (alert.description.isNotBlank()) {
                                    if (isNotEmpty()) append(" • ")
                                    append(alert.description)
                                } else if (alert.type != null) {
                                    if (isNotEmpty()) append(" • ")
                                    append(alert.type)
                                }
                            }
                        )
                )
            }

            notificationManager.notify(alert.uniqueId.hashCode(), notificationBuilder.build())
        }
    }

    private fun immutableFlag(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0

    private suspend fun loadImageBitmap(context: Context, imageUrl: String?): Bitmap? {
        if (imageUrl.isNullOrBlank()) return null
        
        return withContext(Dispatchers.IO) {
            try {
                val imageLoader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .scale(Scale.FIT)
                    .allowHardware(false) // Disable hardware bitmaps for notifications
                    .build()
                
                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                } else {
                    null
                }
            } catch (e: Exception) {
                // Log or silently fail - notifications will still show without image
                null
            }
        }
    }

    // Removed last-known location method to honor the requirement to use an active fix.

    private fun formatDistance(meters: Float): String {
        return if (meters >= 1000f) String.format(java.util.Locale.getDefault(), "%.1f km", meters / 1000f)
        else String.format(java.util.Locale.getDefault(), "%.0f m", meters)
    }

    private fun formatWalkingTime(meters: Float): String {
        // Assume ~5 km/h walking speed => ~83.33 m/min
        val minutes = kotlin.math.ceil((meters / 83.333f).toDouble()).toInt().coerceAtLeast(1)
        return String.format(java.util.Locale.getDefault(), "%d min walk", minutes)
    }
}
