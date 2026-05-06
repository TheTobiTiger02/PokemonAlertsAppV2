package com.example.pokemonalertsv2.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
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
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity
import com.example.pokemonalertsv2.ui.alerts.formatAlertTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object AlertNotifier {
    const val CHANNEL_ID = "pokemon_alerts_channel"
    const val CHANNEL_RAIDS = "pokemon_alerts_raids"
    const val CHANNEL_SPAWNS = "pokemon_alerts_spawns"
    const val CHANNEL_QUESTS = "pokemon_alerts_quests"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
            
            // Generic channel
            val name = context.getString(R.string.notification_channel_name)
            val channelDescription = context.getString(R.string.notification_channel_description)
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH).apply {
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                this.description = channelDescription
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)

            // Raids channel
            val raidsChannel = NotificationChannel(CHANNEL_RAIDS, "Raids", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications for Raid Battles"
                enableLights(true)
                lightColor = Color.MAGENTA
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(raidsChannel)

            // Spawns channel
            val spawnsChannel = NotificationChannel(CHANNEL_SPAWNS, "Spawns", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifications for Wild Spawns"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(spawnsChannel)

            // Quests channel
            val questsChannel = NotificationChannel(CHANNEL_QUESTS, "Quests", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifications for Field Research"
                enableLights(true)
                lightColor = Color.CYAN
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(questsChannel)
        }
    }

    suspend fun notifyAlerts(context: Context, alerts: List<PokemonAlert>) {
        if (alerts.isEmpty()) return
        ensureChannel(context)
        
        // Check global notification preference
        val repository = PokemonAlertsRepository.create(context)
        val notificationsEnabled = repository.alertPreferences.notificationsEnabled.first()
        if (!notificationsEnabled) return
        
        // Check if notifications are silenced
        val silenceUntil = repository.alertPreferences.silenceUntil.first()
        if (silenceUntil > System.currentTimeMillis()) {
            return
        }
        
        val raidsEnabled = repository.alertPreferences.raidsNotifications.first()
        val spawnsEnabled = repository.alertPreferences.spawnsNotifications.first()
        val questsEnabled = repository.alertPreferences.questsNotifications.first()
        val hundosEnabled = repository.alertPreferences.hundosNotifications.first()
        val pvpEnabled = repository.alertPreferences.pvpNotifications.first()
        val nundosEnabled = repository.alertPreferences.nundosNotifications.first()
        val kecleonEnabled = repository.alertPreferences.kecleonNotifications.first()
        val rocketEnabled = repository.alertPreferences.rocketNotifications.first()
        val vibrateEnabled = repository.alertPreferences.notificationVibrate.first()
        
        // Load excluded type sets for granular filtering
        val excludedHundoTypes = repository.alertPreferences.excludedHundoTypes.first()
        val excludedNundoTypes = repository.alertPreferences.excludedNundoTypes.first()
        val excludedPvpTypes = repository.alertPreferences.excludedPvpTypes.first()
        val excludedSpawnTypes = repository.alertPreferences.excludedSpawnTypes.first()
        val excludedRocketTypes = repository.alertPreferences.excludedRocketTypes.first()
        val excludedRaidTiers = repository.alertPreferences.excludedRaidTiers.first()
        
        val selectedArea = repository.alertPreferences.selectedArea.first()
        val maxDistance = repository.alertPreferences.maxDistance.first()
        
        val notificationManager = NotificationManagerCompat.from(context)
        // Actively try to get a fresh location fix; keep it best-effort with short timeout
        val userLocation = com.example.pokemonalertsv2.util.LocationUtils.getCurrentLocationOrNull(context, timeoutMs = 5000, highAccuracy = false)

        alerts.forEachIndexed { index, alert ->
            // Helper function to check if any of the alert's Pokemon types are excluded
            fun isPokemonTypeExcluded(excludedSet: Set<String>): Boolean {
                if (excludedSet.isEmpty()) return false
                val alertTypes = alert.type ?: return false
                return alertTypes.any { type -> 
                    type.lowercase() in excludedSet.map { it.lowercase() }
                }
            }
            
            // Area Filter
            if (selectedArea != "All" && alert.area != selectedArea) return@forEachIndexed
            
            // Distance Filter (allow if maxDistance is 0 or if location is unknown)
            if (maxDistance > 0 && userLocation != null) {
                val results = FloatArray(1)
                Location.distanceBetween(userLocation.latitude, userLocation.longitude, alert.latitude ?: 0.0, alert.longitude ?: 0.0, results)
                if (!results[0].isNaN() && results[0] > maxDistance * 1000) {
                    return@forEachIndexed
                }
            }
            
            // Filter based on alert type and user preferences
            val shouldNotify = when {
                alert.hasTypeContaining("raid") -> {
                    if (!raidsEnabled) false
                    else {
                        // Check raid tier exclusions (e.g., "1", "3", "5", "mega")
                        val raidTier = alert.type?.find { 
                            it.matches(Regex("\\d+|mega", RegexOption.IGNORE_CASE)) 
                        }
                        raidTier == null || raidTier.lowercase() !in excludedRaidTiers.map { it.lowercase() }
                    }
                }
                alert.hasTypeContaining("rare") || alert.hasTypeContaining("spawn") -> 
                    spawnsEnabled && !isPokemonTypeExcluded(excludedSpawnTypes)
                alert.hasTypeContaining("quest") -> questsEnabled
                alert.hasType("hundo") -> hundosEnabled && !isPokemonTypeExcluded(excludedHundoTypes)
                alert.hasType("pvp") -> pvpEnabled && !isPokemonTypeExcluded(excludedPvpTypes)
                alert.hasType("nundo") -> nundosEnabled && !isPokemonTypeExcluded(excludedNundoTypes)
                alert.hasType("kecleon") -> kecleonEnabled
                alert.hasType("rocket") -> {
                    if (!rocketEnabled) false
                    else {
                        // Check grunt type exclusions (e.g., "fire", "water", "psychic")
                        val gruntType = alert.gruntType?.lowercase()
                        gruntType == null || gruntType !in excludedRocketTypes.map { it.lowercase() }
                    }
                }
                else -> true // Default to sending for unknown types
            }
            
            if (!shouldNotify) return@forEachIndexed
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
                    Location.distanceBetween(loc.latitude, loc.longitude, alert.latitude ?: 0.0, alert.longitude ?: 0.0, results)
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

            // Load the alert image using Coil, with map+thumbnail composite fallback
            val bitmap = if (!alert.imageUrl.isNullOrBlank()) {
                loadImageBitmap(context, alert.imageUrl)
            } else if (alert.latitude != null && alert.longitude != null && !alert.thumbnailUrl.isNullOrBlank()) {
                // Generate composite map + thumbnail image
                com.example.pokemonalertsv2.util.MapFallbackImageGenerator.generate(
                    context = context,
                    latitude = alert.latitude,
                    longitude = alert.longitude,
                    thumbnailUrl = alert.thumbnailUrl,
                    outputWidth = 512,
                    outputHeight = 256
                ) ?: loadImageBitmap(context, alert.thumbnailUrl)
            } else {
                loadImageBitmap(context, alert.thumbnailUrl)
            }

            // Select Channel ID based on type
            val channelId = when {
                alert.hasTypeContaining("raid") -> CHANNEL_RAIDS
                alert.hasTypeContaining("rare") || alert.hasTypeContaining("spawn") -> CHANNEL_SPAWNS
                alert.hasTypeContaining("quest") -> CHANNEL_QUESTS
                else -> CHANNEL_ID
            }

            val notificationBuilder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_poke_notification)
                .setContentTitle(formatAlertTitle(alert))
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
                .setColor(ContextCompat.getColor(context, R.color.poke_red))
                .setVibrate(if (vibrateEnabled) longArrayOf(0, 250, 250, 250) else longArrayOf(0))
                .addAction(
                    R.drawable.ic_map,
                    context.getString(R.string.notification_action_directions),
                    mapsPendingIntent
                )
                // Quick Action: Dismiss
                .addAction(
                    R.drawable.ic_poke_notification,
                    "Dismiss",
                    createDismissPendingIntent(context, alert)
                )
                // Quick Action: Open in PiP
                .addAction(
                    R.drawable.ic_pip,
                    "PiP",
                    createPipPendingIntent(context, alert)
                )

            // Add the image as large icon and big picture if available
            bitmap?.let {
                notificationBuilder.setLargeIcon(it)
                // Use BigPictureStyle if we have an image
                notificationBuilder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(it)
                        .bigLargeIcon(null as Bitmap?) // Hide large icon when expanded
                        .setBigContentTitle(formatAlertTitle(alert))
                        .setSummaryText(contentText)
                )
            }

            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(alert.uniqueId.hashCode(), notificationBuilder.build())
            }
        }
    }

    private fun immutableFlag(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0

    private fun createDismissPendingIntent(context: Context, alert: PokemonAlert): PendingIntent {
        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS
            putExtra(NotificationActionReceiver.EXTRA_ALERT_UNIQUE_ID, alert.uniqueId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, alert.uniqueId.hashCode())
        }
        return PendingIntent.getBroadcast(
            context,
            alert.uniqueId.hashCode() + 2000, // unique request code
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
    }

    private fun createPipPendingIntent(context: Context, alert: PokemonAlert): PendingIntent {
        val pipIntent = AlertDetailActivity.createIntent(context, alert).apply {
            putExtra(AlertDetailActivity.EXTRA_LAUNCH_PIP, true)
        }
        return PendingIntent.getActivity(
            context,
            alert.uniqueId.hashCode() + 3000, // unique request code
            pipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
    }

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
