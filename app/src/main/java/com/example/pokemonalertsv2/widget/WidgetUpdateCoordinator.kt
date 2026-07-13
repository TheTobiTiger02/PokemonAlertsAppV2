package com.example.pokemonalertsv2.widget

import android.content.Context
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coalesces widget refresh requests and observes the local alert cache so every
 * Room writer (FCM, workers, and foreground refreshes) updates placed widgets.
 */
internal object WidgetUpdateCoordinator {
    private const val DEBOUNCE_MILLIS = 150L

    private val started = AtomicBoolean(false)
    private val requests = Channel<Unit>(capacity = Channel.CONFLATED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext

        scope.launch {
            requests.receiveAsFlow()
                .debounce(DEBOUNCE_MILLIS)
                .collect {
                    AlertsWidgetProvider.sendUpdateBroadcast(appContext)
                }
        }

        scope.launch {
            PokemonAlertsRepository.create(appContext).alerts
                .map { alerts -> alerts.map { it.uniqueId to it.hashCode() } }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    request(appContext)
                }
        }
    }

    fun request(context: Context) {
        start(context)
        requests.trySend(Unit)
    }
}
