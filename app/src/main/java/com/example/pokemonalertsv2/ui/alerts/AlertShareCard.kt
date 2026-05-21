package com.example.pokemonalertsv2.ui.alerts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.pokemonalertsv2.PokemonAlertsApplication
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.util.MapFallbackImageGenerator
import com.example.pokemonalertsv2.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.roundToInt

object AlertShareCard {
    private const val CARD_WIDTH = 1080
    private const val CARD_HEIGHT = 1520
    private const val PADDING = 64f
    private const val HERO_HEIGHT = 650f

    data class ShareStat(val label: String, val value: String)

    data class ShareCardContent(
        val title: String,
        val subtitle: String?,
        val tags: List<String>,
        val stats: List<ShareStat>,
        val endLabel: String,
        val locationLabel: String?,
        val mapsUrl: String?
    )

    internal fun buildShareCardContent(
        alert: PokemonAlert,
        nowMillis: Long = System.currentTimeMillis()
    ): ShareCardContent {
        val stats = buildList {
            val displayIv = if (alert.isWeatherChange && alert.newIv != null) alert.newIv else alert.formattedIv
            val displayCp = if (alert.isWeatherChange && alert.newCp != null) alert.newCp.toString() else alert.cp?.toString()
            displayIv?.takeIf { it.isNotBlank() }?.let { add(ShareStat("IV", it)) }
            displayCp?.takeIf { it.isNotBlank() }?.let { add(ShareStat("CP", it)) }
            alert.level?.let {
                val level = if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
                add(ShareStat("Level", level))
            }
            bestPvpLabel(alert)?.let { add(ShareStat("PvP", it)) }
        }.take(4)

        val tags = buildList {
            alert.type?.filter { it.isNotBlank() }?.take(4)?.forEach { add(it.uppercase(Locale.getDefault())) }
            if (alert.isWeatherBoosted == true) add("BOOSTED")
            if (alert.isShiny == true) add("SHINY")
        }.distinct().take(5)

        val endMillis = TimeUtils.parseEndTimeToMillis(alert.endTime)
        val endLabel = when {
            endMillis == null -> "Ends: ${alert.endTime.ifBlank { "unknown" }}"
            endMillis <= nowMillis -> "Expired"
            else -> "Ends in ${TimeUtils.formatDurationShort(endMillis - nowMillis)}"
        }

        return ShareCardContent(
            title = formatAlertTitle(alert),
            subtitle = alert.pokemonForm?.takeIf { it.isNotBlank() }
                ?: alert.area?.takeIf { it.isNotBlank() },
            tags = tags,
            stats = stats,
            endLabel = endLabel,
            locationLabel = alert.locationDisplay ?: alert.area,
            mapsUrl = mapsUrl(alert)
        )
    }

    internal fun buildShareText(content: ShareCardContent): String {
        return buildString {
            append("Pokemon Alert: ${content.title}\n")
            content.subtitle?.let { append("$it\n") }
            if (content.stats.isNotEmpty()) {
                append(content.stats.joinToString(" | ") { "${it.label}: ${it.value}" })
                append('\n')
            }
            append(content.endLabel)
            content.locationLabel?.let { append("\nLocation: $it") }
            content.mapsUrl?.let { append("\nMaps: $it") }
        }
    }

    suspend fun share(context: Context, alert: PokemonAlert): Boolean {
        val content = buildShareCardContent(alert)
        val uri = withContext(Dispatchers.IO) {
            runCatching { createShareCardUri(context.applicationContext, alert, content) }.getOrNull()
        }
        val text = buildShareText(content)

        val intent = if (uri != null) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Pokemon Alert: ${content.title}")
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Pokemon Alert: ${content.title}")
                putExtra(Intent.EXTRA_TEXT, text)
            }
        }

        return withContext(Dispatchers.Main) {
            runCatching {
                val chooser = Intent.createChooser(intent, "Share Alert")
                if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                true
            }.getOrDefault(false)
        }
    }

    private suspend fun createShareCardUri(
        context: Context,
        alert: PokemonAlert,
        content: ShareCardContent
    ): Uri {
        val bitmap = createShareBitmap(context, alert, content)
        val dir = File(context.cacheDir, "shared_alerts").apply { mkdirs() }
        val file = File(dir, "alert_share_${alert.uniqueId.hashCode()}.png")
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private suspend fun createShareBitmap(
        context: Context,
        alert: PokemonAlert,
        content: ShareCardContent
    ): Bitmap {
        val heroBitmap = loadHeroBitmap(context, alert)
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(canvas)
        drawHero(canvas, heroBitmap)
        heroBitmap?.recycle()
        drawContent(canvas, content)
        return bitmap
    }

    private suspend fun loadHeroBitmap(context: Context, alert: PokemonAlert): Bitmap? {
        val imageLoader = PokemonAlertsApplication.imageLoader(context)
        val primary = loadBitmap(imageLoader, context, alert.imageUrl)
        if (primary != null) return primary

        val thumbnail = loadBitmap(imageLoader, context, alert.thumbnailUrl)
        if (thumbnail != null) return thumbnail

        val lat = alert.latitude
        val lon = alert.longitude
        if (lat != null && lon != null) {
            MapFallbackImageGenerator.generate(
                context = context,
                latitude = lat,
                longitude = lon,
                thumbnailUrl = alert.thumbnailUrl,
                outputWidth = CARD_WIDTH,
                outputHeight = HERO_HEIGHT.roundToInt()
            )?.let { return it }
        }

        return null
    }

    private suspend fun loadBitmap(
        imageLoader: ImageLoader,
        context: Context,
        url: String?
    ): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(CARD_WIDTH, HERO_HEIGHT.roundToInt())
                .build()
            val result = imageLoader.execute(request)
            if (result is SuccessResult) {
                val drawable = result.drawable
                if (drawable is BitmapDrawable) {
                    drawable.bitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    val width = drawable.intrinsicWidth.coerceAtLeast(1)
                    val height = drawable.intrinsicHeight.coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, width, height)
                    drawable.draw(canvas)
                    bitmap
                }
            } else {
                null
            }
        }.getOrNull()
    }

    private fun drawBackground(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                CARD_WIDTH.toFloat(),
                CARD_HEIGHT.toFloat(),
                intArrayOf(Color.rgb(7, 11, 28), Color.rgb(22, 32, 68), Color.rgb(44, 26, 78)),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), paint)
    }

    private fun drawHero(canvas: Canvas, heroBitmap: Bitmap?) {
        val heroRect = RectF(0f, 0f, CARD_WIDTH.toFloat(), HERO_HEIGHT)
        if (heroBitmap != null) {
            val src = centerCropRect(heroBitmap.width, heroBitmap.height, CARD_WIDTH, HERO_HEIGHT.roundToInt())
            canvas.drawBitmap(heroBitmap, src, heroRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    CARD_WIDTH.toFloat(),
                    HERO_HEIGHT,
                    Color.rgb(28, 37, 75),
                    Color.rgb(111, 75, 255),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(heroRect, paint)
            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(150, 255, 255, 255)
                textSize = 180f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText("Pokemon", CARD_WIDTH / 2f, HERO_HEIGHT / 2f, iconPaint)
        }

        val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                HERO_HEIGHT,
                intArrayOf(Color.argb(20, 0, 0, 0), Color.argb(40, 0, 0, 0), Color.argb(220, 5, 8, 22)),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(heroRect, scrim)
    }

    private fun drawContent(canvas: Canvas, content: ShareCardContent) {
        var y = HERO_HEIGHT - 190f
        val titlePaint = textPaint(64f, Color.WHITE, Typeface.BOLD)
        y = drawWrappedText(canvas, content.title, PADDING, y, CARD_WIDTH - PADDING * 2, titlePaint, 2, 72f)

        content.subtitle?.let {
            y += 8f
            y = drawWrappedText(canvas, it, PADDING, y, CARD_WIDTH - PADDING * 2, textPaint(34f, Color.rgb(202, 213, 240)), 1, 42f)
        }

        y = HERO_HEIGHT + 70f
        if (content.tags.isNotEmpty()) {
            y = drawTags(canvas, content.tags, PADDING, y)
            y += 38f
        }

        if (content.stats.isNotEmpty()) {
            y = drawStats(canvas, content.stats, y)
            y += 42f
        }

        y = drawInfoPanel(canvas, content, y)
        drawFooter(canvas, content, y)
    }

    private fun drawTags(canvas: Canvas, tags: List<String>, startX: Float, startY: Float): Float {
        var x = startX
        var y = startY
        val textPaint = textPaint(28f, Color.WHITE, Typeface.BOLD)
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 255, 255, 255) }
        tags.forEach { tag ->
            val width = textPaint.measureText(tag) + 40f
            if (x + width > CARD_WIDTH - PADDING) {
                x = startX
                y += 58f
            }
            val rect = RectF(x, y, x + width, y + 46f)
            canvas.drawRoundRect(rect, 23f, 23f, chipPaint)
            canvas.drawText(tag, x + 20f, y + 32f, textPaint)
            x += width + 16f
        }
        return y + 46f
    }

    private fun drawStats(canvas: Canvas, stats: List<ShareStat>, startY: Float): Float {
        val columns = stats.size.coerceAtLeast(1).coerceAtMost(4)
        val gap = 18f
        val itemWidth = (CARD_WIDTH - PADDING * 2 - gap * (columns - 1)) / columns
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(64, 255, 255, 255) }
        val labelPaint = textPaint(24f, Color.rgb(174, 188, 220), Typeface.BOLD)
        val valuePaint = textPaint(44f, Color.WHITE, Typeface.BOLD)

        stats.take(columns).forEachIndexed { index, stat ->
            val left = PADDING + index * (itemWidth + gap)
            val rect = RectF(left, startY, left + itemWidth, startY + 150f)
            canvas.drawRoundRect(rect, 32f, 32f, boxPaint)
            canvas.drawText(stat.label.uppercase(Locale.getDefault()), left + 26f, startY + 48f, labelPaint)
            drawFittingText(canvas, stat.value, left + 26f, startY + 108f, itemWidth - 52f, valuePaint)
        }
        return startY + 150f
    }

    private fun drawInfoPanel(canvas: Canvas, content: ShareCardContent, startY: Float): Float {
        val rect = RectF(PADDING, startY, CARD_WIDTH - PADDING, startY + 300f)
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 10, 16, 36) }
        canvas.drawRoundRect(rect, 36f, 36f, panelPaint)

        var y = startY + 64f
        val labelPaint = textPaint(28f, Color.rgb(174, 188, 220), Typeface.BOLD)
        val valuePaint = textPaint(40f, Color.WHITE, Typeface.BOLD)
        canvas.drawText("STATUS", PADDING + 34f, y, labelPaint)
        y += 58f
        canvas.drawText(content.endLabel, PADDING + 34f, y, valuePaint)
        y += 62f

        content.locationLabel?.let {
            canvas.drawText("LOCATION", PADDING + 34f, y, labelPaint)
            y += 48f
            y = drawWrappedText(canvas, it, PADDING + 34f, y, CARD_WIDTH - PADDING * 2 - 68f, textPaint(34f, Color.WHITE), 2, 42f)
        }
        return rect.bottom
    }

    private fun drawFooter(canvas: Canvas, content: ShareCardContent, startY: Float) {
        val footerPaint = textPaint(28f, Color.rgb(174, 188, 220))
        val titlePaint = textPaint(32f, Color.WHITE, Typeface.BOLD)
        val y = (startY + 90f).coerceAtMost(CARD_HEIGHT - 110f)
        canvas.drawText("Shared from Pokemon Alerts", PADDING, y, titlePaint)
        content.mapsUrl?.let {
            drawWrappedText(canvas, it, PADDING, y + 46f, CARD_WIDTH - PADDING * 2, footerPaint, 2, 36f)
        }
    }

    private fun centerCropRect(srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int): Rect {
        val srcRatio = srcWidth.toFloat() / srcHeight
        val dstRatio = dstWidth.toFloat() / dstHeight
        return if (srcRatio > dstRatio) {
            val width = (srcHeight * dstRatio).roundToInt()
            val left = (srcWidth - width) / 2
            Rect(left, 0, left + width, srcHeight)
        } else {
            val height = (srcWidth / dstRatio).roundToInt()
            val top = (srcHeight - height) / 2
            Rect(0, top, srcWidth, top + height)
        }
    }

    private fun textPaint(
        size: Float,
        color: Int,
        style: Int = Typeface.NORMAL
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, style)
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        firstBaseline: Float,
        maxWidth: Float,
        paint: Paint,
        maxLines: Int,
        lineHeight: Float
    ): Float {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        var line = ""
        var y = firstBaseline
        var lines = 0
        for (word in words) {
            val candidate = if (line.isBlank()) word else "$line $word"
            if (paint.measureText(candidate) <= maxWidth) {
                line = candidate
            } else {
                if (line.isNotBlank()) {
                    canvas.drawText(ellipsize(line, maxWidth, paint), x, y, paint)
                    lines++
                    if (lines == maxLines) return y
                    y += lineHeight
                }
                line = word
            }
        }
        if (line.isNotBlank() && lines < maxLines) {
            canvas.drawText(ellipsize(line, maxWidth, paint), x, y, paint)
            y += lineHeight
        }
        return y
    }

    private fun drawFittingText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        paint: Paint
    ) {
        canvas.drawText(ellipsize(text, maxWidth, paint), x, y, paint)
    }

    private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var result = text
        while (result.length > 1 && paint.measureText("$result...") > maxWidth) {
            result = result.dropLast(1)
        }
        return "$result..."
    }

    private fun bestPvpLabel(alert: PokemonAlert): String? {
        val ranking = alert.pvpRankings
            ?.filter { it.rank != null }
            ?.minByOrNull { it.rank!! }
            ?: return null
        val league = ranking.league
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            ?.takeIf { it.isNotBlank() }
        return listOfNotNull(league, ranking.rank?.let { "#$it" }).joinToString(" ").takeIf { it.isNotBlank() }
    }

    private fun mapsUrl(alert: PokemonAlert): String? {
        val latitude = alert.latitude ?: return null
        val longitude = alert.longitude ?: return null
        return "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
    }
}
