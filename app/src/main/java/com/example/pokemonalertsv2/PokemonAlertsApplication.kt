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
import com.example.pokemonalertsv2.raidwatch.RaidWatchController
import com.example.pokemonalertsv2.tracking.ArrivalTrackingService
import com.example.pokemonalertsv2.ui.alerts.trimMapBitmapCaches
import com.example.pokemonalertsv2.util.InAppUpdateManager
import com.example.pokemonalertsv2.util.PendingInstallStore
import com.example.pokemonalertsv2.util.UpdateCheckSource
import com.example.pokemonalertsv2.widget.WidgetUpdateCoordinator
import android.os.Looper
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PokemonAlertsApplication : Application(), Configuration.Provider, ImageLoaderFactory,
    DefaultLifecycleObserver {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * The map's bitmap caches are process-wide and used to hold their full budget for the life
     * of the process, whether or not the map was on screen. Giving them back on request is the
     * difference between the system reclaiming from us and the system killing us.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        trimMapBitmapCaches(level)
    }

    override fun onCreate() {
        super<Application>.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        try {
            AlertNotifier.ensureChannel(this)
            FcmTopicSubscriber.subscribe(this)
            WidgetUpdateCoordinator.start(this)
            warmGoogleMaps()
        } catch (e: Exception) {
            Log.e("PokemonAlertsApp", "Error during application initialization", e)
        }
    }

    /**
     * Loads the Play Services Maps Dynamite module off the main thread.
     *
     * A Perfetto trace of the first Map tab entry showed a ~1s frame inside `draw-VRI`, almost
     * entirely `OpenDexFilesFromOat(.../dl-MapsCoreDynamite...)` plus `VerifyClass
     * com.google.maps.api.android.lib6.*` and `CreatorImpl` — the SDK loading and verifying its
     * classes synchronously the first time a GoogleMap is composed. The work is per-process, so
     * doing it here means the map tab no longer pays for it inside a frame.
     */
    private fun warmGoogleMaps() {
        applicationScope.launch(Dispatchers.IO) {
            runCatching { MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LATEST, null) }
                .onFailure { Log.w("PokemonAlertsApp", "Maps pre-warm skipped", it) }
        }
        warmMapRendererClasses()
    }

    /**
     * Builds and immediately destroys a throwaway [MapView] while the main thread is idle.
     *
     * [MapsInitializer.initialize] loads the Dynamite module but leaves the renderer classes
     * untouched, so a trace still showed them being verified (`VerifyClass
     * com.google.maps.api.android.lib6.*`) inside the first map frame — roughly a second of
     * jank on the first Map tab entry. The SDK refuses `onCreate` off the main thread, so the
     * work cannot simply be moved to a background thread; instead it runs from an idle handler,
     * where it costs a frame nobody is waiting on rather than one in the middle of a tap.
     *
     * Class loading is per-process, so the real map then finds everything already verified.
     * The view is never attached, and any failure is non-fatal because this is only a warm-up.
     */
    private fun warmMapRendererClasses() {
        Looper.getMainLooper().queue.addIdleHandler {
            runCatching {
                MapView(this).apply {
                    onCreate(null)
                    onDestroy()
                }
            }.onFailure { Log.w("PokemonAlertsApp", "Map renderer warm-up skipped", it) }
            false // one shot
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        if (PendingInstallStore.hasPending(this)) return
        ArrivalTrackingService.resumeIfActive(this)
        applicationScope.launch {
            // Re-post the watched raid and re-arm its tick: an alarm can be dropped when
            // the process is killed, and a stale countdown is worse than none.
            withContext(Dispatchers.IO) {
                RaidWatchController.refresh(this@PokemonAlertsApplication)
            }
            withContext(Dispatchers.IO) {
                GoDexRepository.getInstance(this@PokemonAlertsApplication).refreshIfStale()
            }
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
                            // The map's own pin caches sit beside this one; a quarter of the
                            // heap each is how the two of them ran the process out of room.
                            .maxSizePercent(0.12)
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
