package com.example.pokemonalertsv2

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.pokemonalertsv2.fcm.FcmTopicSubscriber
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.notifications.AlertNotifier
import com.example.pokemonalertsv2.util.InAppUpdateManager
import com.example.pokemonalertsv2.util.PendingInstallStore
import com.example.pokemonalertsv2.util.UpdateCheckSource
import com.example.pokemonalertsv2.widget.WidgetUpdateCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

class PokemonAlertsApplication : Application(), Configuration.Provider, ImageLoaderFactory,
    DefaultLifecycleObserver {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super<Application>.onCreate()
        runCatching {
            MapLibre.getInstance(this)
            HttpRequestUtil.setOkHttpClient(
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val identifiedRequest = chain.request().newBuilder()
                            .header(
                                "User-Agent",
                                "PokemonAlertsV2/${BuildConfig.VERSION_NAME} (Android; ${BuildConfig.APPLICATION_ID})"
                            )
                            .build()
                        chain.proceed(identifiedRequest)
                    }
                    .build()
            )
        }.onFailure { error ->
            Log.e("PokemonAlertsApp", "OpenStreetMap initialization failed", error)
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        try {
            AlertNotifier.ensureChannel(this)
            FcmTopicSubscriber.subscribe(this)
            WidgetUpdateCoordinator.start(this)
        } catch (e: Exception) {
            Log.e("PokemonAlertsApp", "Error during application initialization", e)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        if (PendingInstallStore.hasPending(this)) return
        applicationScope.launch {
            GoDexRepository.getInstance(this@PokemonAlertsApplication).refreshIfStale()
            InAppUpdateManager.checkForUpdates(UpdateCheckSource.AUTOMATIC)
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
