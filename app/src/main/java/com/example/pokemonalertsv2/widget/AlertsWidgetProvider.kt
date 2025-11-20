package com.example.pokemonalertsv2.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.AlarmManagerCompat
import com.example.pokemonalertsv2.MainActivity
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AlertsWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleNextUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelScheduledUpdate(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        updateAll(context)
        scheduleNextUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH, ACTION_TIMER_TICK -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        updateAll(context, scheduleNext = true)
                    } finally {
                        try { pending.finish() } catch (_: Throwable) {}
                    }
                }
            }
        }
    }

    private fun updateAll(context: Context, scheduleNext: Boolean = false) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, AlertsWidgetProvider::class.java))
        if (ids.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                ids.forEach { id ->
                    val views = buildViews(context, id)
                    appWidgetManager.updateAppWidget(id, views)
                }
                appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.list_alerts)
            } catch (t: Throwable) {
                Log.w(TAG, "Widget update failed", t)
            }
            if (scheduleNext) scheduleNextUpdate(context)
        }
    }

    private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_alerts)

        // Title/logo click opens app
        val openIntent = Intent(context, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
        )
        views.setOnClickPendingIntent(R.id.tv_title, openPending)
        views.setOnClickPendingIntent(R.id.img_logo, openPending)

        // Set logo tint programmatically for better compatibility
        views.setInt(R.id.img_logo, "setColorFilter", context.getColor(R.color.pokemon_red_dark))

        // Refresh button
        val refreshPending = PendingIntent.getBroadcast(
            context, 1, Intent(context, AlertsWidgetProvider::class.java).apply { action = ACTION_REFRESH },
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
        )
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPending)

        // Last updated
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        views.setTextViewText(R.id.tv_last_updated, context.getString(R.string.widget_updated_at, time))

        // Empty view
        views.setEmptyView(R.id.list_alerts, R.id.tv_empty)

        // Remote adapter with unique data per widget id
        val svcIntent = Intent(context, AlertsWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.list_alerts, svcIntent)

        // PendingIntent template; items supply fill-in intents
        val pendingTemplate = PendingIntent.getActivity(
            context,
            0,
            Intent(context, AlertDetailActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
        )
        views.setPendingIntentTemplate(R.id.list_alerts, pendingTemplate)

        return views
    }

    private fun scheduleNextUpdate(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlertsWidgetProvider::class.java).apply { action = ACTION_TIMER_TICK }
        val pi = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, PendingIntent.FLAG_CANCEL_CURRENT or mutableFlag())
        val triggerAt = System.currentTimeMillis() + UPDATE_INTERVAL_MS
        AlarmManagerCompat.setExactAndAllowWhileIdle(am, AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    private fun cancelScheduledUpdate(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlertsWidgetProvider::class.java).apply { action = ACTION_TIMER_TICK }
        val pi = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE or mutableFlag())
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
        }
    }

    companion object {
        private const val TAG = "AlertsWidget"
        private const val ACTION_REFRESH = "com.example.pokemonalertsv2.widget.ACTION_REFRESH"
        private const val ACTION_TIMER_TICK = "com.example.pokemonalertsv2.widget.ACTION_TIMER_TICK"
        private const val REQUEST_CODE = 2025
        private val UPDATE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(30)

        private fun mutableFlag(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

        fun requestUpdate(context: Context) {
            // Public API to refresh all widgets and schedule next tick
            context.sendBroadcast(Intent(context, AlertsWidgetProvider::class.java).apply { action = ACTION_REFRESH })
        }

        fun updateFromWorker(context: Context) {
            // Called after AlertWorker completes a sync; update widgets now
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, AlertsWidgetProvider::class.java))
            if (ids.isEmpty()) return
            // Delegate to provider by broadcasting refresh (keeps logic in one place)
            requestUpdate(context)
        }
    }
}
