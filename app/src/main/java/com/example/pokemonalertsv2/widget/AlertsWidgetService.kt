package com.example.pokemonalertsv2.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity
import com.example.pokemonalertsv2.ui.alerts.formatAlertTitle
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.LocationUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale

class AlertsWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        return AlertsFactory(applicationContext, widgetId)
    }
}

private class AlertsFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {
    private val items = mutableListOf<PokemonAlert>()
    private lateinit var imageLoader: ImageLoader
    private var currentLocation: Location? = null

    // Colors
    private val colorWhite by lazy { ContextCompat.getColor(context, R.color.poke_white) }
    private val colorRed by lazy { ContextCompat.getColor(context, R.color.poke_red) }
    private val colorYellow by lazy { ContextCompat.getColor(context, R.color.poke_yellow) }
    private val colorBlue by lazy { ContextCompat.getColor(context, R.color.poke_blue) }

    // Corner radius for image clipping (in pixels)
    private val cornerRadiusPx by lazy { (12 * context.resources.displayMetrics.density) }

    override fun onCreate() {
        imageLoader = ImageLoader(context)
    }

    override fun onDataSetChanged() {
        runBlocking {
            val repo = PokemonAlertsRepository.create(context)
            val alerts = runCatching { repo.getLocalAlerts() }.getOrElse { emptyList() }
            val dismissedIds = runCatching { repo.alertPreferences.dismissedAlertIds.first() }.getOrElse { emptySet() }

            // Short timeout for location
            currentLocation = runCatching { LocationUtils.getCurrentLocationOrNull(context, timeoutMs = 4000, highAccuracy = false) }.getOrNull()

            val selectedArea = runCatching { repo.alertPreferences.selectedArea.first() }.getOrElse { "All" }
            val maxDistance = runCatching { repo.alertPreferences.maxDistance.first() }.getOrElse { 0 }

            val now = System.currentTimeMillis()
            var activeAlerts = alerts.filter {
                val end = TimeUtils.parseEndTimeToMillis(it.endTime) ?: Long.MAX_VALUE
                val notExpired = end > now
                val notDismissed = it.uniqueId !in dismissedIds
                
                val areaMatch = selectedArea == "All" || it.area == selectedArea
                
                var distanceMatch = true
                if (maxDistance > 0 && currentLocation != null) {
                    val results = FloatArray(1)
                    Location.distanceBetween(currentLocation!!.latitude, currentLocation!!.longitude, it.latitude ?: 0.0, it.longitude ?: 0.0, results)
                    if (!results[0].isNaN() && results[0] > maxDistance * 1000) {
                        distanceMatch = false
                    }
                }
                
                notExpired && notDismissed && areaMatch && distanceMatch
            }

            // Apply per-widget type filters
            val filterTypes = WidgetFilterPrefs.getFilters(context, appWidgetId)
            if (filterTypes.isNotEmpty()) {
                activeAlerts = activeAlerts.filter { alert ->
                    val alertTypes = alert.type ?: emptyList()
                    if (alertTypes.isEmpty()) true // Show alerts with no type
                    else alertTypes.any { type -> filterTypes.any { filter -> type.contains(filter, ignoreCase = true) } }
                }
            }

            val sorted = activeAlerts.sortedWith(compareByDescending<PokemonAlert> {
                TimeUtils.parseEndTimeToMillis(it.endTime) ?: Long.MIN_VALUE
            }.thenByDescending { it.endTime })

            items.clear()
            items.addAll(sorted)
        }
    }

    override fun onDestroy() {
        items.clear()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        val alert = items.getOrNull(position) ?: return null
        val views = RemoteViews(context.packageName, R.layout.widget_alert_item)

        // 1. Title
        views.setTextViewText(R.id.item_title, formatAlertTitle(alert))

        // 2. Time Logic & Coloring
        val endMillis = TimeUtils.parseEndTimeToMillis(alert.endTime)
        if (endMillis != null) {
            val remaining = endMillis - System.currentTimeMillis()
            if (remaining > 0) {
                val timeStr = TimeUtils.formatDurationShort(remaining)
                views.setTextViewText(R.id.item_time, timeStr)

                // Color coding for urgency
                val minutesLeft = remaining / 1000 / 60
                val timeColor = when {
                    minutesLeft < 5 -> colorRed
                    minutesLeft < 15 -> colorYellow
                    else -> colorBlue
                }
                views.setTextColor(R.id.item_time, timeColor)
            } else {
                views.setTextViewText(R.id.item_time, "Ended")
                views.setTextColor(R.id.item_time, colorWhite)
            }
        } else {
            views.setTextViewText(R.id.item_time, "")
        }

        // 3. Description
        val type = alert.type?.firstOrNull() ?: "Alert"
        views.setTextViewText(R.id.item_desc, "$type: ${alert.description}")

        // 4. Meta Data (Distance and walking time)
        val distanceMeters: Float? = currentLocation?.let { loc ->
            val results = FloatArray(1)
            runCatching {
                Location.distanceBetween(loc.latitude, loc.longitude, alert.latitude ?: 0.0, alert.longitude ?: 0.0, results)
            }.getOrNull()
            results.getOrNull(0)?.takeUnless { it.isNaN() }
        }
        val distanceText = distanceMeters?.let { formatDistance(it) }
        val walkingText = distanceMeters?.let { formatWalkingTime(it) }

        val metaParts = listOfNotNull(distanceText, walkingText)
        views.setTextViewText(R.id.item_meta, metaParts.joinToString(" • "))

        // 5. Image Loading (with rounded corners)
        val imgSize = (56 * context.resources.displayMetrics.density).toInt().coerceAtLeast(40)
        val imageUrl = alert.imageUrl
        if (!imageUrl.isNullOrBlank()) {
            try {
                val req = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(imgSize, imgSize)
                    .allowHardware(false)
                    .build()
                val result = runBlocking { imageLoader.execute(req) }
                if (result is SuccessResult) {
                    val drawable = result.drawable
                    val bmp = if (drawable is BitmapDrawable) drawable.bitmap else drawableToBitmap(drawable, imgSize)
                    views.setImageViewBitmap(R.id.item_image, roundBitmap(bmp, cornerRadiusPx))
                } else {
                    views.setImageViewBitmap(R.id.item_image, createFallbackBitmap(imgSize))
                }
            } catch (_: Throwable) {
                views.setImageViewBitmap(R.id.item_image, createFallbackBitmap(imgSize))
            }
        } else if (alert.latitude != null && alert.longitude != null && !alert.thumbnailUrl.isNullOrBlank()) {
            val mapBmp = runBlocking {
                com.example.pokemonalertsv2.util.MapFallbackImageGenerator.generate(
                    context = context,
                    latitude = alert.latitude,
                    longitude = alert.longitude,
                    thumbnailUrl = alert.thumbnailUrl,
                    outputWidth = imgSize,
                    outputHeight = imgSize
                )
            }
            views.setImageViewBitmap(R.id.item_image, roundBitmap(mapBmp ?: createFallbackBitmap(imgSize), cornerRadiusPx))
        } else {
            views.setImageViewBitmap(R.id.item_image, createFallbackBitmap(imgSize))
        }

        // 6. Navigate button — show only if we have coordinates
        if (alert.latitude != null && alert.longitude != null) {
            views.setViewVisibility(R.id.btn_navigate, View.VISIBLE)
            val navIntent = Intent().apply {
                action = AlertsWidgetProvider.ACTION_NAVIGATE
                putExtra(AlertsWidgetProvider.EXTRA_NAV_LAT, alert.latitude)
                putExtra(AlertsWidgetProvider.EXTRA_NAV_LNG, alert.longitude)
            }
            views.setOnClickFillInIntent(R.id.btn_navigate, navIntent)
        } else {
            views.setViewVisibility(R.id.btn_navigate, View.GONE)
        }

        // 7. Dismiss button
        val dismissIntent = Intent().apply {
            action = AlertsWidgetProvider.ACTION_DISMISS_WIDGET
            putExtra(AlertsWidgetProvider.EXTRA_DISMISS_ALERT_ID, alert.uniqueId)
        }
        views.setOnClickFillInIntent(R.id.btn_dismiss, dismissIntent)

        // 8. Root layout and Image click → open AlertDetailActivity (via broadcast to provider)
        val detailExtras = AlertDetailActivity.createIntent(context, alert)
        val fillInIntent = Intent().apply {
            action = AlertsWidgetProvider.ACTION_ITEM_CLICK
            putExtras(detailExtras)
        }
        views.setOnClickFillInIntent(R.id.item_root, fillInIntent)
        views.setOnClickFillInIntent(R.id.item_image, fillInIntent)
        views.setOnClickFillInIntent(R.id.text_container, fillInIntent)
        views.setOnClickFillInIntent(R.id.item_title, fillInIntent)
        views.setOnClickFillInIntent(R.id.item_desc, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = items.getOrNull(position)?.uniqueId?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = true

    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bmp
    }

    /** Clips a bitmap to rounded corners. */
    private fun roundBitmap(source: Bitmap, radius: Float): Bitmap {
        val w = source.width
        val h = source.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()
        path.addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, Path.Direction.CW)
        canvas.clipPath(path)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    /** Dark background + gold radial glow + Pokéball icon fallback for the widget. */
    private fun createFallbackBitmap(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = size / 2f
        val cy = size / 2f

        // Dark background
        canvas.drawColor(0xFF1A1A2E.toInt())

        // Gold radial glow
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val glowRadius = size * 0.55f
        glowPaint.shader = RadialGradient(
            cx, cy, glowRadius,
            intArrayOf(0x66FFD700.toInt(), 0x26FFD700.toInt(), 0x00000000),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, glowRadius, glowPaint)

        // Draw Pokéball icon from vector drawable
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_placeholder)
        if (drawable != null) {
            val iconSize = (size * 0.50f).toInt()
            val left = ((size - iconSize) / 2f).toInt()
            val top = ((size - iconSize) / 2f).toInt()
            drawable.setBounds(left, top, left + iconSize, top + iconSize)
            drawable.setTint(0xB3FFFFFF.toInt()) // White at ~70% opacity
            drawable.draw(canvas)
        }

        // Apply rounded corners to fallback too
        return roundBitmap(bmp, cornerRadiusPx)
    }

    private fun formatDistance(meters: Float): String {
        return if (meters >= 1000f) String.format(Locale.getDefault(), "%.1fkm", meters / 1000f)
        else String.format(Locale.getDefault(), "%.0fm", meters)
    }

    private fun formatWalkingTime(meters: Float): String {
        val minutes = kotlin.math.ceil((meters / 83.333f).toDouble()).toInt().coerceAtLeast(1)
        return "$minutes min walk"
    }
}