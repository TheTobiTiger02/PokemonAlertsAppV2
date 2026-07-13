package com.example.pokemonalertsv2.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.location.Location
import android.util.LruCache
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity
import com.example.pokemonalertsv2.ui.alerts.buildAlertGlanceMetadata
import com.example.pokemonalertsv2.ui.alerts.formatAlertTitle
import com.example.pokemonalertsv2.ui.alerts.resolveAlertVisualStyle
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.WalkingRouteInfo
import com.example.pokemonalertsv2.util.WalkingRouteUtils
import com.example.pokemonalertsv2.util.validAlertCoordinates
import kotlinx.coroutines.runBlocking

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
    private var currentLocation: Location? = null
    private var walkingRoutes: Map<String, WalkingRouteInfo> = emptyMap()

    private var palette: WidgetThemePalette = WidgetThemePalette.Dark
    private val cornerRadiusPx by lazy { 12 * context.resources.displayMetrics.density }
    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        runBlocking {
            palette = resolveWidgetThemePalette(context)
            val renderSnapshot = WidgetAlertSnapshotStore.currentRenderSnapshot(appWidgetId)
                ?: WidgetAlertLoader.load(
                    context = context,
                    appWidgetId = appWidgetId,
                    fallbackLocation = currentLocation
                ).let {
                    WidgetAlertSnapshotStore.currentRenderSnapshot(appWidgetId)
                }
            currentLocation = renderSnapshot?.location ?: currentLocation

            val sorted = renderSnapshot.orEmpty().sortedWith(compareByDescending<PokemonAlert> {
                TimeUtils.parseEndTimeToMillis(it.endTime) ?: Long.MIN_VALUE
            }.thenByDescending { it.endTime })

            items.clear()
            items.addAll(sorted)
            walkingRoutes = currentLocation?.let { location ->
                WalkingRouteUtils.getWalkingRoutes(location, sorted)
            } ?: emptyMap()
        }
    }

    private fun WidgetAlertSnapshotStore.RenderSnapshot?.orEmpty(): List<PokemonAlert> =
        this?.alerts.orEmpty()

    override fun onDestroy() {
        items.clear()
        walkingRoutes = emptyMap()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        val alert = items.getOrNull(position) ?: return null
        val views = RemoteViews(context.packageName, R.layout.widget_alert_item).apply {
            applyItemWidgetPalette(palette)
        }
        val visualStyle = resolveAlertVisualStyle(alert)
        views.setInt(R.id.item_category_bar, "setBackgroundColor", visualStyle.category.accentArgb.toInt())

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
                    minutesLeft < 5 -> palette.error
                    minutesLeft < 15 -> palette.warning
                    else -> palette.primary
                }
                views.setTextColor(R.id.item_time, timeColor)
            } else {
                views.setTextViewText(R.id.item_time, "Ended")
                views.setTextColor(R.id.item_time, palette.onSurface)
            }
        } else {
            views.setTextViewText(R.id.item_time, "")
        }

        // 3. Description
        val type = alert.type?.firstOrNull() ?: "Alert"
        val usesMapFallback = alert.imageUrl.isNullOrBlank() && validAlertCoordinates(alert) != null
        val descriptionText = if (usesMapFallback) {
            alert.locationDisplay
                ?: alert.area?.takeIf { it.isNotBlank() }
                ?: alert.description.takeIf { it.isNotBlank() }
                ?: type
        } else {
            alert.description.takeIf { it.isNotBlank() }
                ?: alert.locationDisplay
                ?: type
        }
        views.setTextViewText(
            R.id.item_desc,
            if (descriptionText == type) type else "$type: $descriptionText"
        )

        // 4. Meta Data (Distance and walking time)
        val distanceMeters: Float? = currentLocation?.let { loc ->
            val latitude = alert.latitude
            val longitude = alert.longitude
            if (latitude == null || longitude == null) return@let null
            val results = FloatArray(1)
            runCatching {
                Location.distanceBetween(loc.latitude, loc.longitude, latitude, longitude, results)
            }.getOrNull()
            results.getOrNull(0)?.takeUnless { it.isNaN() }
        }
        val routeDisplayInfo = WalkingRouteUtils.buildRouteDisplayInfo(
            straightLineDistanceMeters = distanceMeters,
            routeInfo = walkingRoutes[alert.uniqueId]
        )
        val distanceText = routeDisplayInfo.distanceText
        val walkingText = routeDisplayInfo.walkingText

        val metadata = buildAlertGlanceMetadata(
            alert = alert,
            distanceText = distanceText,
            walkingText = walkingText,
            includeCategory = false,
            separator = " | "
        )
        if (metadata.isBlank()) {
            views.setViewVisibility(R.id.item_meta, View.GONE)
        } else {
            views.setViewVisibility(R.id.item_meta, View.VISIBLE)
            views.setTextViewText(R.id.item_meta, metadata)
        }

        // 5. Image loading is shared with the expanded single-alert layout.
        views.setImageViewBitmap(
            R.id.item_image,
            runBlocking { WidgetAlertImageRenderer.render(context, alert, sizeDp = 56, palette = palette) }
        )

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

    override fun getLoadingView(): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_alert_loading).apply {
            applyLoadingWidgetPalette(palette)
        }
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = items.getOrNull(position)?.uniqueId?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = true

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

        canvas.drawColor(palette.fallbackBackground)
        canvas.drawCircle(
            cx,
            cy,
            size * 0.31f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.fallbackAccent }
        )

        // Draw Pokéball icon from vector drawable
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_placeholder)
        if (drawable != null) {
            val iconSize = (size * 0.50f).toInt()
            val left = ((size - iconSize) / 2f).toInt()
            val top = ((size - iconSize) / 2f).toInt()
            drawable.setBounds(left, top, left + iconSize, top + iconSize)
            drawable.draw(canvas)
        }

        // Apply rounded corners to fallback too
        return roundBitmap(bmp, cornerRadiusPx)
    }

    private companion object {
        private val widgetBitmapCache = object : LruCache<String, Bitmap>(12 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024

            override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
                if (evicted && !oldValue.isRecycled) oldValue.recycle()
            }
        }

        private fun cachedBitmap(key: String, producer: () -> Bitmap): Bitmap {
            widgetBitmapCache.get(key)?.takeUnless { it.isRecycled }?.let { return it }
            return producer().also { bitmap ->
                widgetBitmapCache.put(key, bitmap)
            }
        }
    }
}
