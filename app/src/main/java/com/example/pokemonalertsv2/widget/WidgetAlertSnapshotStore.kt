package com.example.pokemonalertsv2.widget

import android.location.Location
import androidx.annotation.VisibleForTesting
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.util.TimeUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal object WidgetAlertSnapshotStore {
    data class RenderSnapshot(
        val generation: Long,
        val alerts: List<PokemonAlert>,
        val location: Location?,
        val distanceUnavailable: Boolean
    )

    private val cadenceSnapshots = ConcurrentHashMap<Int, List<PokemonAlert>>()
    private val renderSnapshots = ConcurrentHashMap<Int, RenderSnapshot>()
    private val nextGeneration = AtomicLong(0L)

    fun resolve(
        alerts: List<PokemonAlert>,
        criteria: WidgetAlertFilter.Criteria,
        origin: WidgetAlertFilter.Origin?
    ): WidgetAlertFilter.Result.Filtered =
        WidgetAlertFilter.filterAlerts(alerts, criteria, origin) as WidgetAlertFilter.Result.Filtered

    fun remove(appWidgetId: Int) {
        cadenceSnapshots.remove(appWidgetId)
        renderSnapshots.remove(appWidgetId)
    }

    fun clear() {
        cadenceSnapshots.clear()
        renderSnapshots.clear()
    }

    fun publishRenderSnapshot(
        appWidgetId: Int,
        alerts: List<PokemonAlert>,
        location: Location?,
        distanceUnavailable: Boolean = false
    ): RenderSnapshot {
        return RenderSnapshot(
            generation = nextGeneration.incrementAndGet(),
            alerts = alerts.toList(),
            location = location?.let(::Location),
            distanceUnavailable = distanceUnavailable
        ).also { renderSnapshots[appWidgetId] = it }
    }

    fun currentRenderSnapshot(
        appWidgetId: Int,
        expectedGeneration: Long? = null
    ): RenderSnapshot? =
        renderSnapshots[appWidgetId]?.let { snapshot ->
            if (expectedGeneration != null && snapshot.generation != expectedGeneration) return null
            snapshot.copy(
                alerts = snapshot.alerts.toList(),
                location = snapshot.location?.let(::Location)
            )
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
    fun clearForTesting() {
        clear()
    }
}
