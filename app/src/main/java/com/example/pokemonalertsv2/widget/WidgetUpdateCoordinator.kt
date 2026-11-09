package com.example.pokemonalertsv2.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.example.pokemonalertsv2.data.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Widgets cannot be seen with the display off, so their work can wait for it to come back on. */
internal fun Context.isScreenInteractive(): Boolean =
    getSystemService(PowerManager::class.java)?.isInteractive ?: true

/**
 * Coalesces widget refresh requests and observes the local alert cache so every
 * Room writer (FCM, workers, and foreground refreshes) updates placed widgets.
 *
 * Requests made with the screen off are dropped, and turning the screen on always refreshes: the
 * widgets are invisible until then, and a push wave would otherwise redraw them for nobody.
 */
internal object WidgetUpdateCoordinator {
    private const val MIN_REFRESH_GAP_MILLIS = 5_000L

    private val started = AtomicBoolean(false)
    private val requests = Channel<Unit>(capacity = Channel.CONFLATED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext

        ContextCompat.registerReceiver(
            appContext,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    requests.trySend(Unit)
                }
            },
            IntentFilter(Intent.ACTION_SCREEN_ON),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        scope.launch {
            runRefreshLoop(
                requests = requests,
                isScreenOn = appContext::isScreenInteractive,
                gapMillis = MIN_REFRESH_GAP_MILLIS
            ) {
                AlertsWidgetProvider.sendDataChangedBroadcast(appContext)
                NearbyRadarWidgetProvider.sendDataChangedBroadcast(appContext)
            }
        }

        scope.launch {
            AppDatabase.getDatabase(appContext).alertDao().observeTableWrites()
                .drop(1)
                .collect { request(appContext) }
        }
    }

    fun request(context: Context) {
        start(context)
        requests.trySend(Unit)
    }

    /**
     * Refreshes on the first request, then at most once per [gapMillis], with a conflated channel
     * supplying the trailing refresh. A debounce would never fire under a steady stream of pushes.
     */
    @VisibleForTesting
    internal suspend fun runRefreshLoop(
        requests: ReceiveChannel<Unit>,
        isScreenOn: () -> Boolean,
        gapMillis: Long,
        refresh: () -> Unit
    ) {
        for (ignored in requests) {
            if (!isScreenOn()) continue
            refresh()
            delay(gapMillis)
        }
    }
}
