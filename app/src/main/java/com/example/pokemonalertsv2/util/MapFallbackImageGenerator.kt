package com.example.pokemonalertsv2.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
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
    private const val DEFAULT_ZOOM = 16
    private const val STYLE_VERSION = 2
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
     * @param zoom         OSM zoom level (default 16)
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

            val washPaint = Paint().apply {
                color = 0x4DF6F2EA
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(), washPaint)

            // Coordinate is at center of the output
            val cx = outputWidth / 2f
            val cy = outputHeight / 2f

            val minDimension = minOf(outputWidth, outputHeight)
            val radiusPx = (minDimension * 0.12f).coerceIn(8f, 96f)
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x805B8FD8.toInt()
                style = Paint.Style.STROKE
                strokeWidth = (minDimension * 0.006f).coerceIn(1.5f, 5f)
            }
            canvas.drawCircle(cx, cy, radiusPx, ringPaint)

            // Draw thumbnail sprite at coordinate center
            if (spriteBmp != null) {
                val spriteSize = (minDimension * 0.12f).toInt()
                    .coerceIn(12, 96)
                val scaled = Bitmap.createScaledBitmap(spriteBmp, spriteSize, spriteSize, true)
                val spriteLeft = cx - scaled.width / 2f
                val spriteTop = cy - scaled.height / 2f
                canvas.drawBitmap(scaled, spriteLeft, spriteTop, null)
                if (scaled !== spriteBmp) scaled.recycle()
                spriteBmp.recycle()
            }

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
                builder.addHeader("User-Agent", "PokemonAlertsV2/1.0")
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
            setSaturation(0.28f)
        }
        val lift = ColorMatrix(
            floatArrayOf(
                1.08f, 0f, 0f, 0f, 22f,
                0f, 1.08f, 0f, 0f, 22f,
                0f, 0f, 1.08f, 0f, 22f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        matrix.postConcat(lift)
        return ColorMatrixColorFilter(matrix)
    }

    private fun visibleTileRange(outputSize: Int, frac: Float): IntRange {
        val center = outputSize / 2f
        val first = floor((-center / TILE_SIZE) + frac).toInt()
        val last = floor(((outputSize - 1f - center) / TILE_SIZE) + frac).toInt()
        return first..last
    }
}
