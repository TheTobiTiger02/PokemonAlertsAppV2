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
import com.example.pokemonalertsv2.data.godex.GoDexMatchStatus
import com.example.pokemonalertsv2.data.godex.GoDexMatchResult
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.MainActivity
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.data.RaidTierParser
import com.example.pokemonalertsv2.data.AlertFilterMatcher
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterMatchContext
import com.example.pokemonalertsv2.data.isDirectlyInRange
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity
import com.example.pokemonalertsv2.ui.alerts.buildAlertGlanceMetadata
import com.example.pokemonalertsv2.ui.alerts.displayCp
import com.example.pokemonalertsv2.ui.alerts.formatAlertTitle
import com.example.pokemonalertsv2.ui.alerts.resolveAlertVisualStyle
import com.example.pokemonalertsv2.util.CachedLocationProvider
import com.example.pokemonalertsv2.util.MapFallbackImageGenerator
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.WalkingRouteUtils
import com.example.pokemonalertsv2.util.WalkingRouteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import com.example.pokemonalertsv2.util.TravelTime

object AlertNotifier {
    const val CHANNEL_ID = "pokemon_alerts_channel"
    const val CHANNEL_RAIDS = "pokemon_alerts_raids"
    const val CHANNEL_SPAWNS = "pokemon_alerts_spawns"
    const val CHANNEL_QUESTS = "pokemon_alerts_quests"

    /** Beyond this the inbox style truncates anyway; the rest becomes a "+N more". */
    private const val MAX_SUMMARY_LINES = 5

    /**
     * Individual notifications posted per wave, soonest-ending first. Everything past this
     * folds into the channel summaries as a "+N more" line — with the Darmstadt sources a
     * single wave can carry dozens of alerts and the shade would otherwise drown.
     */
    private const val MAX_NOTIFICATIONS_PER_BURST = 25

    internal fun buildNotificationContentText(
        alert: PokemonAlert,
        distanceText: String? = null,
        walkingText: String? = null,
        isInRange: Boolean = false
    ): String = buildAlertGlanceMetadata(
        alert = alert,
        distanceText = distanceText,
        walkingText = walkingText,
        isInRange = isInRange
    )

    internal fun compactNotificationText(
        alert: PokemonAlert,
        distanceText: String?,
        isInRange: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val remainingMinutes = TimeUtils.parseEndTimeToMillis(alert.endTime)
            ?.minus(nowMillis)
            ?.takeIf { it > 0 }
            ?.let { (it + 59_999L) / 60_000L }
        return listOfNotNull(
            remainingMinutes?.let { "$it min left" },
            if (isInRange) "In range" else distanceText?.takeIf { it.isNotBlank() },
            alert.displayCp?.let { "CP $it" }
        ).take(3).joinToString(" • ").ifBlank { resolveAlertVisualStyle(alert).label }
    }

    fun ensureChannel(context: Context) {
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
        val raidsChannel = NotificationChannel(
            CHANNEL_RAIDS,
            context.getString(R.string.notification_channel_raids_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_raids_description)
            enableLights(true)
            lightColor = Color.MAGENTA
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(raidsChannel)

        // Spawns channel
        val spawnsChannel = NotificationChannel(
            CHANNEL_SPAWNS,
            context.getString(R.string.notification_channel_spawns_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_spawns_description)
            enableLights(true)
            lightColor = Color.GREEN
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(spawnsChannel)

        // Quests channel
        val questsChannel = NotificationChannel(
            CHANNEL_QUESTS,
            context.getString(R.string.notification_channel_quests_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_quests_description)
            enableLights(true)
            lightColor = Color.CYAN
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(questsChannel)
    }

    suspend fun notifyAlerts(context: Context, alerts: List<PokemonAlert>) {
        if (alerts.isEmpty()) return
        ensureChannel(context)
        
        val repository = PokemonAlertsRepository.create(context)
        val goDexRepository = GoDexRepository.getInstance(context)
        val (goDexConfig, goDexEntries) = goDexRepository.notificationSnapshot()
        val settings = NotificationSettings.load(repository.alertPreferences).copy(
            goDexFilterEnabled = goDexConfig.notificationFilterEnabled
        )
        if (!settings.notificationsEnabled || settings.isSilenced) return
        
        val notificationManager = NotificationManagerCompat.from(context)
        val imageLoader = PokemonAlertsApplication.imageLoader(context)
        // Shared app-wide source so notifications and widgets compute distances from the
        // same fix during the same refresh instead of disagreeing.
        val userLocation = CachedLocationProvider.get(context, timeoutMs = 4000)

        // Pre-filter: Only alerts that match basic criteria and are physically reachable
        // earn a walking route request. This cuts 90%+ of provider calls during push storms.
        data class PreCandidate(
            val alert: PokemonAlert,
            val straightLineDistanceMeters: Float?,
            val goDexStatus: GoDexMatchResult
        )

        val preCandidates = buildList {
            alerts.forEach { alert ->
                if (alert.isInvalidated || (TimeUtils.parseEndTimeToMillis(alert.endTime) ?: Long.MAX_VALUE) <= System.currentTimeMillis()) return@forEach
                val straightLineDistanceMeters = userLocation?.let { loc ->
                    val latitude = alert.latitude ?: return@let null
                    val longitude = alert.longitude ?: return@let null
                    WalkingRouteUtils.straightLineDistanceMeters(
                        loc.latitude,
                        loc.longitude,
                        latitude,
                        longitude
                    )
                }

                val goDexStatus = if (alert.hasType("hundo")) {
                    goDexRepository.match(alert, goDexEntries, goDexConfig.isConnected)
                } else {
                    GoDexMatchResult(GoDexMatchStatus.NOT_CONFIGURED)
                }

                // Check non-routing filters (area, species, raid tier, etc.) using straight-line distance
                if (!settings.shouldNotify(
                        alert,
                        goDexStatus.status,
                        FilterMatchContext(
                            effectiveDistanceMeters = straightLineDistanceMeters,
                            directDistanceMeters = straightLineDistanceMeters
                        )
                    )
                ) return@forEach

                // Pre-check walking duration upper bound: even at a fast 6.5 km/h (1.8 m/s),
                // if straightLine > maxWalkingSeconds * 1.8m, it is impossible to walk in time.
                // Alerts directly in interaction range bypass walking duration.
                val maxWalkingMinutes = settings.filterDefinition?.maxWalkingMinutes ?: 0
                if (maxWalkingMinutes > 0 && straightLineDistanceMeters != null && !alert.isDirectlyInRange(straightLineDistanceMeters)) {
                    if (straightLineDistanceMeters > maxWalkingMinutes * 60L * 1.8f) return@forEach
                }

                add(PreCandidate(alert, straightLineDistanceMeters, goDexStatus))
            }
        }

        if (preCandidates.isEmpty()) return

        // Every surviving candidate gets a real route. The repository caps the batch
        // at the server ceiling, so the sort keeps the nearest ones if that ever binds.
        val topCandidates = preCandidates
            .sortedBy { it.straightLineDistanceMeters ?: Float.MAX_VALUE }

        val walkingRoutes = userLocation?.let { location ->
            WalkingRouteRepository.getInstance().getWalkingRoutes(
                origin = location,
                alerts = topCandidates.map { it.alert },
                timeoutMillis = WalkingRouteRepository.BACKGROUND_TIMEOUT_MILLIS
            )
        } ?: emptyMap()

        val postedByChannel = linkedMapOf<String, MutableList<PokemonAlert>>()

        // Pass 1 decides *which* alerts earn a notification; pass 2 pays for the expensive
        // parts (images, bitmaps) only for those. With the Darmstadt sources a single push
        // wave can carry dozens of alerts — posting one notification per alert would flood
        // the shade, so everything past the cap folds into the channel summaries instead.
        data class Candidate(
            val alert: PokemonAlert,
            val routeDisplayInfo: com.example.pokemonalertsv2.util.RouteDisplayInfo,
            val goDexStatus: GoDexMatchResult
        )

        val candidates = buildList {
            preCandidates.forEach { preCandidate ->
                val alert = preCandidate.alert
                val routeDisplayInfo = WalkingRouteUtils.buildRouteDisplayInfo(
                    straightLineDistanceMeters = preCandidate.straightLineDistanceMeters,
                    routeInfo = walkingRoutes[alert.uniqueId]
                )

                if (
                    !settings.shouldNotify(
                        alert,
                        preCandidate.goDexStatus.status,
                        FilterMatchContext(
                            effectiveDistanceMeters = routeDisplayInfo.effectiveDistanceMeters,
                            walkingDurationSeconds = routeDisplayInfo.walkingDurationSeconds,
                            directDistanceMeters = preCandidate.straightLineDistanceMeters
                        )
                    )
                ) return@forEach
                add(Candidate(alert, routeDisplayInfo, preCandidate.goDexStatus))
            }
        }

        // Soonest-ending first: the alerts about to vanish are the ones worth the buzz.
        // Alerts without an end time sort last and never win the cap race.
        val byUrgency = candidates.sortedBy { candidate ->
            TimeUtils.parseEndTimeToMillis(candidate.alert.endTime) ?: Long.MAX_VALUE
        }
        val selected = byUrgency.take(MAX_NOTIFICATIONS_PER_BURST)
        val overflowByChannel = byUrgency
            .drop(MAX_NOTIFICATIONS_PER_BURST)
            .groupingBy { candidate -> notificationChannelFor(candidate.alert) }
            .eachCount()

        val artworkUpdates = mutableListOf<suspend () -> Unit>()
        selected.forEach { candidate ->
            val alert = candidate.alert
            val notificationIntent = AlertDetailActivity.createIntent(
                context = context,
                alert = alert,
                returnToAlerts = true
            )
            val pendingIntent = PendingIntent.getActivity(
                context,
                AlertNotificationIds.forAlert(alert.uniqueId),
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
            )

            // Create PendingIntent for opening Google Maps
            val mapsIntent = Intent(Intent.ACTION_VIEW, alert.googleMapsUri)
            val mapsPendingIntent = PendingIntent.getActivity(
                context,
                AlertNotificationIds.forAlertAction("directions", alert.uniqueId),
                mapsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
            )

            val distanceText = candidate.routeDisplayInfo.distanceText
            val walkingText = candidate.routeDisplayInfo.walkingText

            val baseText = alert.type?.joinToString(", ")
                ?: context.getString(R.string.notification_default_body)
            val isInRange = alert.isDirectlyInRange(candidate.routeDisplayInfo.straightLineDistanceMeters)
            val contentText = buildNotificationContentText(
                alert = alert,
                distanceText = distanceText,
                walkingText = walkingText,
                isInRange = isInRange
            ) + goDexNotificationSuffix(alert, candidate.goDexStatus)
            val compactText = compactNotificationText(alert, distanceText, isInRange)
            val expandedText = buildString {
                append(contentText)
                if (alert.description.isNotBlank() && alert.description != baseText) {
                    appendLine()
                    append(alert.description)
                }
            }

            val channelId = notificationChannelFor(alert)

            val notificationBuilder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_poke_notification)
                .setContentTitle(formatAlertTitle(alert, candidate.goDexStatus.status))
                .setContentText(compactText)
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(expandedText)
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setColor(resolveAlertVisualStyle(alert).category.accentArgb.toInt())
                // Grouped by channel so a spawn wave collapses into one shade entry
                // instead of burying everything else the user has.
                .setGroup(channelId)
                .setVibrate(if (settings.vibrateEnabled) longArrayOf(0, 250, 250, 250) else longArrayOf(0))
                .addAction(
                    R.drawable.ic_map,
                    context.getString(R.string.notification_action_directions),
                    mapsPendingIntent
                )
                // Quick Action: Dismiss
                .addAction(
                    R.drawable.ic_poke_notification,
                    context.getString(R.string.notification_action_dismiss),
                    createDismissPendingIntent(context, alert)
                )
                // Quick Action: Open in PiP
                .addAction(
                    R.drawable.ic_pip,
                    context.getString(R.string.enter_pip_short),
                    createPipPendingIntent(context, alert)
                )

            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                val notificationId = AlertNotificationIds.forAlert(alert.uniqueId)
                notificationManager.notify(notificationId, notificationBuilder.build())
                postedByChannel.getOrPut(channelId) { mutableListOf() }.add(alert)
                artworkUpdates += suspend {
                    enrichActiveNotification(
                        context = context,
                        manager = notificationManager,
                        notificationId = notificationId,
                        builder = notificationBuilder,
                        title = formatAlertTitle(alert, candidate.goDexStatus.status),
                        contentText = contentText,
                        image = {
                            resolveAlertNotificationImage(
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
                        }
                    )
                }
            }
        }

        postedByChannel.forEach { (channelId, posted) ->
            postGroupSummary(
                context = context,
                notificationManager = notificationManager,
                channelId = channelId,
                posted = posted,
                overflowCount = overflowByChannel[channelId] ?: 0
            )
        }
        // Images start only after every text/action notification and group summary is visible.
        // Bound bitmap work so a large push wave does not exhaust memory.
        val artworkSlots = Semaphore(3)
        coroutineScope {
            artworkUpdates.map { update ->
                async(Dispatchers.IO) { artworkSlots.withPermit { update() } }
            }.awaitAll()
        }
    }

    internal suspend fun enrichActiveNotification(
        context: Context,
        manager: NotificationManagerCompat,
        notificationId: Int,
        builder: NotificationCompat.Builder,
        title: String,
        contentText: String,
        image: suspend () -> Bitmap?
    ) {
        val bitmap = image() ?: return
        // Dismissal or auto-cancel during image loading must not resurrect an alert.
        if (!manager.areNotificationsEnabled() || manager.activeNotifications.none { it.id == notificationId }) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        builder.setLargeIcon(bitmap).setOnlyAlertOnce(true).setStyle(
            NotificationCompat.BigPictureStyle()
                .bigPicture(bitmap)
                .bigLargeIcon(null as Bitmap?)
                .setBigContentTitle(title)
                .setSummaryText(contentText)
        )
        try {
            manager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Notification access can be revoked while artwork is loading.
        }
    }

    /** Channel grouping, shared by the posting loop and the burst-cap overflow tally. */
    private fun notificationChannelFor(alert: PokemonAlert): String = when {
        alert.hasTypeContaining("raid") -> CHANNEL_RAIDS
        alert.hasTypeContaining("common") || alert.hasTypeContaining("rare") || alert.hasTypeContaining("spawn") -> CHANNEL_SPAWNS
        alert.hasTypeContaining("quest") -> CHANNEL_QUESTS
        else -> CHANNEL_ID
    }

    /**
     * One summary per channel, so the shade shows "5 raids nearby" with the individual
     * alerts underneath rather than five top-level entries.
     *
     * Posted even for a single alert: without a summary the group is inconsistent, and
     * Android collapses a one-child group into just that child anyway.
     *
     * [overflowCount] counts alerts that passed every filter but lost the burst-cap race;
     * they surface here so the summary still tells the whole story.
     */
    private fun postGroupSummary(
        context: Context,
        notificationManager: NotificationManagerCompat,
        channelId: String,
        posted: List<PokemonAlert>,
        overflowCount: Int = 0
    ) {
        if (posted.isEmpty() && overflowCount <= 0) return
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return

        val summaryId = AlertNotificationIds.forSummary(channelId)
        // The headline counts everything the wave delivered for this channel, including the
        // burst-cap overflow, so "32 raids nearby" stays honest even when only 25 got cards.
        val label = groupLabel(channelId, posted.size + overflowCount)
        val inbox = NotificationCompat.InboxStyle().setBigContentTitle(label)
        posted.take(MAX_SUMMARY_LINES).forEach { alert ->
            inbox.addLine(summaryLine(alert))
        }
        val unlisted = (posted.size - MAX_SUMMARY_LINES).coerceAtLeast(0) + overflowCount
        if (unlisted > 0) {
            inbox.setSummaryText("+${unlisted} more")
        }

        val dismissAll = PendingIntent.getBroadcast(
            context,
            AlertNotificationIds.forSummaryAction("dismiss_all", channelId),
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_DISMISS_GROUP
                putExtra(
                    NotificationActionReceiver.EXTRA_ALERT_UNIQUE_IDS,
                    posted.map { it.uniqueId }.toTypedArray()
                )
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, summaryId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summary = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_poke_notification)
            .setContentTitle(label)
            .setStyle(inbox)
            .setGroup(channelId)
            .setGroupSummary(true)
            .setAutoCancel(true)
            // Silent: the child notifications already alerted, and a second buzz for the
            // summary would double every delivery.
            .setSilent(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    AlertNotificationIds.forSummaryAction("tap", channelId),
                    MainActivity.createAlertsIntent(context),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                R.drawable.ic_poke_notification,
                context.getString(R.string.notification_action_dismiss_all),
                dismissAll
            )
            .build()

        notificationManager.notify(summaryId, summary)
    }

    internal fun groupLabel(channelId: String, count: Int): String {
        val noun = when (channelId) {
            CHANNEL_RAIDS -> if (count == 1) "raid" else "raids"
            CHANNEL_SPAWNS -> if (count == 1) "spawn" else "spawns"
            CHANNEL_QUESTS -> if (count == 1) "quest" else "quests"
            else -> if (count == 1) "alert" else "alerts"
        }
        return "$count $noun"
    }

    private fun summaryLine(alert: PokemonAlert): String {
        val name = alert.pokemon?.takeIf { it.isNotBlank() } ?: alert.name
        val where = alert.gym?.takeIf { it.isNotBlank() }
            ?: alert.pokestop?.takeIf { it.isNotBlank() }
            ?: alert.area?.takeIf { it.isNotBlank() }
        return if (where == null) name else "$name • $where"
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
        val maxWalkingMinutes: Int,
        val quietHoursEnabled: Boolean,
        val quietHoursStartMinute: Int,
        val quietHoursEndMinute: Int,
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
        val goDexFilterEnabled: Boolean = false,
        val nowMillis: Long = System.currentTimeMillis(),
        val filterDefinition: FilterDefinition? = null
    ) {
        /**
         * Silenced by either mechanism: the one-off "quiet for N hours" timestamp, or the
         * standing nightly window. They are independent -- turning one off must not
         * override the other.
         */
        val isSilenced: Boolean
            get() = silenceUntil > nowMillis || isWithinQuietHours

        val isWithinQuietHours: Boolean
            get() = QuietHours.isQuiet(
                enabled = quietHoursEnabled,
                startMinute = quietHoursStartMinute,
                endMinute = quietHoursEndMinute,
                nowMillis = nowMillis
            )

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

        fun shouldNotify(
            alert: PokemonAlert,
            goDexStatus: GoDexMatchStatus = GoDexMatchStatus.NOT_CONFIGURED,
            matchContext: FilterMatchContext = FilterMatchContext()
        ): Boolean {
            if (!notificationsEnabled || isSilenced) return false
            filterDefinition?.let { definition ->
                if (!AlertFilterMatcher.matches(alert, definition, matchContext)) return false
                if (
                    goDexFilterEnabled &&
                    alert.hasType("hundo") &&
                    goDexStatus == GoDexMatchStatus.COLLECTED
                ) return false
                return true
            }
            return when {
                alert.hasTypeContaining("raid") -> {
                    raidsEnabled && raidTierAllowed(alert)
                }
                alert.hasTypeContaining("common") || alert.hasTypeContaining("rare") || alert.hasTypeContaining("spawn") -> {
                    spawnsEnabled &&
                        !isPokemonTypeExcluded(alert, excludedSpawnTypesLower) &&
                        isSpeciesAllowed(alert, allowedSpawnSpeciesLower)
                }
                alert.hasTypeContaining("quest") -> questsEnabled
                alert.hasType("hundo") -> {
                    hundosEnabled &&
                        !isPokemonTypeExcluded(alert, excludedHundoTypesLower) &&
                        if (goDexFilterEnabled) {
                            goDexStatus != GoDexMatchStatus.COLLECTED
                        } else {
                            isSpeciesAllowed(alert, allowedHundoSpeciesLower)
                        }
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
                    rocketEnabled && alert.gruntType?.lowercase(Locale.ROOT)?.let { it !in excludedRocketTypesLower } != false
                }
                else -> true
            }
        }

        private fun raidTierAllowed(alert: PokemonAlert): Boolean {
            val excluded = excludedRaidTiersLower
            if (excluded.isEmpty()) return true
            // RaidTierParser resolves "Elite", "Primal", "Ultra Beast" and shadow tiers that
            // the old `\d+|mega` regex never matched, which had left those exclusions dead.
            val tier = RaidTierParser.parse(alert) ?: return true
            return tier.displayLabel.lowercase(Locale.ROOT) !in excluded
        }

        private fun isPokemonTypeExcluded(alert: PokemonAlert, excludedSet: Set<String>): Boolean {
            if (excludedSet.isEmpty()) return false
            return alert.type?.any { it.lowercase(Locale.ROOT) in excludedSet } == true
        }

        private fun isSpeciesAllowed(alert: PokemonAlert, allowedSet: Set<String>): Boolean {
            if (allowedSet.isEmpty()) return true
            return (alert.pokemon ?: alert.name).lowercase(Locale.ROOT) in allowedSet
        }

        companion object {
            suspend fun load(preferences: AlertPreferencesStore): NotificationSettings {
                val filterDocument = preferences.filterStateDocument.first()
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
                    maxWalkingMinutes = preferences.maxWalkingMinutes.first(),
                    quietHoursEnabled = preferences.quietHoursEnabled.first(),
                    quietHoursStartMinute = preferences.quietHoursStartMinute.first(),
                    quietHoursEndMinute = preferences.quietHoursEndMinute.first(),
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
                    allowedSpawnSpecies = preferences.allowedSpawnSpecies.first(),
                    filterDefinition = filterDocument.notifications.resolve(filterDocument)
                )
            }
        }
    }

    private fun Set<String>.lowercaseSet(): Set<String> {
        return mapTo(LinkedHashSet(size)) { it.lowercase(Locale.ROOT) }
    }

    private fun goDexNotificationSuffix(alert: PokemonAlert, result: GoDexMatchResult): String {
        if (!alert.hasType("hundo")) return ""
        return when (result.status) {
            GoDexMatchStatus.NEEDED -> " \u2022 Needed in GoDex"
            GoDexMatchStatus.EVOLUTION_NEEDED ->
                " \u2022 Collected \u2022 Evolution needed: ${result.compactEvolutionLabel ?: "evolution"}"
            GoDexMatchStatus.FORM_CHANGE_NEEDED ->
                " \u2022 Collected \u2022 Form change needed: ${result.compactFormChangeLabel ?: "form"}"
            GoDexMatchStatus.EVOLUTION_AND_FORM_CHANGE_NEEDED ->
                " \u2022 Collected \u2022 Evolution needed: ${result.compactEvolutionLabel ?: "evolution"}" +
                    " \u2022 Form change needed: ${result.compactFormChangeLabel ?: "form"}"
            GoDexMatchStatus.COLLECTED -> " \u2022 Already collected in GoDex"
            GoDexMatchStatus.UNKNOWN -> " \u2022 GoDex form unknown"
            GoDexMatchStatus.NOT_CONFIGURED -> ""
        }
    }

    private fun immutableFlag(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0

    private fun createDismissPendingIntent(context: Context, alert: PokemonAlert): PendingIntent {
        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS
            putExtra(NotificationActionReceiver.EXTRA_ALERT_UNIQUE_ID, alert.uniqueId)
            putExtra(
                NotificationActionReceiver.EXTRA_NOTIFICATION_ID,
                AlertNotificationIds.forAlert(alert.uniqueId)
            )
        }
        return PendingIntent.getBroadcast(
            context,
            AlertNotificationIds.forAlertAction("dismiss", alert.uniqueId),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
    }

    private fun createPipPendingIntent(context: Context, alert: PokemonAlert): PendingIntent {
        val pipIntent = AlertDetailActivity.createIntent(
            context = context,
            alert = alert,
            returnToAlerts = true
        ).apply {
            putExtra(AlertDetailActivity.EXTRA_LAUNCH_PIP, true)
        }
        return PendingIntent.getActivity(
            context,
            AlertNotificationIds.forAlertAction("pip", alert.uniqueId),
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
