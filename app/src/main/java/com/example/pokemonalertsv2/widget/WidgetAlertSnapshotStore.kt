package com.example.pokemonalertsv2.widget

import androidx.annotation.VisibleForTesting
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.util.TimeUtils
import java.util.concurrent.ConcurrentHashMap

internal object WidgetAlertSnapshotStore {
    private val snapshots = ConcurrentHashMap<Int, List<PokemonAlert>>()
    private val cadenceSnapshots = ConcurrentHashMap<Int, List<PokemonAlert>>()

    fun resolve(
        appWidgetId: Int,
        alerts: List<PokemonAlert>,
        criteria: WidgetAlertFilter.Criteria,
        origin: WidgetAlertFilter.Origin?
    ): List<PokemonAlert> {
        return when (val result = WidgetAlertFilter.filterAlerts(alerts, criteria, origin)) {
            is WidgetAlertFilter.Result.Filtered -> {
                result.alerts.also { snapshots[appWidgetId] = it }
            }
            WidgetAlertFilter.Result.PreservePrevious -> {
                WidgetAlertFilter.filterWithoutDistance(
                    alerts = snapshots[appWidgetId].orEmpty(),
                    criteria = criteria
                ).also { snapshots[appWidgetId] = it }
            }
        }
    }

    fun remove(appWidgetId: Int) {
        snapshots.remove(appWidgetId)
        cadenceSnapshots.remove(appWidgetId)
    }

    fun clear() {
        snapshots.clear()
        cadenceSnapshots.clear()
    }

    fun updateCadence(appWidgetId: Int, alerts: List<PokemonAlert>) {
        cadenceSnapshots[appWidgetId] = alerts
    }

    fun nextExpirationMillis(nowMillis: Long): Long? {
        return cadenceSnapshots.values
            .asSequence()
            .flatten()
            .mapNotNull { TimeUtils.parseEndTimeToMillis(it.endTime) }
            .filter { it > nowMillis }
            .minOrNull()
    }

    @VisibleForTesting
    fun putForTesting(appWidgetId: Int, alerts: List<PokemonAlert>) {
        snapshots[appWidgetId] = alerts
    }

    @VisibleForTesting
    fun clearForTesting() {
        clear()
    }
}
