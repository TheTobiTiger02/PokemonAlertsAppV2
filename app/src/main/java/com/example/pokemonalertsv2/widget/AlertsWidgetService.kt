package com.example.pokemonalertsv2.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity
import com.example.pokemonalertsv2.util.TimeUtils
import kotlinx.coroutines.runBlocking
import java.util.Locale

class AlertsWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = AlertsFactory(applicationContext)
}

private class AlertsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private val items = mutableListOf<PokemonAlert>()
    private lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        imageLoader = ImageLoader(context)
    }

    override fun onDataSetChanged() {
        runBlocking {
            val repo = PokemonAlertsRepository.create(context)
            val alerts = runCatching { repo.fetchAlerts() }.getOrElse { emptyList() }
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
        val desc = listOfNotNull(alert.type, countdownText, alert.endTime.takeIf { it.isNotBlank() }?.let { context.getString(R.string.alert_end_time, it) })
            .joinToString(" · ")
        views.setTextViewText(R.id.item_desc, if (desc.isNotBlank()) desc else alert.description)

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

        // Fill-in click intent to open details
        val fillInIntent = AlertDetailActivity.createIntent(context, alert)
        views.setOnClickFillInIntent(R.id.item_title, fillInIntent)
        views.setOnClickFillInIntent(R.id.item_desc, fillInIntent)
        views.setOnClickFillInIntent(R.id.item_image, fillInIntent)
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
}
