package com.example.pokemonalertsv2.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.pokemonalertsv2.MainActivity
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.events.EventPreferences
import com.example.pokemonalertsv2.events.EventsRepository
import com.example.pokemonalertsv2.events.eventTypeName
import com.example.pokemonalertsv2.events.eventsToRemind
import java.util.concurrent.TimeUnit

/**
 * Plans event reminders: refreshes the calendar (falling back to the cached copy) and enqueues one
 * delayed notification per event the trainer wants reminding about, replacing earlier plans so a moved
 * event or a changed lead time is picked up. Reminders no longer wanted are cancelled.
 */
class EventReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = EventsRepository(applicationContext)
        val events = runCatching { repository.refresh() }.getOrElse { repository.cached() }
        val settings = EventPreferences(applicationContext).current()
        val now = System.currentTimeMillis()
        val reminders = eventsToRemind(events, settings.reminderTypes, settings.starredIds, settings.leadMinutes, now)
        val workManager = WorkManager.getInstance(applicationContext)
        val wanted = reminders.map { reminderName(it.event.id) }.toSet()
        // Cancel reminders for events no longer selected; the names are the only record of what was planned.
        events.map { reminderName(it.id) }.filter { it !in wanted }.forEach { workManager.cancelUniqueWork(it) }
        for (reminder in reminders) {
            val minutesBefore = ((reminder.event.startMillis - reminder.notifyAtMillis) / 60_000).toInt()
            val request = OneTimeWorkRequestBuilder<EventReminderNotifyWorker>()
                .setInitialDelay(reminder.notifyAtMillis - now, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(
                    EventReminderNotifyWorker.KEY_ID to reminder.event.id,
                    EventReminderNotifyWorker.KEY_TITLE to reminder.event.name,
                    EventReminderNotifyWorker.KEY_TEXT to "${eventTypeName(reminder.event.eventType)} starts " +
                        if (minutesBefore > 0) "in $minutesBefore min" else "now",
                ))
                .build()
            workManager.enqueueUniqueWork(reminderName(reminder.event.id), ExistingWorkPolicy.REPLACE, request)
        }
        return Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "event_reminders_periodic"
        private const val IMMEDIATE_NAME = "event_reminders_now"
        private fun reminderName(eventId: String) = "event-reminder-$eventId"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<EventReminderWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** Re-plans at once, e.g. after the trainer changed which events to be reminded of. */
        fun replan(context: Context) {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(IMMEDIATE_NAME, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<EventReminderWorker>().build())
        }
    }
}

/** Posts one event reminder; opening it lands on the Events tab. */
class EventReminderNotifyWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        val context = applicationContext
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        ensureChannel(context)
        val open = PendingIntent.getActivity(context, id.hashCode(), MainActivity.createEventsIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_poke_notification)
            .setContentTitle(inputData.getString(KEY_TITLE))
            .setContentText(inputData.getString(KEY_TEXT))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()
        NotificationManagerCompat.from(context).notify("event-reminder".hashCode() xor id.hashCode(), notification)
        return Result.success()
    }

    companion object {
        const val KEY_ID = "event_id"
        const val KEY_TITLE = "title"
        const val KEY_TEXT = "text"
        const val CHANNEL = "pokemon_alerts_event_reminders"

        fun ensureChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Event reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Reminders before Community Days, Spotlight Hours and other events you chose"
                }
            )
        }
    }
}
