package com.example.pokemonalertsv2.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.location.Location
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
import kotlinx.coroutines.runBlocking
import java.util.Locale

class AlertsWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = AlertsFactory(applicationContext)
}

private class AlertsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private val items = mutableListOf<PokemonAlert>()
    private lateinit var imageLoader: ImageLoader
    private var currentLocation: Location? = null

    // Colors
    private val colorWhite by lazy { ContextCompat.getColor(context, R.color.poke_white) }
    private val colorRed by lazy { ContextCompat.getColor(context, R.color.poke_red) }
    private val colorYellow by lazy { ContextCompat.getColor(context, R.color.poke_yellow) }
    private val colorBlue by lazy { ContextCompat.getColor(context, R.color.poke_blue) }

    override fun onCreate() {
        imageLoader = ImageLoader(context)
    }

    override fun onDataSetChanged() {
        runBlocking {
            val repo = PokemonAlertsRepository.create(context)
            val alerts = runCatching { repo.getLocalAlerts() }.getOrElse { emptyList() }

            // Short timeout for location
            currentLocation = runCatching { LocationUtils.getCurrentLocationOrNull(context, timeoutMs = 4000, highAccuracy = false) }.getOrNull()

            val now = System.currentTimeMillis()
            val activeAlerts = alerts.filter {
                val end = TimeUtils.parseEndTimeToMillis(it.endTime) ?: Long.MAX_VALUE
                end > now
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

        // 3. Description (now cleaner)
        val type = alert.type ?: "Alert"
        views.setTextViewText(R.id.item_desc, "$type: ${alert.description}")

        // 4. Meta Data (Distance and specifics)
        val distanceMeters: Float? = currentLocation?.let { loc ->
            val results = FloatArray(1)
            runCatching {
                Location.distanceBetween(loc.latitude, loc.longitude, alert.latitude ?: 0.0, alert.longitude ?: 0.0, results)
            }.getOrNull()
            results.getOrNull(0)?.takeUnless { it.isNaN() }
        }
        val distanceText = distanceMeters?.let { formatDistance(it) }
        val walkingText = distanceMeters?.let { formatWalkingTime(it) }

        val metaParts = listOfNotNull(
            distanceText,
            walkingText
        )
        views.setTextViewText(R.id.item_meta, metaParts.joinToString(" • "))

        // 5. Image Loading
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
                    views.setImageViewBitmap(R.id.item_image, bmp)
                } else {
                    views.setImageViewResource(R.id.item_image, R.drawable.ic_placeholder)
                }
            } catch (_: Throwable) {
                views.setImageViewResource(R.id.item_image, R.drawable.ic_placeholder)
            }
        } else {
            views.setImageViewResource(R.id.item_image, R.drawable.ic_placeholder)
        }

        // Fill-in click intent
        val fillInIntent = AlertDetailActivity.createIntent(context, alert)
        views.setOnClickFillInIntent(R.id.item_image, fillInIntent)
        views.setOnClickFillInIntent(R.id.item_title, fillInIntent)
        views.setOnClickFillInIntent(R.id.item_desc, fillInIntent) // Make whole card clickable usually

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
        return if (meters >= 1000f) String.format(Locale.getDefault(), "%.1fkm", meters / 1000f)
        else String.format(Locale.getDefault(), "%.0fm", meters)
    }

    private fun formatWalkingTime(meters: Float): String {
        val minutes = kotlin.math.ceil((meters / 83.333f).toDouble()).toInt().coerceAtLeast(1)
        return "$minutes min walk"
    }
}