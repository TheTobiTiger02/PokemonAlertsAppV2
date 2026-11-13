package com.example.pokemonalertsv2.raidwatch

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.pokemonalertsv2.PokemonAlertsApplication
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.work.RaidTeamPrefetchWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Starts, refreshes and tears down the single raid Live Update.
 *
 * A Live Update cannot tick itself, so the notification is re-posted from an exact alarm on
 * the cadence [RaidWatchTiming] decides. Deliberately not a foreground service: unlike
 * arrival tracking there is no location subscription to hold open, so an alarm that fires a
 * few times over a raid window is far cheaper than a service that lives for it.
 */
object RaidWatchController {

    private const val TAG = "RaidWatchController"
    private const val REQUEST_TICK = 40_064
    private val watchMutex = Mutex()

    /**
     * Begins watching [alert]. Returns false when the raid has no parseable end time or has
     * already ended, in which case nothing is posted.
     */
    suspend fun start(
        context: Context,
        alert: PokemonAlert,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val endMillis = TimeUtils.parseEndTimeToMillis(alert.endTime) ?: return false
        if (RaidWatchTiming.hasEnded(nowMillis, endMillis)) return false

        val appContext = context.applicationContext
        watchMutex.withLock { RaidWatchStore(appContext).save(alert, nowMillis, endMillis) }
        refresh(appContext, nowMillis)
        // Ranking the roster takes a Pokebattler round trip, so it cannot happen inline: the
        // arrival service stops itself the moment this returns. The worker re-posts the
        // notification once the team exists.
        RaidTeamPrefetchWorker.enqueue(appContext)
        return true
    }

    /** Stops the watch: cancels the alarm, drops the notification, clears the stored raid. */
    suspend fun stop(context: Context) {
        watchMutex.withLock { stopLocked(context.applicationContext) }
    }

    private suspend fun stopLocked(context: Context) {
        val appContext = context.applicationContext
        cancelTick(appContext)
        RaidTeamPrefetchWorker.cancel(appContext)
        NotificationManagerCompat.from(appContext)
            .cancel(RaidWatchNotifications.NOTIFICATION_ID)
        RaidWatchStore(appContext).clear()
    }

    /**
     * Stops the watch only when it is watching [alertUniqueId], and says whether it did.
     *
     * "Got it" retires one target. If a different raid is being watched -- the trainer
     * walked on and is now standing at another one -- that watch is still the right
     * thing on screen and must survive the catch.
     */
    suspend fun stopIfWatching(context: Context, alertUniqueId: String): Boolean {
        val appContext = context.applicationContext
        return watchMutex.withLock {
            val watched = RaidWatchStore(appContext).current()
            if (!isWatching(watched, alertUniqueId)) return@withLock false
            stopLocked(appContext)
            true
        }
    }

    internal fun isWatching(watched: WatchedRaid?, alertUniqueId: String): Boolean =
        watched?.alert?.uniqueId == alertUniqueId

    /**
     * Re-posts the notification for the currently watched raid and schedules the next tick,
     * or tears the watch down if the raid has ended. Safe to call with no watch active.
     */
    suspend fun refresh(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val refreshStartedAt = SystemClock.elapsedRealtime()
        val appContext = context.applicationContext
        val store = RaidWatchStore(appContext)
        val watch = store.current() ?: return

        val imageStyle = AlertPreferences(appContext.alertPreferencesDataStore).notificationContent.first()
            .imageStyle(com.example.pokemonalertsv2.data.FilterAlertType.RAID)
        val largeIcon = if (imageStyle == com.example.pokemonalertsv2.data.NotificationImageStyle.NONE) null
        else loadBossImage(appContext, watch.alert)
        // Only the team computed for this raid; a leftover from the previous one would put six
        // wrong Pokemon in front of someone about to start a lobby.
        watchMutex.withLock {
            // Image loading yields. Dismissal or replacement during it must win over this refresh.
            val current = store.current()
            if (current?.alert?.uniqueId != watch.alert.uniqueId || current.endMillis != watch.endMillis) return@withLock
            val refreshedAt = nowMillis + SystemClock.elapsedRealtime() - refreshStartedAt
            val delay = RaidWatchTiming.nextTickDelayMillis(refreshedAt, watch.endMillis)
            if (delay == null) {
                stopLocked(appContext)
                return@withLock
            }
            val team = store.currentTeam()?.takeIf { it.alertUniqueId == watch.alert.uniqueId }
            val content = AlertPreferences(appContext.alertPreferencesDataStore).notificationContent.first()
            RaidWatchNotifications.ensureChannel(appContext)
            val notification = RaidWatchNotifications.build(
                context = appContext,
                watch = current,
                team = team,
                nowMillis = refreshedAt,
                largeIcon = largeIcon,
                contentPreferences = content
            )
            if (hasNotificationPermission(appContext)) postNotification(appContext, notification)
            scheduleTick(appContext, refreshedAt + delay)
        }
    }

    private suspend fun loadBossImage(context: Context, alert: PokemonAlert) =
        withContext(Dispatchers.IO) {
            val url = alert.imageUrl?.takeIf { it.isNotBlank() }
                ?: alert.thumbnailUrl?.takeIf { it.isNotBlank() }
                ?: return@withContext null
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .scale(Scale.FIT)
                    // Hardware bitmaps cannot cross into a notification.
                    .allowHardware(false)
                    .build()
                val result = PokemonAlertsApplication.imageLoader(context).execute(request)
                (result as? SuccessResult)
                    ?.drawable
                    ?.let { it as? android.graphics.drawable.BitmapDrawable }
                    ?.bitmap
            }.getOrNull()
        }

    private fun scheduleTick(context: Context, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        val pendingIntent = tickPendingIntent(context)
        runCatching {
            if (canScheduleExact(context, alarmManager)) {
                AlarmManagerCompat.setExactAndAllowWhileIdle(
                    alarmManager,
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                // Without the exact-alarm permission the countdown just updates a little
                // late; that is much better than not updating at all.
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }.onFailure { Log.w(TAG, "Could not schedule raid watch tick", it) }
    }

    private fun cancelTick(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        runCatching { alarmManager.cancel(tickPendingIntent(context)) }
    }

    private fun tickPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_TICK,
            Intent(context, RaidWatchReceiver::class.java).apply {
                action = RaidWatchReceiver.ACTION_TICK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun canScheduleExact(context: Context, alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Called only after [hasNotificationPermission]; isolated for lint's flow boundary. */
    @SuppressLint("MissingPermission")
    private fun postNotification(context: Context, notification: Notification) {
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(RaidWatchNotifications.NOTIFICATION_ID, notification)
        }.onFailure { Log.w(TAG, "Could not post raid watch notification", it) }
    }
}
