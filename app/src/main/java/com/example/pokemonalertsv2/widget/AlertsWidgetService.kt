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
import android.util.LruCache
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.pokemonalertsv2.PokemonAlertsApplication
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity
import com.example.pokemonalertsv2.ui.alerts.formatAlertTitle
import com.example.pokemonalertsv2.util.CachedLocationProvider
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.WalkingRouteInfo
import com.example.pokemonalertsv2.util.WalkingRouteUtils
import kotlinx.coroutines.flow.first
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
    private lateinit var imageLoader: ImageLoader
    private var currentLocation: Location? = null
    private var walkingRoutes: Map<String, WalkingRouteInfo> = emptyMap()

    // Colors
    private val colorWhite by lazy { ContextCompat.getColor(context, R.color.poke_white) }
    private val colorRed by lazy { ContextCompat.getColor(context, R.color.poke_red) }
    private val colorYellow by lazy { ContextCompat.getColor(context, R.color.poke_yellow) }
    private val colorBlue by lazy { ContextCompat.getColor(context, R.color.poke_blue) }

    // Corner radius for image clipping (in pixels)
    private val cornerRadiusPx by lazy { (12 * context.resources.displayMetrics.density) }

    override fun onCreate() {
        imageLoader = PokemonAlertsApplication.imageLoader(context)
    }

    override fun onDataSetChanged() {
        runBlocking {
            val repo = PokemonAlertsRepository.create(context)
            val alerts = runCatching { repo.getLocalAlerts() }.getOrElse { emptyList() }
            val dismissedIds = runCatching { repo.alertPreferences.dismissedAlertIds.first() }.getOrElse { emptySet() }
            val selectedArea = runCatching { repo.alertPreferences.selectedArea.first() }.getOrElse { "All" }
            val maxDistance = runCatching { repo.alertPreferences.maxDistance.first() }.getOrElse { 0 }
            val filterTypes = WidgetFilterPrefs.getFilters(context, appWidgetId)
            val resolvedLocation = runCatching {
                CachedLocationProvider.get(context, timeoutMs = 4000, highAccuracy = false)
            }.getOrNull() ?: currentLocation

            currentLocation = resolvedLocation

            val filterResult = WidgetAlertFilter.filterAlerts(
                alerts = alerts,
                criteria = WidgetAlertFilter.Criteria(
                    dismissedAlertIds = dismissedIds,
                    selectedArea = selectedArea,
                    maxDistanceKm = maxDistance,
                    widgetFilterTypes = filterTypes
                ),
                origin = resolvedLocation?.let { WidgetAlertFilter.originFrom(it) }
            )

            if (filterResult is WidgetAlertFilter.Result.PreservePrevious) {
                return@runBlocking
            }

            val activeAlerts = (filterResult as WidgetAlertFilter.Result.Filtered).alerts
            val sorted = activeAlerts.sortedWith(compareByDescending<PokemonAlert> {
                TimeUtils.parseEndTimeToMillis(it.endTime) ?: Long.MIN_VALUE
            }.thenByDescending { it.endTime })

            items.clear()
            items.addAll(sorted)
            walkingRoutes = currentLocation?.let { location ->
                WalkingRouteUtils.getWalkingRoutes(location, sorted)
            } ?: emptyMap()
        }
    }

    override fun onDestroy() {
        items.clear()
        walkingRoutes = emptyMap()
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
        val descriptionText = alert.description
            .takeIf { it.isNotBlank() }
            ?: alert.locationDisplay
            ?: type
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

        val metaParts = listOfNotNull(distanceText, walkingText)
        if (metaParts.isEmpty()) {
            views.setViewVisibility(R.id.item_meta, View.GONE)
        } else {
            views.setViewVisibility(R.id.item_meta, View.VISIBLE)
            views.setTextViewText(R.id.item_meta, metaParts.joinToString(" | "))
        }

        // 5. Image Loading (with rounded corners)
        val imgSize = (56 * context.resources.displayMetrics.density).toInt().coerceAtLeast(40)
        val imageUrl = alert.imageUrl
        if (!imageUrl.isNullOrBlank()) {
            try {
                val bitmap = cachedBitmap("image|$imageUrl|$imgSize|$cornerRadiusPx") {
                    val req = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .size(imgSize, imgSize)
                        .allowHardware(false)
                        .build()
                    val result = runBlocking { imageLoader.execute(req) }
                    if (result is SuccessResult) {
                        val drawable = result.drawable
                        val bmp = if (drawable is BitmapDrawable) drawable.bitmap else drawableToBitmap(drawable, imgSize)
                        roundBitmap(bmp, cornerRadiusPx)
                    } else {
                        createFallbackBitmap(imgSize)
                    }
                }
                views.setImageViewBitmap(R.id.item_image, bitmap)
            } catch (_: Throwable) {
                views.setImageViewBitmap(R.id.item_image, cachedBitmap("fallback|$imgSize|$cornerRadiusPx") { createFallbackBitmap(imgSize) })
            }
        } else if (alert.latitude != null && alert.longitude != null && !alert.thumbnailUrl.isNullOrBlank()) {
            val latitude = alert.latitude
            val longitude = alert.longitude
            val thumbnailUrl = alert.thumbnailUrl
            val mapKey = "map|$latitude|$longitude|$thumbnailUrl|$imgSize|$cornerRadiusPx"
            val bitmap = cachedBitmap(mapKey) {
                val mapBmp = runBlocking {
                    com.example.pokemonalertsv2.util.MapFallbackImageGenerator.generate(
                        context = context,
                        latitude = latitude,
                        longitude = longitude,
                        thumbnailUrl = thumbnailUrl,
                        outputWidth = imgSize,
                        outputHeight = imgSize
                    )
                }
                mapBmp?.let { roundBitmap(it, cornerRadiusPx) } ?: createFallbackBitmap(imgSize)
            }
            views.setImageViewBitmap(R.id.item_image, bitmap)
        } else {
            views.setImageViewBitmap(R.id.item_image, cachedBitmap("fallback|$imgSize|$cornerRadiusPx") { createFallbackBitmap(imgSize) })
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
