package com.example.pokemonalertsv2.ui.alerts

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.example.pokemonalertsv2.data.net.Http
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.offline.OfflineManager

/** Initializes the optional OpenStreetMap engine only when that map is requested. */
internal object MapLibreInitializer {
    private const val TAG = "MapLibreInitializer"
    private const val PREFS = "maplibre_init"
    private const val PURGED_BLOCKED_TILES_KEY = "purged_blocked_osm_tiles_v1"

    @Volatile
    private var initialized = false

    /**
     * The client MapLibre fetches tiles with.
     *
     * Kept so the tile cache can be cleared through the same [okhttp3.Cache] instance that
     * wrote it, and so prefetching can reuse the connection pool rather than opening its
     * own.
     */
    @Volatile
    private var tileClient: OkHttpClient? = null

    fun tileClient(): OkHttpClient? = tileClient

    /**
     * Replaces whatever User-Agent the request carries with the app's own.
     *
     * MapLibre sets its own, starting with the application id. tile.openstreetmap.org refuses
     * any client that identifies as `com.example.*` and answers with an "Access blocked" image
     * (status 200), so the shared client's add-if-missing User-Agent is not enough here.
     */
    private val appUserAgent = Interceptor { chain ->
        chain.proceed(chain.request().newBuilder().header("User-Agent", Http.userAgent).build())
    }

    fun ensureInitialized(context: Context): Boolean {
        if (initialized) return true
        synchronized(this) {
            if (initialized) return true
            return runCatching {
                val appContext = context.applicationContext
                MapLibre.getInstance(appContext)
                val client = OpenStreetMapTileCache.install(
                    Http.base.newBuilder().addInterceptor(appUserAgent),
                    appContext
                ).build()
                tileClient = client
                HttpRequestUtil.setOkHttpClient(client)
                purgeBlockedTilesOnce(appContext, client)
                initialized = true
            }.onFailure { error ->
                Log.e(TAG, "OpenStreetMap initialization failed", error)
            }.isSuccess
        }
    }

    /**
     * Versions 1.28.2 to 1.29.4 sent MapLibre's User-Agent and were served "Access blocked"
     * images, which both caches then kept for weeks. Drops both caches once so real tiles load.
     */
    private fun purgeBlockedTilesOnce(context: Context, client: OkHttpClient) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PURGED_BLOCKED_TILES_KEY, false)) return
        runCatching { client.cache?.evictAll() }
            .onFailure { Log.w(TAG, "Could not clear the OkHttp tile cache", it) }
        OfflineManager.getInstance(context).clearAmbientCache(object : OfflineManager.FileSourceCallback {
            override fun onSuccess() {
                prefs.edit { putBoolean(PURGED_BLOCKED_TILES_KEY, true) }
            }

            override fun onError(message: String) {
                // Left unset, so the next start tries again.
                Log.w(TAG, "Could not clear MapLibre's tile cache: $message")
            }
        })
    }
}
