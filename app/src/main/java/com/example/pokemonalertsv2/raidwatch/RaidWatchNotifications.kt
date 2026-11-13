package com.example.pokemonalertsv2.raidwatch

import com.example.pokemonalertsv2.util.AppText
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.NotificationContentPreferences
import com.example.pokemonalertsv2.notifications.NotificationContentInput
import com.example.pokemonalertsv2.notifications.renderNotificationContent
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity
import com.example.pokemonalertsv2.ui.alerts.resolveAlertVisualStyle

/**
 * The raid Live Update shown after an "I'm going" journey reaches the gym.
 *
 * The shared content renderer applies the raid's collapsed and expanded field selections.
 * The chip defaults to the hundo catch CPs, and selected team rows lead the expanded body.
 * Native BigTextStyle supports the live status chip as well as the multi-line team.
 *
 * Built on the same Android 16 surfaces as
 * [com.example.pokemonalertsv2.tracking.ArrivalTrackingNotifications] --
 * setShortCriticalText and the android.requestPromotedOngoing extra -- with a
 * NotificationCompat fallback below API 36 that renders the identical body, just without the
 * Now Bar treatment.
 */
internal object RaidWatchNotifications {

    const val NOTIFICATION_ID = 40_060
    const val CHANNEL_ID = "raid_watch_ongoing"

    private const val REQUEST_STOP = 40_062
    private const val REQUEST_COUNTERS = 40_063
    private const val REQUEST_COPY = 40_065
    private const val REQUEST_STOP_FROM_SUMMARY = 40_066

    /** Android 16+ (API 36) exposes the Now Bar / live-update surfaces. */
    private const val LIVE_NOTIFICATION_MIN_SDK = 36

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Raid arrival",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hundo CP Live Update shown after arriving at a raid"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    fun build(
        context: Context,
        watch: WatchedRaid,
        team: RaidTeamSnapshot? = null,
        nowMillis: Long = System.currentTimeMillis(),
        largeIcon: Bitmap? = null,
        contentPreferences: NotificationContentPreferences = NotificationContentPreferences()
    ): Notification {
        val alert = watch.alert
        val title = bossTitle(alert)
        val rendered = renderNotificationContent(
            NotificationContentInput(
                alert = alert,
                // Raid Watch outlives location tracking, so it cannot assert current range.
                nowMillis = nowMillis,
                endMillis = watch.endMillis,
                raidWatch = true,
                teamText = buildTeamBody(team)
            ), contentPreferences
        )
        val body = rendered.expanded
        val summary = rendered.collapsed
        // A snapshot that came back with no team is the one case where there is genuinely
        // nothing to put on the clipboard. A missing snapshot only means "not yet", and the
        // action computes it on demand.
        val showCopy = team == null || team.hasTeam

        if (Build.VERSION.SDK_INT >= LIVE_NOTIFICATION_MIN_SDK) {
            return liveBuilder(context, alert)
                .setContentTitle(title)
                .setContentText(summary)
                .setContentIntent(openCountersPendingIntent(context, alert))
                .setShortCriticalText(rendered.chip)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .apply {
                    if (showCopy) {
                        addAction(
                            Notification.Action.Builder(
                                Icon.createWithResource(context, R.drawable.ic_content_copy),
                                "Copy team",
                                copyTeamPendingIntent(context)
                            ).build()
                        )
                    }
                }
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_close_small),
                        "Dismiss",
                        stopPendingIntent(context)
                    ).build()
                )
                .apply { largeIcon?.let { setLargeIcon(it) } }
                .build()
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_poke_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setColor(resolveAlertVisualStyle(alert).category.accentArgb.toInt())
            .setContentTitle(title)
            .setContentText(summary)
            .setContentIntent(openCountersPendingIntent(context, alert))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .apply {
                if (showCopy) {
                    addAction(
                        R.drawable.ic_content_copy,
                        "Copy team",
                        copyTeamPendingIntent(context)
                    )
                }
            }
            .addAction(R.drawable.ic_close_small, "Dismiss", stopPendingIntent(context))
            .apply { largeIcon?.let { setLargeIcon(it) } }
            .build()
    }

    /**
     * Suggested counter rows, passed as one selectable field to the content renderer.
     *
     * Every line here is fighting for room. A promoted ongoing notification is rendered at a
     * capped height in both the shade and the chip's popup, and the system truncates the body
     * to fit rather than letting it scroll -- measured on a Pixel 10 Pro at around four lines.
     * So there is no blank separator, and a Pokemon and its moveset share one line: the team is
     * only useful if all of it survives the cut.
     */
    internal fun buildTeamBody(team: RaidTeamSnapshot?): String {
        return when {
            team == null -> AppText.get(R.string.raidwatch_building_your_team)
            !team.hasTeam -> team.note ?: "No team to suggest for this boss."
            else -> team.members.mapIndexed { index, member ->
                "${index + 1}. ${member.displayName} ×${member.count}  ·  " +
                    "${member.fastMove} › ${member.chargedMove}"
            }.joinToString("\n")
        }
    }

    /** Boss name, falling back to the alert name when the species is not broken out. */
    internal fun bossTitle(alert: PokemonAlert): String =
        alert.pokemon?.takeIf { it.isNotBlank() }
            ?: alert.name.takeIf { it.isNotBlank() }
            ?: "Raid"

    @RequiresApi(LIVE_NOTIFICATION_MIN_SDK)
    private fun liveBuilder(context: Context, alert: PokemonAlert): Notification.Builder =
        Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_poke_notification)
            .setCategory(Notification.CATEGORY_EVENT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(resolveAlertVisualStyle(alert).category.accentArgb.toInt())
            .addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })

    /**
     * Lands on the counters screen rather than the detail page: someone watching a raid
     * already knows what it is and wants to know what to bring.
     */
    private fun openCountersPendingIntent(context: Context, alert: PokemonAlert): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_COUNTERS,
            AlertDetailActivity.createIntent(context, alert, returnToAlerts = true).apply {
                putExtra(AlertDetailActivity.EXTRA_OPEN_COUNTERS, true)
                putExtra(AlertDetailActivity.EXTRA_PREFER_PERSONAL_TEAM, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * Copies the team without opening anything: the target Activity is invisible and finishes
     * immediately, so the trainer stays in Pokémon GO.
     */
    private fun copyTeamPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_COPY,
            Intent(context, CopyRaidTeamActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun stopPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_STOP,
            Intent(context, RaidWatchReceiver::class.java).apply {
                action = RaidWatchReceiver.ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * The same stop, offered on the "Hunt finished" notification. Its own request code and
     * extra, so the receiver also re-posts that summary without the button it just used.
     */
    internal fun stopFromHuntSummaryPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_STOP_FROM_SUMMARY,
            Intent(context, RaidWatchReceiver::class.java).apply {
                action = RaidWatchReceiver.ACTION_STOP
                putExtra(RaidWatchReceiver.EXTRA_FROM_HUNT_SUMMARY, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
