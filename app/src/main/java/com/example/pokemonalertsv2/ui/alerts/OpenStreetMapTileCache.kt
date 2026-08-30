package com.example.pokemonalertsv2.ui.alerts

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.tan

/**
 * On-disk cache for the OpenStreetMap raster tiles.
 *
 * The map is most useful exactly when the network is worst -- out raiding, on a phone with
 * no signal -- and MapLibre otherwise re-fetches every tile. Tiles are immutable enough for
 * this: OSM re-renders slowly, and a week-old tile of a street is still a correct picture of
 * that street.
 *
 * Hooks into the OkHttp client that [MapLibreInitializer] already hands MapLibre, so nothing
 * about how the map requests tiles has to change.
 */
internal object OpenStreetMapTileCache {

    private const val TAG = "OsmTileCache"
    private const val CACHE_DIR = "osm_tiles"

    /** Roughly a few thousand tiles; OSM raster tiles are typically 10-30 KB. */
    const val MAX_SIZE_BYTES: Long = 128L * 1024 * 1024

    private val TILE_MAX_AGE_SECONDS = TimeUnit.DAYS.toSeconds(14).toInt()

    /** How stale a tile may be before it is refused while offline. */
    private val TILE_MAX_STALE_SECONDS = TimeUnit.DAYS.toSeconds(90).toInt()

    private fun cacheDir(context: Context) = File(context.applicationContext.cacheDir, CACHE_DIR)

    fun install(builder: OkHttpClient.Builder, context: Context): OkHttpClient.Builder =
        builder
            .cache(Cache(cacheDir(context), MAX_SIZE_BYTES))
            // A network interceptor so the rewritten headers are what gets stored. Tile
            // servers often send no-cache or a short max-age, which would defeat the cache
            // entirely.
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (isTileRequest(chain.request())) {
                    response.newBuilder()
                        .removeHeader("Pragma")
                        .removeHeader("Expires")
                        .header("Cache-Control", "public, max-age=$TILE_MAX_AGE_SECONDS")
                        .build()
                } else {
                    response
                }
            }
            // An application interceptor so a cached tile can still be served once the
            // stored max-age has run out but the device has no network to revalidate with.
            .addInterceptor { chain ->
                val request = chain.request()
                if (!isTileRequest(request)) return@addInterceptor chain.proceed(request)
                try {
                    chain.proceed(request)
                } catch (io: java.io.IOException) {
                    Log.d(TAG, "Tile fetch failed, trying the cache", io)
                    chain.proceed(
                        request.newBuilder()
                            .cacheControl(
                                CacheControl.Builder()
                                    .onlyIfCached()
                                    .maxStale(TILE_MAX_STALE_SECONDS, TimeUnit.SECONDS)
                                    .build()
                            )
                            .build()
                    )
                }
            }

    private fun isTileRequest(request: Request): Boolean =
        request.url.encodedPath.endsWith(".png", ignoreCase = true) ||
            request.url.encodedPath.endsWith(".jpg", ignoreCase = true)

    suspend fun sizeBytes(context: Context): Long = withContext(Dispatchers.IO) {
        runCatching {
            cacheDir(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
    }

    suspend fun clear(context: Context, client: OkHttpClient?) = withContext(Dispatchers.IO) {
        // Evict through the Cache when there is one, so OkHttp does not keep serving from
        // an index whose files have been deleted underneath it.
        runCatching { client?.cache?.evictAll() }
        runCatching { cacheDir(context).deleteRecursively() }
        Unit
    }

    /**
     * Warms the cache around a map centre, at the current zoom and the two levels below it.
     *
     * Takes a centre rather than a bounding box because the two map engines report their
     * camera differently, and both of them can always give a centre and a zoom.
     *
     * Two extra levels is the useful compromise: enough that zooming in on arrival still
     * works, without the fourfold-per-level growth turning a city view into tens of
     * thousands of requests. The tile count is capped regardless.
     */
    suspend fun prefetch(
        client: OkHttpClient,
        tileUrlTemplate: String,
        latitude: Double,
        longitude: Double,
        zoom: Int,
        tileRadius: Int = 2,
        extraZoomLevels: Int = 2,
        maxTiles: Int = 1_500,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        val urls = tileUrlsAround(
            template = tileUrlTemplate,
            latitude = latitude,
            longitude = longitude,
            zoom = zoom,
            tileRadius = tileRadius,
            extraZoomLevels = extraZoomLevels,
            maxTiles = maxTiles
        )
        var stored = 0
        var consecutiveFailures = 0
        urls.forEachIndexed { index, url ->
            val ok = runCatching {
                client.newCall(Request.Builder().url(url).build()).execute()
                    .use { it.isSuccessful }
            }.getOrDefault(false)
            if (ok) {
                stored++
                consecutiveFailures = 0
            } else {
                consecutiveFailures++
                // One missing tile is normal (sea, or a gap in coverage). A run of them
                // means offline or rate limited, and hammering on would help nobody.
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) return@withContext stored
            }
            onProgress(index + 1, urls.size)
        }
        stored
    }

    private const val MAX_CONSECUTIVE_FAILURES = 8

    /**
     * The tile URLs covering a bounding box, in Web Mercator (XYZ) order.
     *
     * Pure, so the tile-count explosion and the zoom clamping can be tested without a
     * network.
     */
    internal fun tileUrls(
        template: String,
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        zoom: Int,
        extraZoomLevels: Int = 2,
        maxTiles: Int = 1_500
    ): List<String> {
        val urls = mutableListOf<String>()
        val startZoom = zoom.coerceIn(0, 19)
        val endZoom = (startZoom + extraZoomLevels).coerceAtMost(19)

        for (z in startZoom..endZoom) {
            val xs = longitudeToTileX(west, z)..longitudeToTileX(east, z)
            val ys = latitudeToTileY(north, z)..latitudeToTileY(south, z)
            for (x in xs) {
                for (y in ys) {
                    if (urls.size >= maxTiles) return urls
                    urls += template
                        .replace("{z}", z.toString())
                        .replace("{x}", x.toString())
                        .replace("{y}", y.toString())
                        // Some templates carry a {s} subdomain placeholder.
                        .replace("{s}", "a")
                }
            }
        }
        return urls
    }

    /**
     * The tile URLs in a square of side `2 * tileRadius + 1` around a centre point.
     *
     * Works in tile space rather than degrees so the covered area stays the same size on
     * screen at every zoom, and so the count is exactly predictable.
     */
    internal fun tileUrlsAround(
        template: String,
        latitude: Double,
        longitude: Double,
        zoom: Int,
        tileRadius: Int = 2,
        extraZoomLevels: Int = 2,
        maxTiles: Int = 1_500
    ): List<String> {
        val urls = mutableListOf<String>()
        val startZoom = zoom.coerceIn(0, 19)
        val endZoom = (startZoom + extraZoomLevels).coerceAtMost(19)

        for (z in startZoom..endZoom) {
            val span = 2.0.pow(z).toInt()
            val centreX = longitudeToTileX(longitude, z)
            val centreY = latitudeToTileY(latitude, z)
            // Each extra zoom level halves the tile size, so the same on-screen area needs
            // twice the radius to stay covered.
            val radius = tileRadius shl (z - startZoom)
            for (x in (centreX - radius)..(centreX + radius)) {
                for (y in (centreY - radius)..(centreY + radius)) {
                    if (x < 0 || y < 0 || x >= span || y >= span) continue
                    if (urls.size >= maxTiles) return urls
                    urls += template
                        .replace("{z}", z.toString())
                        .replace("{x}", x.toString())
                        .replace("{y}", y.toString())
                        .replace("{s}", "a")
                }
            }
        }
        return urls
    }

    internal fun longitudeToTileX(longitude: Double, zoom: Int): Int {
        val n = 2.0.pow(zoom)
        val normalized = ((longitude + 180.0) / 360.0)
        return floor(normalized * n).toInt().coerceIn(0, (n - 1).toInt())
    }

    internal fun latitudeToTileY(latitude: Double, zoom: Int): Int {
        val n = 2.0.pow(zoom)
        // Web Mercator is undefined past ~85 degrees, so clamp rather than produce NaN.
        val clamped = latitude.coerceIn(-85.05112878, 85.05112878)
        val radians = clamped * PI / 180.0
        val normalized = (1.0 - asinh(tan(radians)) / PI) / 2.0
        return floor(normalized * n).toInt().coerceIn(0, (n - 1).toInt())
    }
}
