package com.example.pokemonalertsv2.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.LocationUtils
import kotlinx.coroutines.runBlocking
import java.util.Locale

class AlertsWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = AlertsFactory(applicationContext)
}

private class AlertsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private val items = mutableListOf<PokemonAlert>()
    private lateinit var imageLoader: ImageLoader
    private var currentLocation: Location? = null

    override fun onCreate() {
        imageLoader = ImageLoader(context)
    }

    override fun onDataSetChanged() {
        runBlocking {
            val repo = PokemonAlertsRepository.create(context)
            val alerts = runCatching { repo.fetchAlerts() }.getOrElse { emptyList() }
            // Try to actively get a fresh location fix with a short timeout for distance display
            currentLocation = runCatching { LocationUtils.getCurrentLocationOrNull(context, timeoutMs = 4000, highAccuracy = false) }.getOrNull()
            val sorted = alerts.sortedWith(compareByDescending<PokemonAlert> {
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
        views.setTextViewText(R.id.item_title, alert.name)
        val endMillis = TimeUtils.parseEndTimeToMillis(alert.endTime)
        val countdownText = endMillis?.let { ms ->
            val remaining = ms - System.currentTimeMillis()
            if (remaining > 0) context.getString(R.string.widget_countdown_format, TimeUtils.formatDurationShort(remaining)) else null
        }
        val distanceMeters: Float? = currentLocation?.let { loc ->
            val results = FloatArray(1)
            runCatching {
                Location.distanceBetween(loc.latitude, loc.longitude, alert.latitude, alert.longitude, results)
            }.getOrNull()
            results.getOrNull(0)?.takeUnless { it.isNaN() }
        }
        val distanceText = distanceMeters?.let { formatDistance(it) }
        val walkingText = distanceMeters?.let { formatWalkingTime(it) }
        val desc = listOfNotNull(distanceText, walkingText, alert.type, countdownText, alert.endTime.takeIf { it.isNotBlank() }?.let { context.getString(R.string.alert_end_time, it) })
            .joinToString(" · ")
        views.setTextViewText(R.id.item_desc, if (desc.isNotBlank()) desc else alert.description)

        val imgSize = (64 * context.resources.displayMetrics.density).toInt().coerceAtLeast(40)
        val imageUrl = alert.imageUrl
        if (!imageUrl.isNullOrBlank()) {
            try {
                val req = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(imgSize, imgSize)
                    .allowHardware(false)
                    .bitmapConfig(Bitmap.Config.ARGB_8888)
                    .build()
                val result = runBlocking { imageLoader.execute(req) }
                if (result is SuccessResult) {
                    val drawable = result.drawable
                    val bmp = if (drawable is BitmapDrawable) drawable.bitmap else drawableToBitmap(drawable, imgSize)
                    // Apply rounded corners manually since coil-transformations might be missing
                    val safeBmp = if (bmp.config == Bitmap.Config.HARDWARE) bmp.copy(Bitmap.Config.ARGB_8888, false) else bmp
                    val roundedBmp = getRoundedCornerBitmap(safeBmp, 24f)
                    views.setImageViewBitmap(R.id.item_image, roundedBmp)
                } else {
                    views.setImageViewResource(R.id.item_image, R.drawable.ic_placeholder)
                }
            } catch (_: Throwable) {
                views.setImageViewResource(R.id.item_image, R.drawable.ic_placeholder)
            }
        } else {
            views.setImageViewResource(R.id.item_image, R.drawable.ic_placeholder)
        }

        // Fill-in click intent to open details
        val fillInIntent = AlertDetailActivity.createIntent(context, alert)
        views.setOnClickFillInIntent(R.id.widget_item_root, fillInIntent)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = items.getOrNull(position)?.uniqueId?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bmp
    }

    private fun formatDistance(meters: Float): String {
        return if (meters >= 1000f) String.format(Locale.getDefault(), "%.1f km", meters / 1000f)
        else String.format(Locale.getDefault(), "%.0f m", meters)
    }

    private fun formatWalkingTime(meters: Float): String {
        // ~5 km/h walking speed (~83.33 m/min)
        val minutes = kotlin.math.ceil((meters / 83.333f).toDouble()).toInt().coerceAtLeast(1)
        return String.format(Locale.getDefault(), "%d min walk", minutes)
    }

    private fun getRoundedCornerBitmap(bitmap: Bitmap, pixels: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val color = -0xbdbdbe
        val paint = Paint()
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)

        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = color
        canvas.drawRoundRect(rectF, pixels, pixels, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)

        return output
    }
}
