package com.example.pokemonalertsv2.widget

import android.content.Context
import android.location.Location
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.util.CachedLocationProvider
import kotlinx.coroutines.flow.first

internal object WidgetAlertLoader {
    data class LoadedAlerts(
        val alerts: List<PokemonAlert>,
        val cadenceAlerts: List<PokemonAlert>,
        val location: Location?
    )

    suspend fun load(
        context: Context,
        appWidgetId: Int,
        fallbackLocation: Location? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): LoadedAlerts {
        val repo = PokemonAlertsRepository.create(context)
        val alerts = runCatching { repo.getLocalAlerts() }.getOrElse { emptyList() }
        val dismissedIds = runCatching {
            repo.alertPreferences.dismissedAlertIds.first()
        }.getOrElse { emptySet() }
        val selectedArea = runCatching { repo.alertPreferences.selectedArea.first() }.getOrElse { "All" }
        val maxDistance = runCatching { repo.alertPreferences.maxDistance.first() }.getOrElse { 0 }
        val filterTypes = WidgetFilterPrefs.getFilters(context, appWidgetId)
        val location = runCatching {
            CachedLocationProvider.get(context)
        }.getOrNull() ?: fallbackLocation
        val criteria = WidgetAlertFilter.Criteria(
            dismissedAlertIds = dismissedIds,
            selectedArea = selectedArea,
            maxDistanceKm = maxDistance,
            widgetFilterTypes = filterTypes,
            nowMillis = nowMillis
        )

        val visibleAlerts = WidgetAlertSnapshotStore.resolve(
            appWidgetId = appWidgetId,
            alerts = alerts,
            criteria = criteria,
            origin = location?.let { WidgetAlertFilter.originFrom(it) }
        )
        val cadenceAlerts = WidgetAlertFilter.filterWithoutDistance(
            alerts = alerts,
            criteria = criteria
        ).also { WidgetAlertSnapshotStore.updateCadence(appWidgetId, it) }

        WidgetAlertSnapshotStore.publishRenderSnapshot(
            appWidgetId = appWidgetId,
            alerts = visibleAlerts,
            location = location
        )

        return LoadedAlerts(
            alerts = visibleAlerts,
            cadenceAlerts = cadenceAlerts,
            location = location
        )
    }
}
