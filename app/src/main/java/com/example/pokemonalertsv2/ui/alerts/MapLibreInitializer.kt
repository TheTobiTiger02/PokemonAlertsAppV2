package com.example.pokemonalertsv2.ui.alerts

import android.content.Context
import android.util.Log
import com.example.pokemonalertsv2.BuildConfig
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

/** Initializes the optional OpenStreetMap engine only when that map is requested. */
internal object MapLibreInitializer {
    private const val TAG = "MapLibreInitializer"

    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context): Boolean {
        if (initialized) return true
        synchronized(this) {
            if (initialized) return true
            return runCatching {
                val appContext = context.applicationContext
                MapLibre.getInstance(appContext)
                HttpRequestUtil.setOkHttpClient(
                    OkHttpClient.Builder()
                        .addInterceptor { chain ->
                            chain.proceed(
                                chain.request().newBuilder()
                                    .header(
                                        "User-Agent",
                                        "PokemonAlertsV2/${BuildConfig.VERSION_NAME} " +
                                            "(Android; ${BuildConfig.APPLICATION_ID})"
                                    )
                                    .build()
                            )
                        }
                        .build()
                )
                initialized = true
            }.onFailure { error ->
                Log.e(TAG, "OpenStreetMap initialization failed", error)
            }.isSuccess
        }
    }
}
