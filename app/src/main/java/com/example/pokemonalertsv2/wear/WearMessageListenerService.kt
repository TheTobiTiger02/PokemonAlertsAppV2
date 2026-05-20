package com.example.pokemonalertsv2.wear

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.notifications.AlertSnoozeScheduler
import com.example.pokemonalertsv2.ui.alerts.AlertDetailActivity
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WearMessageListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        if (messageEvent.path !in SUPPORTED_PATHS) return

        val alertKey = String(messageEvent.data)
        Log.d(TAG, "Received ${messageEvent.path} for $alertKey")

        CoroutineScope(Dispatchers.IO).launch {
            val repository = PokemonAlertsRepository.create(applicationContext)
            val alerts = runCatching { repository.getLocalAlerts() }.getOrElse { emptyList() }
            val alert = resolveAlertByKey(alerts, alertKey) ?: return@launch

            when (messageEvent.path) {
                PATH_OPEN_ALERT -> openAlert(alert)
                PATH_NAVIGATE_ALERT -> navigateToAlert(alert)
                PATH_SNOOZE_ALERT -> {
                    if (!shouldScheduleWearSnooze(alert)) return@launch
                    val minutes = repository.alertPreferences.snoozeDuration.first()
                    AlertSnoozeScheduler.schedule(applicationContext, alert, minutes)
                }
            }
        }
    }

    private fun openAlert(alert: PokemonAlert) {
        startActivity(AlertDetailActivity.createIntent(this, alert))
    }

    private fun navigateToAlert(alert: PokemonAlert) {
        val latitude = alert.latitude ?: return
        val longitude = alert.longitude ?: return
        val mapsIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=$latitude,$longitude&mode=w")
        ).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(mapsIntent)
        } catch (_: ActivityNotFoundException) {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude&travelmode=walking")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { startActivity(browserIntent) }
        }
    }

    companion object {
        private const val TAG = "WearMessageListener"
        const val PATH_OPEN_ALERT = "/open_alert"
        const val PATH_NAVIGATE_ALERT = "/navigate_alert"
        const val PATH_SNOOZE_ALERT = "/snooze_alert"
        private val SUPPORTED_PATHS = setOf(PATH_OPEN_ALERT, PATH_NAVIGATE_ALERT, PATH_SNOOZE_ALERT)

        internal fun resolveAlertByKey(alerts: List<PokemonAlert>, key: String): PokemonAlert? {
            return alerts.firstOrNull { it.uniqueId == key && AlertSnoozeScheduler.isAlertActive(it) }
                ?: alerts.firstOrNull { it.name == key && AlertSnoozeScheduler.isAlertActive(it) }
                ?: alerts.firstOrNull { it.uniqueId == key }
                ?: alerts.firstOrNull { it.name == key }
        }

        internal fun shouldScheduleWearSnooze(
            alert: PokemonAlert,
            nowMillis: Long = System.currentTimeMillis()
        ): Boolean = AlertSnoozeScheduler.isAlertActive(alert, nowMillis)
    }
}
