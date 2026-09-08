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
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.notifications.AlertNotifier
import com.example.pokemonalertsv2.raidwatch.RaidWatchController
import com.example.pokemonalertsv2.tracking.ArrivalTrackingService
import com.example.pokemonalertsv2.ui.alerts.trimMapBitmapCaches
import com.example.pokemonalertsv2.util.InAppUpdateManager
import com.example.pokemonalertsv2.util.PendingInstallStore
import com.example.pokemonalertsv2.util.UpdateCheckSource
import com.example.pokemonalertsv2.widget.WidgetUpdateCoordinator
import com.example.pokemonalertsv2.work.PushTopicSyncWorker
import com.google.android.gms.maps.MapsInitializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
            PushTopicSyncWorker.triggerSync(this, delaySeconds = 0)
            PushTopicSyncWorker.schedule(this)
            resyncPushTopicsOnFilterChanges()
            WidgetUpdateCoordinator.start(this)
            warmGoogleMaps()
        } catch (e: Exception) {
            Log.e("PokemonAlertsApp", "Error during application initialization", e)
        }
    }

    /**
     * Re-plans the FCM topic subscription whenever the filters change.
     *
     * Watching the document in one place beats calling into the sync from every editor: the feed
     * sheet, the map sheet, the filter studio, a profile edit and a backup restore all land here.
     * The first emission is the current state, which [PushTopicSyncWorker.triggerSync] in
     * `onCreate` has already covered.
     */
    private fun resyncPushTopicsOnFilterChanges() {
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                AlertPreferences(alertPreferencesDataStore).filterStateDocument
                    .drop(1)
                    .distinctUntilChanged()
                    .collect { PushTopicSyncWorker.triggerSync(this@PokemonAlertsApplication) }
            }
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
        // Let the visible map own renderer creation. A throwaway MapView here starts main-
        // thread and GPU work during feed startup, even for users who never open the map.
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
                    // Every marker sprite comes from the same icons host, and OkHttp allows
                    // five concurrent requests per host by default. A screen of quest pins
                    // therefore trickled in five at a time however fast the network was.
                    .okHttpClient {
                        okhttp3.OkHttpClient.Builder()
                            .dispatcher(
                                okhttp3.Dispatcher().apply {
                                    maxRequests = 64
                                    maxRequestsPerHost = 16
                                }
                            )
                            .build()
                    }
                    .crossfade(true)
                    .build()
                    .also { sharedImageLoader = it }
            }
        }
    }
}
