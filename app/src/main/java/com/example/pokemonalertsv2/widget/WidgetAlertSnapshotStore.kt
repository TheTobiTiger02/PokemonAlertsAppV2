package com.example.pokemonalertsv2.widget

import android.location.Location
import androidx.annotation.VisibleForTesting
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.WalkingRouteInfo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal object WidgetAlertSnapshotStore {
    data class RenderSnapshot(
        val generation: Long,
        val alerts: List<PokemonAlert>,
        val location: Location?,
        val distanceUnavailable: Boolean,
        val walkingRoutes: Map<String, WalkingRouteInfo> = emptyMap()
    )

    private val cadenceSnapshots = ConcurrentHashMap<Int, List<PokemonAlert>>()
    private val renderSnapshots = ConcurrentHashMap<Int, RenderSnapshot>()
    private val nextGeneration = AtomicLong(0L)

    fun resolve(
        alerts: List<PokemonAlert>,
        criteria: WidgetAlertFilter.Criteria,
        origin: WidgetAlertFilter.Origin?,
        walkingRoutes: Map<String, WalkingRouteInfo> = emptyMap()
    ): WidgetAlertFilter.Result.Filtered =
        WidgetAlertFilter.filterAlerts(
            alerts,
            criteria,
            origin
        ) { routeOrigin, alert ->
            walkingRoutes[alert.uniqueId]?.distanceMeters?.toFloat()
                ?: WidgetAlertFilter.directDistanceMeters(routeOrigin, alert)
        } as WidgetAlertFilter.Result.Filtered

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
        distanceUnavailable: Boolean = false,
        walkingRoutes: Map<String, WalkingRouteInfo> = emptyMap()
    ): RenderSnapshot {
        return RenderSnapshot(
            generation = nextGeneration.incrementAndGet(),
            alerts = alerts.toList(),
            location = location?.let(::Location),
            distanceUnavailable = distanceUnavailable,
            walkingRoutes = walkingRoutes.toMap()
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
                location = snapshot.location?.let(::Location),
                walkingRoutes = snapshot.walkingRoutes.toMap()
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
