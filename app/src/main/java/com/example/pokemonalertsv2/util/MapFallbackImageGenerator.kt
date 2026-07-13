package com.example.pokemonalertsv2.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.example.pokemonalertsv2.BuildConfig
import com.example.pokemonalertsv2.PokemonAlertsApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Generates a composite fallback Bitmap showing OSM map tiles
 * with a Pokemon thumbnail sprite overlaid at the alert's exact coordinates.
 * Used for notifications and widgets when [imageUrl] is unavailable.
 */
object MapFallbackImageGenerator {

    private const val TILE_SIZE = 256
    private const val DEFAULT_ZOOM = 17
    private const val STYLE_VERSION = 5
    private const val TILE_USER_AGENT_REPOSITORY =
        "https://github.com/TheTobiTiger02/PokemonAlertsAppV2"
    private val bitmapCache = object : LruCache<String, Bitmap>(32 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && !oldValue.isRecycled) oldValue.recycle()
        }
    }

    /**
     * Generates a composite map + thumbnail bitmap.
     *
     * @param context     Application context
     * @param latitude    Alert latitude
     * @param longitude   Alert longitude
     * @param thumbnailUrl URL of the Pokemon thumbnail sprite (nullable)
     * @param outputWidth  Desired output bitmap width in pixels
     * @param outputHeight Desired output bitmap height in pixels
     * @param zoom         OSM zoom level (default 17)
     * @return Composite [Bitmap] or null if generation fails
     */
    suspend fun generate(
        context: Context,
        latitude: Double,
        longitude: Double,
        thumbnailUrl: String?,
        outputWidth: Int = 512,
        outputHeight: Int = 256,
        zoom: Int = DEFAULT_ZOOM
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Validate coordinates
            if (!latitude.isFinite() || !longitude.isFinite()) return@withContext null
            if (latitude !in -85.0511..85.0511 || longitude !in -180.0..180.0) return@withContext null
            if (abs(latitude) < 0.0001 && abs(longitude) < 0.0001) return@withContext null

            val cacheKey =
                "$STYLE_VERSION|$latitude|$longitude|${thumbnailUrl.orEmpty()}|$outputWidth|$outputHeight|$zoom"
            bitmapCache.get(cacheKey)
                ?.takeUnless { it.isRecycled }
                ?.let { return@withContext it.copy(Bitmap.Config.ARGB_8888, false) }

            val imageLoader = PokemonAlertsApplication.imageLoader(context)

            // --- Tile math (same as SharedComponents.kt) ---
            val n = 1 shl zoom

            // Exact fractional tile coordinates
            val tileXExact = (longitude + 180.0) / 360.0 * n
            val latRad = Math.toRadians(latitude)
            val tileYExact =
                (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n

            // Integer tile coordinates (which tile the point falls in)
            val centerTileX = floor(tileXExact).toInt()
            val centerTileY = floor(tileYExact).toInt()

            // Fractional position within the center tile (0.0–1.0)
            val fracX = (tileXExact - centerTileX).toFloat()
            val fracY = (tileYExact - centerTileY).toFloat()

            // Build only the tile ranges that intersect the output bitmap.
            val xTileRange = visibleTileRange(outputWidth, fracX)
            val yTileRange = visibleTileRange(outputHeight, fracY)

            // --- Load all tiles concurrently ---
            data class TileInfo(val dx: Int, val dy: Int, val bitmap: Bitmap?)

            val spriteJob = if (!thumbnailUrl.isNullOrBlank()) {
                async { loadBitmap(imageLoader, context, thumbnailUrl, isMapTile = false) }
            } else {
                null
            }

            val tileJobs = xTileRange.flatMap { dx ->
                yTileRange.map { dy ->
                    async {
                        val x = ((centerTileX + dx) % n + n) % n
                        val y = (centerTileY + dy).coerceIn(0, n - 1)
                        val tileUrl = "https://tile.openstreetmap.org/$zoom/$x/$y.png"
                        val bmp = loadBitmap(imageLoader, context, tileUrl, isMapTile = true)
                        TileInfo(dx, dy, bmp)
                    }
                }
            }

            val tiles = tileJobs.awaitAll()
            val spriteBmp = spriteJob?.await()

            // The tile containing the exact alert coordinate is essential. If it
            // failed, let callers continue to their thumbnail/placeholder fallback.
            if (tiles.none { it.dx == 0 && it.dy == 0 && it.bitmap != null }) {
                tiles.forEach { it.bitmap?.recycle() }
                spriteBmp?.recycle()
                return@withContext null
            }

            // --- Composite the output bitmap ---
            val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)

            // Light neutral background (visible if tiles fail to load)
            canvas.drawColor(0xFFE8EAED.toInt())

            val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = mutedMapColorFilter()
            }

            // Draw map tiles in a muted, lighter style closer to API alert images.
            for (tile in tiles) {
                val bmp = tile.bitmap ?: continue
                val left = outputWidth / 2f + (tile.dx - fracX) * TILE_SIZE
                val top = outputHeight / 2f + (tile.dy - fracY) * TILE_SIZE
                canvas.drawBitmap(bmp, left, top, tilePaint)
            }

            // Coordinate is at center of the output
            val cx = outputWidth / 2f
            val cy = outputHeight / 2f

            val minDimension = minOf(outputWidth, outputHeight)
            drawPokemonAtCoordinate(canvas, cx, cy, minDimension, spriteBmp)
            drawOpenStreetMapAttribution(canvas, context, outputWidth, outputHeight)

            // Recycle tile bitmaps
            tiles.forEach { it.bitmap?.recycle() }

            bitmapCache.put(cacheKey, output)
            output.copy(Bitmap.Config.ARGB_8888, false)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun loadBitmap(
        imageLoader: ImageLoader,
        context: Context,
        url: String,
        isMapTile: Boolean
    ): Bitmap? {
        return try {
            val builder = ImageRequest.Builder(context)
                .data(url)
                .scale(Scale.FILL)
                .allowHardware(false)
            if (isMapTile) {
                builder.addHeader(
                    "User-Agent",
                    "PokemonAlertsV2/${BuildConfig.VERSION_NAME} (+$TILE_USER_AGENT_REPOSITORY)"
                )
            }
            val result = imageLoader.execute(builder.build())
            if (result is SuccessResult) {
                val drawable = result.drawable
                if (drawable is BitmapDrawable) {
                    // Return a copy so we can safely recycle later
                    drawable.bitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    val bmp = Bitmap.createBitmap(
                        drawable.intrinsicWidth.coerceAtLeast(1),
                        drawable.intrinsicHeight.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    val c = Canvas(bmp)
                    drawable.setBounds(0, 0, c.width, c.height)
                    drawable.draw(c)
                    bmp
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun mutedMapColorFilter(): ColorMatrixColorFilter {
        val matrix = ColorMatrix().apply {
            setSaturation(0.65f)
        }
        val lift = ColorMatrix(
            floatArrayOf(
                1.04f, 0f, 0f, 0f, 8f,
                0f, 1.04f, 0f, 0f, 8f,
                0f, 0f, 1.04f, 0f, 8f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        matrix.postConcat(lift)
        return ColorMatrixColorFilter(matrix)
    }

    private fun drawPokemonAtCoordinate(
        canvas: Canvas,
        coordinateX: Float,
        coordinateY: Float,
        minDimension: Int,
        sprite: Bitmap?
    ) {
        if (sprite != null) {
            val spriteSize = (minDimension * 0.18f).toInt().coerceIn(24, 112)
            val scaled = Bitmap.createScaledBitmap(sprite, spriteSize, spriteSize, true)
            val spriteLeft = coordinateX - scaled.width / 2f
            val spriteTop = coordinateY - scaled.height / 2f
            canvas.drawBitmap(scaled, spriteLeft, spriteTop, null)
            if (scaled !== sprite) scaled.recycle()
            sprite.recycle()
        } else {
            canvas.drawCircle(
                coordinateX,
                coordinateY,
                (minDimension * 0.025f).coerceIn(4f, 14f),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2869D8.toInt() }
            )
        }
    }

    private fun drawOpenStreetMapAttribution(
        canvas: Canvas,
        context: Context,
        outputWidth: Int,
        outputHeight: Int
    ) {
        val density = context.resources.displayMetrics.density
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCC172033.toInt()
            textSize = maxOf(7f * density, minOf(outputWidth, outputHeight) * 0.028f)
                .coerceAtMost(24f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val attribution = "\u00A9 OpenStreetMap contributors"
        val availableTextWidth = outputWidth * 0.88f
        val measuredWidth = textPaint.measureText(attribution)
        if (measuredWidth > availableTextWidth) {
            textPaint.textSize *= availableTextWidth / measuredWidth
        }
        val paddingX = maxOf(4f * density, outputWidth * 0.006f)
        val paddingY = maxOf(2f * density, outputHeight * 0.008f)
        val margin = maxOf(4f * density, outputWidth * 0.008f)
        val metrics = textPaint.fontMetrics
        val width = textPaint.measureText(attribution) + paddingX * 2f
        val height = metrics.descent - metrics.ascent + paddingY * 2f
        val rect = RectF(
            outputWidth - margin - width,
            outputHeight - margin - height,
            outputWidth - margin,
            outputHeight - margin
        )
        canvas.drawRoundRect(
            rect,
            height * 0.22f,
            height * 0.22f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9FFFFFF.toInt() }
        )
        canvas.drawText(
            attribution,
            rect.left + paddingX,
            rect.top + paddingY - metrics.ascent,
            textPaint
        )
    }

    private fun visibleTileRange(outputSize: Int, frac: Float): IntRange {
        val center = outputSize / 2f
        val first = floor((-center / TILE_SIZE) + frac).toInt()
        val last = floor(((outputSize - 1f - center) / TILE_SIZE) + frac).toInt()
        return first..last
    }
}
