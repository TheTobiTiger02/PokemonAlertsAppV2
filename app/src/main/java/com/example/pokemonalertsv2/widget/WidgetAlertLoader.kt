package com.example.pokemonalertsv2.widget

import android.content.Context
import android.location.Location
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.data.SortPreference
import com.example.pokemonalertsv2.util.CachedLocationProvider
import com.example.pokemonalertsv2.util.WalkingRouteRepository
import kotlinx.coroutines.flow.first

internal object WidgetAlertLoader {
    data class LoadedAlerts(
        val alerts: List<PokemonAlert>,
        val cadenceAlerts: List<PokemonAlert>,
        val location: Location?,
        val distanceUnavailable: Boolean,
        val generation: Long,
        val state: WidgetLoadState
    )

    suspend fun load(
        context: Context,
        appWidgetId: Int,
        fallbackLocation: Location? = null,
        nowMillis: Long = System.currentTimeMillis(),
        highAccuracyLocation: Boolean = false
    ): LoadedAlerts {
        val repo = PokemonAlertsRepository.create(context)
        val alertsResult = runCatching { repo.getLocalAlerts() }
        val alerts = alertsResult.getOrElse { emptyList() }
        if (alertsResult.isFailure) {
            WidgetAlertSnapshotStore.currentRenderSnapshot(appWidgetId)?.let { previous ->
                return LoadedAlerts(
                    alerts = previous.alerts,
                    cadenceAlerts = previous.alerts,
                    location = previous.location ?: fallbackLocation,
                    distanceUnavailable = previous.distanceUnavailable,
                    generation = previous.generation,
                    state = WidgetLoadState.ERROR
                )
            }
        }
        val dismissedIds = runCatching {
            repo.alertPreferences.dismissedAlertIds.first()
        }.getOrElse { emptySet() }
        val selectedArea = runCatching { repo.alertPreferences.selectedArea.first() }.getOrElse { "All" }
        val maxDistance = runCatching { repo.alertPreferences.maxDistance.first() }.getOrElse { 0 }
        val sortPreference = runCatching { repo.alertPreferences.sortPreference.first() }
            .getOrElse { com.example.pokemonalertsv2.data.SortPreference.POSTED_TIME }
        val storedConfiguration = WidgetConfigurationStore.get(context, appWidgetId)
        val configuration = if (storedConfiguration.filterAssignment == null) {
            storedConfiguration.copy(filterAssignment = com.example.pokemonalertsv2.data.FilterAssignment.local(storedConfiguration.legacyFilterDefinition(selectedArea, maxDistance)))
                .also { WidgetConfigurationStore.save(context, appWidgetId, it) }
        } else storedConfiguration
        val filterDocument = runCatching { repo.alertPreferences.filterStateDocument.first() }.getOrNull()
        val unifiedDefinition = configuration.filterAssignment?.let { assignment ->
            filterDocument?.let(assignment::resolve) ?: assignment.definition
        }
        val filterTypes = configuration.selectedAlertTypes
        // A widget pinned to one area keeps its own view; otherwise it follows the app.
        val effectiveArea = when (val areaMode = configuration.area) {
            WidgetAreaMode.InheritApp -> selectedArea
            is WidgetAreaMode.Fixed -> areaMode.area
        }
        val location = runCatching {
            CachedLocationProvider.get(
                context = context,
                timeoutMs = if (highAccuracyLocation) 6_000 else 4_000,
                highAccuracy = highAccuracyLocation,
                forceRefresh = highAccuracyLocation
            )
        }.getOrNull() ?: fallbackLocation
        val criteria = WidgetAlertFilter.Criteria(
            dismissedAlertIds = dismissedIds,
            selectedArea = if (unifiedDefinition == null) effectiveArea else "All",
            maxDistanceKm = if (unifiedDefinition == null) when (val mode = configuration.distance) {
                    WidgetDistanceMode.InheritApp -> maxDistance
                    WidgetDistanceMode.Unlimited -> 0
                    is WidgetDistanceMode.Fixed -> mode.kilometers
                } else 0,
            widgetFilterTypes = filterTypes,
            filterDefinition = unifiedDefinition,
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
        val effectiveSort = when (configuration.priority) {
            WidgetPriority.APP_DEFAULT -> sortPreference
            WidgetPriority.NEAREST -> if (origin == null) SortPreference.TIME_REMAINING else SortPreference.DISTANCE
            WidgetPriority.ENDING_SOON -> SortPreference.TIME_REMAINING
            WidgetPriority.NEWEST -> SortPreference.POSTED_TIME
        }
        val visibleAlerts = WidgetAlertSorter.sort(
            alerts = resolvedAlerts.alerts,
            preference = effectiveSort,
            origin = origin,
            walkingRoutes = walkingRoutes
        )
        val cadenceAlerts = WidgetAlertSorter.sort(
            alerts = WidgetAlertFilter.filterWithoutDistance(
                alerts = alerts,
                criteria = criteria
            ),
            preference = effectiveSort,
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
            generation = renderSnapshot.generation,
            state = when {
                alertsResult.isFailure -> WidgetLoadState.ERROR
                configuration.priority == WidgetPriority.NEAREST && location == null ->
                    WidgetLoadState.LOCATION_UNAVAILABLE
                visibleAlerts.isEmpty() -> WidgetLoadState.EMPTY
                else -> WidgetLoadState.CONTENT
            }
        )
    }
}
