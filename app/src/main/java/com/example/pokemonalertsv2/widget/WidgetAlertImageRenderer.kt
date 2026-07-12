package com.example.pokemonalertsv2.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.content.ContextCompat
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.pokemonalertsv2.PokemonAlertsApplication
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.util.MapFallbackImageGenerator

internal enum class WidgetAlertImageSource { REMOTE_IMAGE, MAP_THUMBNAIL, PLACEHOLDER }

internal fun widgetAlertImageSource(alert: PokemonAlert): WidgetAlertImageSource = when {
    !alert.imageUrl.isNullOrBlank() -> WidgetAlertImageSource.REMOTE_IMAGE
    alert.latitude != null && alert.longitude != null && !alert.thumbnailUrl.isNullOrBlank() ->
        WidgetAlertImageSource.MAP_THUMBNAIL
    else -> WidgetAlertImageSource.PLACEHOLDER
}

/** Shared image pipeline for collection rows and the expanded single-alert widget. */
internal object WidgetAlertImageRenderer {
    suspend fun render(context: Context, alert: PokemonAlert, sizeDp: Int): Bitmap {
        val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(40)
        val radiusPx = 12 * context.resources.displayMetrics.density
        return runCatching {
            when (widgetAlertImageSource(alert)) {
                WidgetAlertImageSource.REMOTE_IMAGE -> remoteImage(context, alert.imageUrl!!, sizePx, radiusPx)
                WidgetAlertImageSource.MAP_THUMBNAIL -> mapImage(context, alert, sizePx, radiusPx)
                WidgetAlertImageSource.PLACEHOLDER -> fallback(context, sizePx, radiusPx)
            }
        }.getOrElse { fallback(context, sizePx, radiusPx) }
    }

    private suspend fun remoteImage(context: Context, url: String, size: Int, radius: Float): Bitmap {
        val key = "image|$url|$size|$radius"
        cached(key)?.let { return it }
        val request = ImageRequest.Builder(context).data(url).size(size, size).allowHardware(false).build()
        val result = PokemonAlertsApplication.imageLoader(context).execute(request)
        val bitmap = if (result is SuccessResult) {
            val drawable = result.drawable
            roundBitmap(if (drawable is BitmapDrawable) drawable.bitmap else drawableToBitmap(drawable, size), radius)
        } else fallback(context, size, radius)
        return cache(key, bitmap)
    }

    private suspend fun mapImage(context: Context, alert: PokemonAlert, size: Int, radius: Float): Bitmap {
        val key = "map|${alert.latitude}|${alert.longitude}|${alert.thumbnailUrl}|$size|$radius"
        cached(key)?.let { return it }
        val bitmap = MapFallbackImageGenerator.generate(
            context, requireNotNull(alert.latitude), requireNotNull(alert.longitude),
            requireNotNull(alert.thumbnailUrl), size, size
        )?.let { roundBitmap(it, radius) } ?: fallback(context, size, radius)
        return cache(key, bitmap)
    }

    private fun fallback(context: Context, size: Int, radius: Float): Bitmap {
        val key = "fallback|$size|$radius"
        cached(key)?.let { return it }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        canvas.drawColor(ContextCompat.getColor(context, R.color.widget_fallback_background))
        canvas.drawCircle(center, center, size * 0.31f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.widget_primary_container)
        })
        ContextCompat.getDrawable(context, R.drawable.ic_placeholder)?.let { drawable ->
            val iconSize = (size * 0.50f).toInt()
            val inset = (size - iconSize) / 2
            drawable.setBounds(inset, inset, inset + iconSize, inset + iconSize)
            drawable.draw(canvas)
        }
        return cache(key, roundBitmap(bitmap, radius))
    }

    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawable.setBounds(0, 0, size, size)
            drawable.draw(Canvas(bitmap))
        }

    private fun roundBitmap(source: Bitmap, radius: Float): Bitmap =
        Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also { output ->
            val canvas = Canvas(output)
            val path = Path().apply {
                addRoundRect(RectF(0f, 0f, source.width.toFloat(), source.height.toFloat()), radius, radius, Path.Direction.CW)
            }
            canvas.clipPath(path)
            canvas.drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
        }

    private val bitmapCache = object : LruCache<String, Bitmap>(12 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && !oldValue.isRecycled) oldValue.recycle()
        }
    }

    private fun cached(key: String): Bitmap? = bitmapCache.get(key)?.takeUnless(Bitmap::isRecycled)
    private fun cache(key: String, bitmap: Bitmap): Bitmap = bitmap.also { bitmapCache.put(key, it) }
}
