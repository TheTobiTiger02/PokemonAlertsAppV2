package com.example.pokemonalertsv2.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity
import com.example.pokemonalertsv2.ui.alerts.displayCp
import com.example.pokemonalertsv2.ui.alerts.resolveAlertVisualStyle
import com.example.pokemonalertsv2.util.TimeUtils
import java.util.Locale
import kotlin.math.roundToInt

internal object ArrivalTrackingNotifications {
    const val ONGOING_NOTIFICATION_ID = 40_040
    private const val CHANNEL_ONGOING = "arrival_tracking_ongoing"
    private const val CHANNEL_ARRIVAL = "arrival_tracking_arrived"
    private const val REQUEST_STOP = 40_041
    private const val REQUEST_OPEN = 40_042
    private const val REQUEST_MAPS = 40_043

    /** Android 16+ (API 36) exposes the Now Bar / live-update surfaces. */
    private const val LIVE_NOTIFICATION_MIN_SDK = 36
    /** Assumed max journey distance for the progress bar, in meters. */
    private const val PROGRESS_MAX_METERS = 10_000f

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ONGOING,
                "Active alert journey",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the alert currently being approached"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ARRIVAL,
                "Alert arrival",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies you when an alert is within your selected range"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    fun restoring(context: Context): Notification =
        ongoingBuilder(context)
            .setContentTitle("Restoring alert journey")
            .setContentText("Waiting for the active destination")
            .build()

    fun ongoing(
        context: Context,
        destination: TrackedDestination,
        distanceMeters: Float? = null,
        waitingForPreciseLocation: Boolean = false
    ): Notification {
        val alert = destination.alert
        val content = when {
            waitingForPreciseLocation -> "Waiting for precise location"
            distanceMeters != null -> {
                "${formatDistance(distanceMeters)} away \u2022 Arrival at ${destination.radiusMeters} m"
            }
            else -> "Finding your precise location \u2022 Arrival at ${destination.radiusMeters} m"
        }
        val remaining = TimeUtils.parseEndTimeToMillis(alert.endTime)
            ?.minus(System.currentTimeMillis())
            ?.takeIf { it > 0L }
            ?.let { " \u2022 ${TimeUtils.formatDurationShort(it)} left" }
            .orEmpty()
        val title = ongoingTitle(alert)
        val expandedBody = buildExpandedBody(alert, content, remaining)
        val chip = when {
            waitingForPreciseLocation -> null
            distanceMeters != null -> formatDistance(distanceMeters)
            else -> null
        } ?: remaining.trim().takeIf { it.isNotBlank() }

        if (Build.VERSION.SDK_INT >= LIVE_NOTIFICATION_MIN_SDK) {
            return liveBuilder(context, alert)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(openAlertPendingIntent(context, alert))
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_poke_notification),
                        "Stop",
                        stopPendingIntent(context)
                    ).build()
                )
                .setStyle(
                    buildProgressStyle(
                        context = context,
                        alert = alert,
                        distanceMeters = distanceMeters,
                        waitingForPreciseLocation = waitingForPreciseLocation
                    )
                )
                .apply {
                    chip?.let { setShortCriticalText(it) }
                }
                .build()
        }
        return ongoingBuilder(context)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openAlertPendingIntent(context, alert))
            .addAction(
                R.drawable.ic_poke_notification,
                "Stop",
                stopPendingIntent(context)
            )
            .setRequestPromotedOngoing(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedBody))
            .build()
    }

    internal fun ongoingTitle(alert: PokemonAlert): String {
        val name = displayName(alert)
        val cp = alert.displayCp?.let { " \u2022 CP $it" }.orEmpty()
        return "Going to $name$cp"
    }

    internal fun buildExpandedBody(alert: PokemonAlert, content: String, remaining: String): String {
        val detail = buildList {
            remaining.trim().takeIf { it.isNotBlank() }?.let { add(it) }
            alert.displayCp?.let { add("CP $it") }
        }
        return if (detail.isEmpty()) content else "$content\n${detail.joinToString(" \u2022 ")}"
    }

    @RequiresApi(LIVE_NOTIFICATION_MIN_SDK)
    private fun buildProgressStyle(
        context: Context,
        alert: PokemonAlert,
        distanceMeters: Float?,
        waitingForPreciseLocation: Boolean
    ): Notification.ProgressStyle {
        val accent = resolveAlertVisualStyle(alert).category.accentArgb.toInt()
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressStartIcon(Icon.createWithResource(context, R.drawable.ic_my_location))
            .setProgressEndIcon(Icon.createWithResource(context, R.drawable.ic_poke_notification))
            .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_navigate))
            .addProgressPoint(
                Notification.ProgressStyle.Point(PROGRESS_MAX_METERS.roundToInt()).setColor(accent)
            )
        if (waitingForPreciseLocation || distanceMeters == null) {
            return style.setProgressIndeterminate(true)
        }
        val remaining = distanceMeters.coerceAtLeast(0f)
        val travelled = (PROGRESS_MAX_METERS - remaining).coerceAtLeast(0f)
        return style
            .addProgressSegment(
                Notification.ProgressStyle.Segment(PROGRESS_MAX_METERS.roundToInt())
                    .setColor(accent)
            )
            .setProgress(travelled.roundToInt())
    }

    @RequiresApi(LIVE_NOTIFICATION_MIN_SDK)
    private fun liveBuilder(context: Context, alert: PokemonAlert): Notification.Builder {
        val builder = Notification.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_poke_notification)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setColor(resolveAlertVisualStyle(alert).category.accentArgb.toInt())
        val extras = Bundle().apply {
            putBoolean("android.requestPromotedOngoing", true)
        }
        return builder.addExtras(extras)
    }

    fun postArrival(context: Context, destination: TrackedDestination) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val alert = destination.alert
        val title = "You\u2019re in range of ${displayName(alert)}"
        val body = buildArrivalBody(alert, destination.radiusMeters)
        val builder = NotificationCompat.Builder(context, CHANNEL_ARRIVAL)
            .setSmallIcon(R.drawable.ic_poke_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setColor(Color.GREEN)
            .setContentIntent(openAlertPendingIntent(context, alert))
            .addAction(R.drawable.ic_map, "Open Maps", mapsPendingIntent(context, alert))

        NotificationManagerCompat.from(context)
            .notify(alert.uniqueId.hashCode() xor 0x415252, builder.build())
    }

    internal fun buildArrivalBody(
        alert: PokemonAlert,
        radiusMeters: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val lead = when {
            alert.hasTypeContaining("raid") -> {
                val gym = alert.gym?.takeIf { it.isNotBlank() }
                if (gym != null) "You\u2019re at $gym." else "You\u2019re in range of the raid."
            }
            alert.hasTypeContaining("quest") -> {
                val stop = alert.pokestop?.takeIf { it.isNotBlank() }
                if (stop != null) "You\u2019re at $stop." else "You\u2019re in range of the Pok\u00e9Stop."
            }
            alert.pokemon != null ||
                alert.hasTypeContaining("hundo") ||
                alert.hasTypeContaining("spawn") ||
                alert.hasTypeContaining("pvp") -> {
                "It should now be visible in Pok\u00e9mon GO."
            }
            else -> "You\u2019re within $radiusMeters m of the alert."
        }

        val metadata = buildList {
            val exactCp = if (alert.isWeatherChange) alert.newCp else alert.cp
            exactCp?.takeIf { it > 0 }?.let { add("CP $it") }
            if (exactCp == null && alert.hasTypeContaining("raid")) {
                alert.hundoCP?.level20?.takeIf { it > 0 }?.let { add("100% L20 $it") }
                alert.hundoCP?.level25?.takeIf { it > 0 }?.let { add("100% L25 $it") }
            }
            val iv = if (alert.isWeatherChange) alert.newIv else alert.formattedIv
            iv?.takeIf { it.isNotBlank() }?.let { add("IV $it") }
            alert.pokemonForm?.takeIf { it.isNotBlank() }?.let(::add)
            if (alert.hasTypeContaining("quest")) {
                alert.questTask?.takeIf { it.isNotBlank() }?.let(::add)
                alert.questReward?.takeIf { it.isNotBlank() }?.let(::add)
            }
            TimeUtils.parseEndTimeToMillis(alert.endTime)
                ?.minus(nowMillis)
                ?.takeIf { it > 0L }
                ?.let { add("${TimeUtils.formatDurationShort(it)} left") }
        }
        return if (metadata.isEmpty()) lead else "$lead ${metadata.joinToString(" \u2022 ")}"
    }

    private fun ongoingBuilder(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_poke_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)

    private fun openAlertPendingIntent(context: Context, alert: PokemonAlert): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            AlertDetailActivity.createIntent(context, alert, returnToAlerts = true),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )

    private fun mapsPendingIntent(context: Context, alert: PokemonAlert): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_MAPS,
            Intent(Intent.ACTION_VIEW, alert.googleMapsUri),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )

    private fun stopPendingIntent(context: Context): PendingIntent =
        PendingIntent.getService(
            context,
            REQUEST_STOP,
            Intent(context, ArrivalTrackingService::class.java).apply {
                action = ArrivalTrackingService.ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )

    private fun displayName(alert: PokemonAlert): String =
        alert.pokemon?.takeIf { it.isNotBlank() }
            ?: alert.name.takeIf { it.isNotBlank() }
            ?: "alert"

    private fun formatDistance(distanceMeters: Float): String =
        if (distanceMeters < 1_000f) {
            "${distanceMeters.roundToInt()} m"
        } else {
            String.format(Locale.getDefault(), "%.1f km", distanceMeters / 1_000f)
        }

    private fun immutableFlag(): Int = PendingIntent.FLAG_IMMUTABLE
}
