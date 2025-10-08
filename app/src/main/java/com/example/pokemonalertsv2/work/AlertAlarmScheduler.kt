package com.example.pokemonalertsv2.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.AlarmManagerCompat
import java.util.concurrent.TimeUnit

object AlertAlarmScheduler {

    private const val TAG = "AlertAlarmScheduler"
    internal const val ACTION_POLL_ALERTS = "com.example.pokemonalertsv2.action.POLL_ALERTS"
    private const val REQUEST_CODE = 1001
    private val INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(10)

    fun prime(context: Context) {
        if (!canScheduleExact(context)) {
            Log.i(TAG, "Exact alarms unavailable; relying on WorkManager scheduling only.")
            return
        }
        scheduleNext(context)
    }

    fun onWorkFinished(context: Context) {
        if (!canScheduleExact(context)) return
        scheduleNext(context)
    }

    fun canScheduleExact(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun shouldPromptForPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExact(context)
    }

    fun createSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(pendingIntent(context))
    }

    fun scheduleNext(context: Context, delayMillis: Long = INTERVAL_MILLIS) {
        if (!canScheduleExact(context)) {
            Log.d(TAG, "Skipping exact alarm schedule; permission unavailable.")
            return
        }
        val clampedDelay = delayMillis.coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
        val triggerAtMillis = System.currentTimeMillis() + clampedDelay
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = pendingIntent(context)
        alarmManager.cancel(pendingIntent)
        AlarmManagerCompat.setExactAndAllowWhileIdle(
            alarmManager,
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
        Log.d(TAG, "Scheduled exact alarm in ${clampedDelay / 1000} seconds")
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlertAlarmReceiver::class.java).apply {
            action = ACTION_POLL_ALERTS
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
