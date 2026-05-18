package com.example.pokemonalertsv2.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.AlarmManagerCompat
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.util.TimeUtils
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

object AlertSnoozeScheduler {
    private const val TAG = "AlertSnoozeScheduler"
    private const val REQUEST_CODE_OFFSET = 40_000
    private val json = Json { ignoreUnknownKeys = true }

    internal fun normalizedDurationMinutes(minutes: Int): Int {
        return minutes.coerceIn(1, 24 * 60)
    }

    internal fun calculateTriggerAt(
        nowMillis: Long,
        durationMinutes: Int,
        alertEndMillis: Long?
    ): Long? {
        if (alertEndMillis != null && alertEndMillis <= nowMillis) return null

        val triggerAt = nowMillis + TimeUnit.MINUTES.toMillis(
            normalizedDurationMinutes(durationMinutes).toLong()
        )
        return if (alertEndMillis != null && triggerAt >= alertEndMillis) null else triggerAt
    }

    fun isAlertActive(alert: PokemonAlert, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val endMillis = TimeUtils.parseEndTimeToMillis(alert.endTime)
        return endMillis == null || endMillis > nowMillis
    }

    fun schedule(
        context: Context,
        alert: PokemonAlert,
        durationMinutes: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val triggerAt = calculateTriggerAt(
            nowMillis = nowMillis,
            durationMinutes = durationMinutes,
            alertEndMillis = TimeUtils.parseEndTimeToMillis(alert.endTime)
        ) ?: return false

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return false
        val pendingIntent = createTriggerPendingIntent(context, alert)

        try {
            if (canScheduleExact(context, alarmManager)) {
                AlarmManagerCompat.setExactAndAllowWhileIdle(
                    alarmManager,
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            return true
        } catch (exception: SecurityException) {
            Log.w(TAG, "Exact snooze alarm denied; using inexact fallback.", exception)
            return runCatching {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                true
            }.getOrElse {
                Log.w(TAG, "Failed to schedule fallback snooze alarm.", it)
                false
            }
        }
    }

    fun encodeAlert(alert: PokemonAlert): String = json.encodeToString(alert)

    fun decodeAlert(alertJson: String): PokemonAlert? {
        return runCatching { json.decodeFromString<PokemonAlert>(alertJson) }.getOrNull()
    }

    private fun createTriggerPendingIntent(context: Context, alert: PokemonAlert): PendingIntent {
        val triggerIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_TRIGGER_SNOOZE
            putExtra(NotificationActionReceiver.EXTRA_ALERT_JSON, encodeAlert(alert))
        }
        return PendingIntent.getBroadcast(
            context,
            alert.uniqueId.hashCode() + REQUEST_CODE_OFFSET,
            triggerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
    }

    private fun canScheduleExact(context: Context, alarmManager: AlarmManager): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    private fun immutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }
}
