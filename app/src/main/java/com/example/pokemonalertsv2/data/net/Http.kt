package com.example.pokemonalertsv2.data.net

import com.example.pokemonalertsv2.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
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
    val userAgent: String =
        "PokemonAlertsV2/${BuildConfig.VERSION_NAME} (Android; ${BuildConfig.APPLICATION_ID})"

    private val userAgentInterceptor = Interceptor { chain ->
        val request = chain.request()
        chain.proceed(
            if (request.header("User-Agent") != null) request
            else request.newBuilder().header("User-Agent", userAgent).build()
        )
    }

    val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            // Bounds the whole call, so a server that trickles bytes cannot hold a worker forever.
            .callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(userAgentInterceptor)
            .build()
    }
}
