package com.example.pokemonalertsv2

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.pokemonalertsv2.fcm.FcmTopicSubscriber
import com.example.pokemonalertsv2.notifications.AlertNotifier

class PokemonAlertsApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        try {
            AlertNotifier.ensureChannel(this)
            FcmTopicSubscriber.subscribe(this)
        } catch (e: Exception) {
            Log.e("PokemonAlertsApp", "Error during application initialization", e)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun newImageLoader(): ImageLoader = imageLoader(this)

    companion object {
        @Volatile
        private var sharedImageLoader: ImageLoader? = null

        fun imageLoader(context: Context): ImageLoader {
            return sharedImageLoader ?: synchronized(this) {
                sharedImageLoader ?: ImageLoader.Builder(context.applicationContext)
                    .memoryCache {
                        MemoryCache.Builder(context.applicationContext)
                            .maxSizePercent(0.25)
                            .build()
                    }
                    .diskCache {
                        DiskCache.Builder()
                            .directory(context.applicationContext.cacheDir.resolve("image_cache"))
                            .maxSizePercent(0.02)
                            .build()
                    }
                    .crossfade(true)
                    .build()
                    .also { sharedImageLoader = it }
            }
        }
    }
}
