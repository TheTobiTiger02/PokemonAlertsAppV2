package com.example.pokemonalertsv2.data.net

import com.example.pokemonalertsv2.BuildConfig
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The one OkHttp client the app's other clients are derived from.
 *
 * Every client built with `Http.base.newBuilder()` shares this connection pool
 * and dispatcher threads instead of opening its own, and inherits the timeouts
 * and User-Agent below. A client that needs different limits (Pokebattler's slow
 * ranking calls, the APK download) overrides them on its own builder.
 */
object Http {
    private const val CACHE_BYTES = 20L * 1024 * 1024
    val userAgent: String =
        "PokemonAlertsV2/${BuildConfig.VERSION_NAME} (Android; ${BuildConfig.APPLICATION_ID})"

    private val userAgentInterceptor = Interceptor { chain ->
        val request = chain.request()
        chain.proceed(
            if (request.header("User-Agent") != null) request
            else request.newBuilder().header("User-Agent", userAgent).build()
        )
    }

    /** Set by the Application before any request, so responses can be cached on disk. */
    @Volatile
    var cacheDirectory: File? = null

    val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // Honours Cache-Control and ETags (the backend's waypoints and routes, events).
            .apply { cacheDirectory?.let { cache(Cache(File(it, "http"), CACHE_BYTES)) } }
            // Asks for Brotli as well as gzip. Cloudflare serves the full alert feed about a
            // third smaller as Brotli (186 kB against 284 kB for ~5,000 alerts).
            .addInterceptor(BrotliInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            // Bounds the whole call, so a server that trickles bytes cannot hold a worker forever.
            .callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(userAgentInterceptor)
            .build()
    }
}
