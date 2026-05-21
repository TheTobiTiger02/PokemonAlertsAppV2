package com.example.pokemonalertsv2.fcm

import android.content.Context
import android.util.Log
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.notifications.AlertNotifier
import com.example.pokemonalertsv2.widget.AlertsWidgetProvider

object FcmAlertHandler {
    private const val TAG = "FcmAlertHandler"

    suspend fun handle(context: Context, data: Map<String, String>) {
        val appContext = context.applicationContext
        val payload = FcmAlertPayloadParser.parse(data)
        if (payload == null) {
            Log.w(TAG, "Ignoring FCM alert message without a parseable alert payload")
            return
        }

        val repository = PokemonAlertsRepository.create(appContext)
        repository.upsertAlert(payload.alert)
        repository.markAlertsAsSeen(listOf(payload.alert))

        AlertNotifier.notifyAlerts(
            context = appContext,
            alerts = listOf(payload.alert)
        )

        AlertsWidgetProvider.updateFromWorker(appContext)
    }
}
