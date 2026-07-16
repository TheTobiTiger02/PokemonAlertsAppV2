package com.example.pokemonalertsv2.fcm

import android.content.Context
import android.util.Log
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.notifications.AlertNotifier
import com.example.pokemonalertsv2.widget.AlertsWidgetProvider

internal object FcmAlertHandler {
    private const val TAG = "FcmAlertHandler"

    suspend fun handle(context: Context, data: Map<String, String>): FcmAlertHandlingResult {
        val appContext = context.applicationContext
        val payload = FcmAlertPayloadParser.parse(data)
        if (payload == null) {
            Log.w(TAG, "Ignoring FCM alert message without a parseable alert payload")
            return FcmAlertHandlingResult.INVALID
        }

        val repository = PokemonAlertsRepository.create(appContext)
        return FcmAlertProcessor(
            detectNew = { alert -> repository.detectNewAlerts(listOf(alert)).isNotEmpty() },
            processIncoming = { alert -> repository.processIncomingAlert(alert) },
            clearExpired = { repository.clearExpiredAlerts() },
            notify = { alert -> AlertNotifier.notifyAlerts(appContext, listOf(alert)) },
            markSeen = { alert -> repository.markAlertsAsSeen(listOf(alert)) },
            requestWidgetUpdate = { AlertsWidgetProvider.updateFromWorker(appContext) }
        ).process(payload.alert)
    }
}

internal enum class FcmAlertHandlingResult(val requestsAuthoritativeSync: Boolean) {
    HANDLED(false),
    DUPLICATE(false),
    WEATHER_HANDLED(true),
    WEATHER_DUPLICATE(true),
    INVALID(true)
}

internal class FcmAlertProcessor(
    private val detectNew: suspend (PokemonAlert) -> Boolean,
    private val processIncoming: suspend (PokemonAlert) -> Unit,
    private val clearExpired: suspend () -> Unit,
    private val notify: suspend (PokemonAlert) -> Unit,
    private val markSeen: suspend (PokemonAlert) -> Unit,
    private val requestWidgetUpdate: () -> Unit
) {
    suspend fun process(alert: PokemonAlert): FcmAlertHandlingResult {
        val isNewAlert = !alert.isInvalidated && detectNew(alert)
        processIncoming(alert)
        clearExpired()

        if (isNewAlert) {
            notify(alert)
            markSeen(alert)
        }

        requestWidgetUpdate()
        return when {
            alert.isWeatherChange && isNewAlert -> FcmAlertHandlingResult.WEATHER_HANDLED
            alert.isWeatherChange -> FcmAlertHandlingResult.WEATHER_DUPLICATE
            isNewAlert -> FcmAlertHandlingResult.HANDLED
            else -> FcmAlertHandlingResult.DUPLICATE
        }
    }
}
