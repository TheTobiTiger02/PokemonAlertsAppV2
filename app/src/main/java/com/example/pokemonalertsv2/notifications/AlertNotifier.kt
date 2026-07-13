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
import com.example.pokemonalertsv2.PokemonAlertsApplication
import com.example.pokemonalertsv2.data.AlertPreferencesStore
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity
import com.example.pokemonalertsv2.ui.alerts.buildAlertGlanceMetadata
import com.example.pokemonalertsv2.ui.alerts.formatAlertTitle
import com.example.pokemonalertsv2.ui.alerts.resolveAlertVisualStyle
import com.example.pokemonalertsv2.util.LocationUtils
import com.example.pokemonalertsv2.util.MapFallbackImageGenerator
import com.example.pokemonalertsv2.util.WalkingRouteUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object AlertNotifier {
    const val CHANNEL_ID = "pokemon_alerts_channel"
    const val CHANNEL_RAIDS = "pokemon_alerts_raids"
    const val CHANNEL_SPAWNS = "pokemon_alerts_spawns"
    const val CHANNEL_QUESTS = "pokemon_alerts_quests"

    internal fun buildNotificationContentText(
        alert: PokemonAlert,
        distanceText: String? = null,
        walkingText: String? = null
    ): String = buildAlertGlanceMetadata(
        alert = alert,
        distanceText = distanceText,
        walkingText = walkingText
    )

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
        
        val repository = PokemonAlertsRepository.create(context)
        val settings = NotificationSettings.load(repository.alertPreferences)
        if (!settings.notificationsEnabled || settings.isSilenced) return
        
        val notificationManager = NotificationManagerCompat.from(context)
        val imageLoader = PokemonAlertsApplication.imageLoader(context)
        val userLocation = NotificationLocationCache.get(context)
        val walkingRoutes = userLocation?.let { location ->
            WalkingRouteUtils.getWalkingRoutes(location, alerts)
        } ?: emptyMap()

        alerts.forEachIndexed { index, alert ->
            // Area Filter
            if (settings.selectedArea != "All" && alert.area != settings.selectedArea) return@forEachIndexed
            
            // Distance Filter (allow if maxDistance is 0 or if location is unknown)
            if (settings.maxDistance > 0 && userLocation != null) {
                val latitude = alert.latitude
                val longitude = alert.longitude
                if (latitude != null && longitude != null) {
                    val results = FloatArray(1)
                    Location.distanceBetween(userLocation.latitude, userLocation.longitude, latitude, longitude, results)
                    if (!results[0].isNaN() && results[0] > settings.maxDistance * 1000) {
                        return@forEachIndexed
                    }
                }
            }

            if (!settings.shouldNotify(alert)) return@forEachIndexed
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

            val straightLineDistanceMeters = userLocation?.let { loc ->
                val latitude = alert.latitude
                val longitude = alert.longitude
                if (latitude == null || longitude == null) return@let null
                val results = FloatArray(1)
                runCatching {
                    Location.distanceBetween(loc.latitude, loc.longitude, latitude, longitude, results)
                }.getOrNull()
                val meters = results.getOrNull(0) ?: Float.NaN
                meters.takeUnless { it.isNaN() }
            }
            val routeDisplayInfo = WalkingRouteUtils.buildRouteDisplayInfo(
                straightLineDistanceMeters = straightLineDistanceMeters,
                routeInfo = walkingRoutes[alert.uniqueId]
            )
            val distanceText = routeDisplayInfo.distanceText
            val walkingText = routeDisplayInfo.walkingText

            val baseText = alert.type?.joinToString(", ")
                ?: context.getString(R.string.notification_default_body)
            val contentText = buildNotificationContentText(
                alert = alert,
                distanceText = distanceText,
                walkingText = walkingText
            )
            val expandedText = buildString {
                append(contentText)
                if (alert.description.isNotBlank() && alert.description != baseText) {
                    appendLine()
                    append(alert.description)
                }
            }

            // Fully prepare the image before posting so the first notification already
            // contains its map fallback. A bounded wait keeps delivery reliable offline.
            val bitmap = resolveAlertNotificationImage(
                alert = alert,
                loadRemoteImage = { url -> loadImageBitmap(context, imageLoader, url) },
                generateMapFallback = { coordinates, thumbnailUrl ->
                    MapFallbackImageGenerator.generate(
                        context = context,
                        latitude = coordinates.latitude,
                        longitude = coordinates.longitude,
                        thumbnailUrl = thumbnailUrl,
                        outputWidth = 512,
                        outputHeight = 256
                    )
                }
            )

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
                    NotificationCompat.BigTextStyle().bigText(expandedText)
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setColor(resolveAlertVisualStyle(alert).category.accentArgb.toInt())
                .setVibrate(if (settings.vibrateEnabled) longArrayOf(0, 250, 250, 250) else longArrayOf(0))
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

    internal data class NotificationSettings(
        val notificationsEnabled: Boolean,
        val raidsEnabled: Boolean,
        val spawnsEnabled: Boolean,
        val questsEnabled: Boolean,
        val hundosEnabled: Boolean,
        val pvpEnabled: Boolean,
        val nundosEnabled: Boolean,
        val kecleonEnabled: Boolean,
        val rocketEnabled: Boolean,
        val vibrateEnabled: Boolean,
        val silenceUntil: Long,
        val selectedArea: String,
        val maxDistance: Int,
        val excludedHundoTypes: Set<String>,
        val excludedNundoTypes: Set<String>,
        val excludedPvpTypes: Set<String>,
        val excludedSpawnTypes: Set<String>,
        val excludedRocketTypes: Set<String>,
        val excludedRaidTiers: Set<String>,
        val allowedHundoSpecies: Set<String>,
        val allowedNundoSpecies: Set<String>,
        val allowedPvpSpecies: Set<String>,
        val allowedSpawnSpecies: Set<String>,
        val nowMillis: Long = System.currentTimeMillis()
    ) {
        val isSilenced: Boolean get() = silenceUntil > nowMillis

        private val excludedHundoTypesLower = excludedHundoTypes.lowercaseSet()
        private val excludedNundoTypesLower = excludedNundoTypes.lowercaseSet()
        private val excludedPvpTypesLower = excludedPvpTypes.lowercaseSet()
        private val excludedSpawnTypesLower = excludedSpawnTypes.lowercaseSet()
        private val excludedRocketTypesLower = excludedRocketTypes.lowercaseSet()
        private val excludedRaidTiersLower = excludedRaidTiers.lowercaseSet()
        private val allowedHundoSpeciesLower = allowedHundoSpecies.lowercaseSet()
        private val allowedNundoSpeciesLower = allowedNundoSpecies.lowercaseSet()
        private val allowedPvpSpeciesLower = allowedPvpSpecies.lowercaseSet()
        private val allowedSpawnSpeciesLower = allowedSpawnSpecies.lowercaseSet()

        fun shouldNotify(alert: PokemonAlert): Boolean {
            if (!notificationsEnabled || isSilenced) return false
            return when {
                alert.hasTypeContaining("raid") -> {
                    raidsEnabled && raidTierAllowed(alert)
                }
                alert.hasTypeContaining("rare") || alert.hasTypeContaining("spawn") -> {
                    spawnsEnabled &&
                        !isPokemonTypeExcluded(alert, excludedSpawnTypesLower) &&
                        isSpeciesAllowed(alert, allowedSpawnSpeciesLower)
                }
                alert.hasTypeContaining("quest") -> questsEnabled
                alert.hasType("hundo") -> {
                    hundosEnabled &&
                        !isPokemonTypeExcluded(alert, excludedHundoTypesLower) &&
                        isSpeciesAllowed(alert, allowedHundoSpeciesLower)
                }
                alert.hasType("pvp") -> {
                    pvpEnabled &&
                        !isPokemonTypeExcluded(alert, excludedPvpTypesLower) &&
                        isSpeciesAllowed(alert, allowedPvpSpeciesLower)
                }
                alert.hasType("nundo") -> {
                    nundosEnabled &&
                        !isPokemonTypeExcluded(alert, excludedNundoTypesLower) &&
                        isSpeciesAllowed(alert, allowedNundoSpeciesLower)
                }
                alert.hasType("kecleon") -> kecleonEnabled
                alert.hasType("rocket") -> {
                    rocketEnabled && alert.gruntType?.lowercase()?.let { it !in excludedRocketTypesLower } != false
                }
                else -> true
            }
        }

        private fun raidTierAllowed(alert: PokemonAlert): Boolean {
            val raidTier = alert.type?.find { it.matches(RAID_TIER_REGEX) }
            return raidTier == null || raidTier.lowercase() !in excludedRaidTiersLower
        }

        private fun isPokemonTypeExcluded(alert: PokemonAlert, excludedSet: Set<String>): Boolean {
            if (excludedSet.isEmpty()) return false
            return alert.type?.any { it.lowercase() in excludedSet } == true
        }

        private fun isSpeciesAllowed(alert: PokemonAlert, allowedSet: Set<String>): Boolean {
            if (allowedSet.isEmpty()) return true
            return (alert.pokemon ?: alert.name).lowercase() in allowedSet
        }

        companion object {
            private val RAID_TIER_REGEX = Regex("\\d+|mega", RegexOption.IGNORE_CASE)

            suspend fun load(preferences: AlertPreferencesStore): NotificationSettings {
                return NotificationSettings(
                    notificationsEnabled = preferences.notificationsEnabled.first(),
                    raidsEnabled = preferences.raidsNotifications.first(),
                    spawnsEnabled = preferences.spawnsNotifications.first(),
                    questsEnabled = preferences.questsNotifications.first(),
                    hundosEnabled = preferences.hundosNotifications.first(),
                    pvpEnabled = preferences.pvpNotifications.first(),
                    nundosEnabled = preferences.nundosNotifications.first(),
                    kecleonEnabled = preferences.kecleonNotifications.first(),
                    rocketEnabled = preferences.rocketNotifications.first(),
                    vibrateEnabled = preferences.notificationVibrate.first(),
                    silenceUntil = preferences.silenceUntil.first(),
                    selectedArea = preferences.selectedArea.first(),
                    maxDistance = preferences.maxDistance.first(),
                    excludedHundoTypes = preferences.excludedHundoTypes.first(),
                    excludedNundoTypes = preferences.excludedNundoTypes.first(),
                    excludedPvpTypes = preferences.excludedPvpTypes.first(),
                    excludedSpawnTypes = preferences.excludedSpawnTypes.first(),
                    excludedRocketTypes = preferences.excludedRocketTypes.first(),
                    excludedRaidTiers = preferences.excludedRaidTiers.first(),
                    allowedHundoSpecies = preferences.allowedHundoSpecies.first(),
                    allowedNundoSpecies = preferences.allowedNundoSpecies.first(),
                    allowedPvpSpecies = preferences.allowedPvpSpecies.first(),
                    allowedSpawnSpecies = preferences.allowedSpawnSpecies.first()
                )
            }
        }
    }

    private object NotificationLocationCache {
        private const val STALE_LOCATION_MS = 10 * 60 * 1000L

        @Volatile
        private var cachedLocation: Location? = null

        @Volatile
        private var cachedAtMillis: Long = 0L

        /**
         * Suspending location fetch that actually waits for a fix.
         * Falls back to FusedLocationProvider's last-known location if a fresh
         * fix cannot be obtained within the timeout.
         */
        suspend fun get(context: Context): Location? {
            val now = System.currentTimeMillis()
            // Return cached if still fresh enough
            cachedLocation?.takeIf { now - cachedAtMillis <= STALE_LOCATION_MS }?.let { return it }

            val appContext = context.applicationContext
            // Try a fresh location fix with a reasonable timeout
            val freshLocation = LocationUtils.getCurrentLocationOrNull(
                context = appContext,
                timeoutMs = 4000,
                highAccuracy = false
            )
            if (freshLocation != null) {
                cachedLocation = freshLocation
                cachedAtMillis = System.currentTimeMillis()
                return freshLocation
            }

            // Fall back to last-known location from FusedLocationProvider
            val lastKnown = getLastKnownLocation(appContext)
            if (lastKnown != null) {
                cachedLocation = lastKnown
                cachedAtMillis = System.currentTimeMillis()
                return lastKnown
            }

            return null
        }

        @android.annotation.SuppressLint("MissingPermission")
        private suspend fun getLastKnownLocation(context: Context): Location? {
            if (!hasLocationPermission(context)) return null
            return withContext(Dispatchers.IO) {
                runCatching {
                    val fused = com.google.android.gms.location.LocationServices
                        .getFusedLocationProviderClient(context)
                    kotlinx.coroutines.suspendCancellableCoroutine<Location?> { cont ->
                        fused.lastLocation
                            .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                            .addOnFailureListener { _ -> if (cont.isActive) cont.resume(null) }
                    }
                }.getOrNull()
            }
        }

        private fun hasLocationPermission(context: Context): Boolean {
            val fine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
            val coarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
            return fine == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                    coarse == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun Set<String>.lowercaseSet(): Set<String> {
        return mapTo(LinkedHashSet(size)) { it.lowercase() }
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

    private suspend fun loadImageBitmap(context: Context, imageLoader: ImageLoader, imageUrl: String?): Bitmap? {
        if (imageUrl.isNullOrBlank()) return null
        
        return withContext(Dispatchers.IO) {
            try {
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

}
