package com.example.pokemonalertsv2.ui.counters

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.pokemonalertsv2.PokemonAlertsApplication
import com.example.pokemonalertsv2.data.counters.toCounterMetric
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * The top counters as a picture, for pasting into a raid group.
 *
 * Deliberately a Canvas card rather than a screenshot of the composable: the card is a
 * fragment of a scrolling detail screen, it is theme-dependent, and half of what it shows
 * (chips, the tune button) is interactive chrome that means nothing to the recipient.
 *
 * Mirrors [com.example.pokemonalertsv2.ui.alerts.AlertShareCard] — same cache directory,
 * same FileProvider authority, same chooser handling — so there is one export path in the
 * app rather than two that drift apart.
 */
object CounterTeamShareCard {

    private const val CARD_WIDTH = 1080
    private const val PADDING = 56f
    private const val HEADER_HEIGHT = 240f
    private const val ROW_HEIGHT = 168f
    private const val FOOTER_HEIGHT = 96f
    private const val SPRITE = 112f
    private const val MAX_ROWS = 6

    /** One counter as it appears on the shared card. */
    data class ShareRow(
        val rank: Int,
        val name: String,
        val moves: String,
        val metric: String,
        val spriteUrl: String?
    )

    data class ShareContent(
        val bossName: String,
        val bossSpriteUrl: String?,
        val setupLine: String,
        val metricLabel: String,
        val rows: List<ShareRow>
    )

    /**
     * Builds the content for the current state.
     *
     * Returns null when there is nothing worth sharing, so the caller can hide the action
     * rather than produce an empty card.
     */
    fun buildContent(state: RaidCountersUiState): ShareContent? {
        val metric = state.options.sort
        val rows = if (state.showingPersonal) {
            state.personal?.ranked.orEmpty().take(MAX_ROWS).mapIndexed { index, counter ->
                ShareRow(
                    rank = index + 1,
                    name = counter.displayName,
                    moves = counter.moveLine(),
                    metric = counter.metrics.headline(metric.toCounterMetric()),
                    spriteUrl = state.spriteUrls[counter.pokemonId]?.firstOrNull()
                )
            }
        } else {
            val all = if (state.ownedOnly) state.counters.filter { it.isOwned } else state.counters
            all.take(MAX_ROWS).map { entry ->
                val counter = entry.counter
                ShareRow(
                    rank = counter.rank,
                    name = counter.displayName,
                    moves = listOfNotNull(counter.fastMove, counter.chargedMove)
                        .joinToString(" / "),
                    metric = headlineMetric(counter, metric.toCounterMetric()),
                    spriteUrl = state.spriteUrls[counter.pokemonId]?.firstOrNull()
                )
            }
        }
        if (rows.isEmpty()) return null

        val setup = buildString {
            if (state.showingPersonal) append("My Pokémon") else append("L${state.options.attackerLevel}")
            append(" · ").append(state.options.weather.label)
            append(" · ").append(state.options.friendship.label)
        }
        return ShareContent(
            bossName = state.bossDisplayName ?: "Raid boss",
            bossSpriteUrl = state.bossThumbnailUrl
                ?: state.spriteUrls[state.bossPokemonId]?.firstOrNull(),
            setupLine = setup,
            metricLabel = metric.label,
            rows = rows
        )
    }

    /** The same list as plain text, for chat clients that reject images. */
    fun buildShareText(content: ShareContent): String = buildString {
        append("Best counters — ${content.bossName}\n")
        append("${content.setupLine}\n\n")
        content.rows.forEach { row ->
            append("${row.rank}. ${row.name}")
            if (row.moves.isNotBlank()) append(" — ${row.moves}")
            if (row.metric.isNotBlank()) append(" (${row.metric})")
            append('\n')
        }
        append("\nvia Pokébattler")
    }

    /**
     * Renders and hands the card to the system chooser.
     *
     * Falls back to text when the bitmap cannot be written, which is the same degradation
     * the alert share card uses; a share that silently does nothing is worse than a share
     * without a picture.
     */
    suspend fun share(context: Context, content: ShareContent): Boolean {
        val uri = withContext(Dispatchers.IO) {
            runCatching { createUri(context.applicationContext, content) }.getOrNull()
        }
        val text = buildShareText(content)

        val intent = if (uri != null) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Counters: ${content.bossName}")
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Counters: ${content.bossName}")
                putExtra(Intent.EXTRA_TEXT, text)
            }
        }

        return withContext(Dispatchers.Main) {
            runCatching {
                val chooser = Intent.createChooser(intent, "Share counters")
                if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                true
            }.getOrDefault(false)
        }
    }

    private suspend fun createUri(context: Context, content: ShareContent): Uri {
        val bitmap = render(context, content)
        val dir = File(context.cacheDir, "shared_alerts").apply { mkdirs() }
        val file = File(dir, "counters_${content.bossName.hashCode()}.png")
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    // ── Drawing ──────────────────────────────────────────────────────────────

    // Fixed, light-on-dark colours. The recipient does not share the sender's theme, and a
    // card that changes appearance depending on who exported it is confusing in a thread.
    private const val BACKGROUND = 0xFF161A21.toInt()
    private const val PANEL = 0xFF1F252E.toInt()
    private const val ACCENT = 0xFF7FA7FF.toInt()
    private const val TEXT = 0xFFF2F4F8.toInt()
    private const val MUTED = 0xFF9AA5B1.toInt()

    private suspend fun render(context: Context, content: ShareContent): Bitmap {
        val loader = PokemonAlertsApplication.imageLoader(context)
        val bossSprite = loadBitmap(loader, context, content.bossSpriteUrl)
        val sprites = content.rows.map { loadBitmap(loader, context, it.spriteUrl) }

        val height = (HEADER_HEIGHT + ROW_HEIGHT * content.rows.size + FOOTER_HEIGHT).toInt()
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)

        drawHeader(canvas, content, bossSprite)
        bossSprite?.recycle()

        content.rows.forEachIndexed { index, row ->
            drawRow(canvas, row, sprites[index], HEADER_HEIGHT + ROW_HEIGHT * index)
            sprites[index]?.recycle()
        }

        drawFooter(canvas, content, height.toFloat())
        return bitmap
    }

    private fun drawHeader(canvas: Canvas, content: ShareContent, sprite: Bitmap?) {
        sprite?.let { drawSprite(canvas, it, PADDING, 52f, 136f) }
        val left = PADDING + 136f + 28f
        canvas.drawText(
            content.bossName,
            left,
            118f,
            textPaint(52f, TEXT, Typeface.BOLD)
        )
        canvas.drawText(content.setupLine, left, 172f, textPaint(32f, MUTED))
        canvas.drawText(
            "Best counters by ${content.metricLabel.lowercase(Locale.getDefault())}",
            left,
            216f,
            textPaint(30f, ACCENT)
        )
    }

    private fun drawRow(canvas: Canvas, row: ShareRow, sprite: Bitmap?, top: Float) {
        val panel = RectF(PADDING, top + 8f, CARD_WIDTH - PADDING, top + ROW_HEIGHT - 8f)
        canvas.drawRoundRect(panel, 28f, 28f, Paint().apply { color = PANEL; isAntiAlias = true })

        val centreY = panel.centerY()
        canvas.drawText(
            row.rank.toString(),
            panel.left + 40f,
            centreY + 14f,
            textPaint(40f, ACCENT, Typeface.BOLD)
        )
        sprite?.let { drawSprite(canvas, it, panel.left + 92f, centreY - SPRITE / 2f, SPRITE) }

        val textLeft = panel.left + 92f + SPRITE + 24f
        val metricPaint = textPaint(32f, ACCENT, Typeface.BOLD)
        val metricWidth = metricPaint.measureText(row.metric)
        val available = (panel.right - 32f - metricWidth - textLeft).coerceAtLeast(120f)

        canvas.drawText(
            ellipsize(row.name, textPaint(38f, TEXT, Typeface.BOLD), available),
            textLeft,
            centreY - 6f,
            textPaint(38f, TEXT, Typeface.BOLD)
        )
        canvas.drawText(
            ellipsize(row.moves, textPaint(28f, MUTED), available),
            textLeft,
            centreY + 38f,
            textPaint(28f, MUTED)
        )
        canvas.drawText(row.metric, panel.right - 32f - metricWidth, centreY + 12f, metricPaint)
    }

    private fun drawFooter(canvas: Canvas, content: ShareContent, height: Float) {
        canvas.drawText(
            "Simulated by Pokébattler",
            PADDING,
            height - 36f,
            textPaint(28f, MUTED)
        )
    }

    /** Scales into a square box, preserving aspect ratio and centring the artwork. */
    private fun drawSprite(canvas: Canvas, sprite: Bitmap, left: Float, top: Float, box: Float) {
        val scale = box / maxOf(sprite.width, sprite.height).toFloat()
        val width = sprite.width * scale
        val height = sprite.height * scale
        val dest = RectF(
            left + (box - width) / 2f,
            top + (box - height) / 2f,
            left + (box + width) / 2f,
            top + (box + height) / 2f
        )
        canvas.drawBitmap(
            sprite,
            Rect(0, 0, sprite.width, sprite.height),
            dest,
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
    }

    private fun textPaint(size: Float, colour: Int, style: Int = Typeface.NORMAL) = Paint().apply {
        isAntiAlias = true
        textSize = size
        color = colour
        typeface = Typeface.create(Typeface.DEFAULT, style)
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (text.isEmpty() || paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    private suspend fun loadBitmap(loader: ImageLoader, context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(SPRITE.toInt() * 2)
                .build()
            (loader.execute(request) as? SuccessResult)
                ?.drawable
                ?.let { it as? BitmapDrawable }
                ?.bitmap
                ?.copy(Bitmap.Config.ARGB_8888, false)
        }.getOrNull()
    }
}
