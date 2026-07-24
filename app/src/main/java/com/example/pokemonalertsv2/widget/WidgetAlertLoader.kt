package com.example.pokemonalertsv2.widget

import android.content.Context
import android.location.Location
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.util.CachedLocationProvider
import com.example.pokemonalertsv2.util.WalkingRouteRepository
import kotlinx.coroutines.flow.first

internal object WidgetAlertLoader {
    data class LoadedAlerts(
        val alerts: List<PokemonAlert>,
        val cadenceAlerts: List<PokemonAlert>,
        val location: Location?,
        val distanceUnavailable: Boolean,
        val generation: Long
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
        val sortPreference = runCatching { repo.alertPreferences.sortPreference.first() }
            .getOrElse { com.example.pokemonalertsv2.data.SortPreference.POSTED_TIME }
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

        val origin = location?.let { WidgetAlertFilter.originFrom(it) }
        val walkingRoutes = location?.let {
            WalkingRouteRepository.getInstance().getWalkingRoutes(
                origin = it,
                alerts = alerts,
                timeoutMillis = WalkingRouteRepository.BACKGROUND_TIMEOUT_MILLIS
            )
        }.orEmpty()
        val resolvedAlerts = WidgetAlertSnapshotStore.resolve(
            alerts = alerts,
            criteria = criteria,
            origin = origin,
            walkingRoutes = walkingRoutes
        )
        val visibleAlerts = WidgetAlertSorter.sort(
            alerts = resolvedAlerts.alerts,
            preference = sortPreference,
            origin = origin,
            walkingRoutes = walkingRoutes
        )
        val cadenceAlerts = WidgetAlertSorter.sort(
            alerts = WidgetAlertFilter.filterWithoutDistance(
                alerts = alerts,
                criteria = criteria
            ),
            preference = sortPreference,
            origin = origin,
            walkingRoutes = walkingRoutes
        ).also { WidgetAlertSnapshotStore.updateCadence(appWidgetId, it) }

        val renderSnapshot = WidgetAlertSnapshotStore.publishRenderSnapshot(
            appWidgetId = appWidgetId,
            alerts = visibleAlerts,
            location = location,
            distanceUnavailable = !resolvedAlerts.distanceFilterApplied,
            walkingRoutes = walkingRoutes
        )

        return LoadedAlerts(
            alerts = visibleAlerts,
            cadenceAlerts = cadenceAlerts,
            location = location,
            distanceUnavailable = renderSnapshot.distanceUnavailable,
            generation = renderSnapshot.generation
        )
    }
}
