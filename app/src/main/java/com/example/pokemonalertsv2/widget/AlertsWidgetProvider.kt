package com.example.pokemonalertsv2.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.AlarmManagerCompat
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.MainActivity
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
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

        val repo = PokemonAlertsRepository.create(context)
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { repo.fetchAlerts() }
                .onSuccess { alerts ->
                    val topThree = alerts.sortedByDescending { it.endTime }.take(3)
                    ids.forEach { id ->
                        val views = buildViews(context, topThree)
                        appWidgetManager.updateAppWidget(id, views)
                    }
                }
                .onFailure {
                    Log.w(TAG, "Widget update failed", it)
                }
            if (scheduleNext) scheduleNextUpdate(context)
        }
    }

    private suspend fun buildViews(context: Context, alerts: List<com.example.pokemonalertsv2.data.PokemonAlert>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_alerts)

        // Title/logo click opens app
        val openIntent = Intent(context, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
        )
        views.setOnClickPendingIntent(R.id.tv_title, openPending)
        views.setOnClickPendingIntent(R.id.img_logo, openPending)

        // Refresh button
        val refreshPending = PendingIntent.getBroadcast(
            context, 1, Intent(context, AlertsWidgetProvider::class.java).apply { action = ACTION_REFRESH },
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
        )
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPending)

        // Last updated
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        views.setTextViewText(R.id.tv_last_updated, context.getString(R.string.widget_updated_at, time))

        // Show rows or empty
        val rows = listOf(
            Triple(R.id.row1, R.id.row1_title, R.id.row1_desc),
            Triple(R.id.row2, R.id.row2_title, R.id.row2_desc),
            Triple(R.id.row3, R.id.row3_title, R.id.row3_desc)
        )

        if (alerts.isEmpty()) {
            views.setViewVisibility(R.id.tv_empty, android.view.View.VISIBLE)
            rows.forEach { (row, _, _) -> views.setViewVisibility(row, android.view.View.GONE) }
        } else {
            views.setViewVisibility(R.id.tv_empty, android.view.View.GONE)
            val imageLoader = ImageLoader(context)
            val sizePx = (40 * context.resources.displayMetrics.density).toInt().coerceAtLeast(32)
            rows.forEachIndexed { index, triple ->
                val (row, titleId, descId) = triple
                val alert = alerts.getOrNull(index)
                if (alert != null) {
                    views.setViewVisibility(row, android.view.View.VISIBLE)
                    views.setTextViewText(titleId, alert.name)
                    val endText = if (alert.endTime.isNotBlank()) context.getString(R.string.alert_end_time, alert.endTime) else ""
                    val desc = listOfNotNull(alert.type, endText.takeIf { it.isNotBlank() }).joinToString(" · ")
                    views.setTextViewText(descId, if (desc.isNotBlank()) desc else alert.description)

                    // Load image into row's ImageView using imageUrl from API
                    val imageUrl = alert.imageUrl
                    val imageViewId = when (index) {
                        0 -> R.id.row1_image
                        1 -> R.id.row2_image
                        else -> R.id.row3_image
                    }
                    if (!imageUrl.isNullOrBlank()) {
                        try {
                            val req = ImageRequest.Builder(context)
                                .data(imageUrl)
                                .size(sizePx, sizePx)
                                .allowHardware(false)
                                .build()
                            val result = imageLoader.execute(req)
                            if (result is SuccessResult) {
                                val drawable = result.drawable
                                val bmp = if (drawable is BitmapDrawable) drawable.bitmap else drawableToBitmap(drawable, sizePx)
                                views.setImageViewBitmap(imageViewId, bmp)
                            } else {
                                views.setImageViewResource(imageViewId, R.drawable.ic_placeholder)
                            }
                        } catch (_: Throwable) {
                            views.setImageViewResource(imageViewId, R.drawable.ic_placeholder)
                        }
                    } else {
                        views.setImageViewResource(imageViewId, R.drawable.ic_placeholder)
                    }

                    // Row click opens specific alert details
                    val detailIntent = AlertDetailActivity.createIntent(context, alert)
                    val requestCode = (alert.uniqueId.hashCode() + index) and 0x7FFFFFFF
                    val pending = PendingIntent.getActivity(
                        context,
                        requestCode,
                        detailIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
                    )
                    views.setOnClickPendingIntent(row, pending)
                } else {
                    views.setViewVisibility(row, android.view.View.GONE)
                }
            }
        }

        return views
    }

    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
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
