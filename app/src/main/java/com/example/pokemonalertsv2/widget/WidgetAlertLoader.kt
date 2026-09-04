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
    /**
     * Both widgets tick every minute, but the GPS fix and the walking-route batch behind
     * each tick are the expensive part -- and while walking, the repository's 75 m origin
     * rule turns every tick into a real request. A young snapshot whose alert set still
     * matches reuses its location and routes, so cheap ticks only re-render the view.
     * Push waves and list changes bypass the gate via the id comparison.
     */
    internal const val ROUTE_DATA_MAX_AGE_MILLIS = 2 * 60 * 1000L

    /** Pure so the gate is unit-testable without an Android fixture. */
    internal fun shouldReuseRouteData(
        snapshotRouteDataAtMillis: Long,
        snapshotAlertIds: Set<String>,
        currentAlertIds: Set<String>,
        nowMillis: Long,
        maxAgeMillis: Long = ROUTE_DATA_MAX_AGE_MILLIS
    ): Boolean =
        nowMillis - snapshotRouteDataAtMillis < maxAgeMillis &&
            snapshotAlertIds == currentAlertIds

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
        // A tick inside the freshness window re-uses the previous fix and routes so the
        // per-minute countdown redraw costs no GPS and no backend call.
        val previousSnapshot = WidgetAlertSnapshotStore.currentRenderSnapshot(appWidgetId)
        val reuseRouteData = shouldReuseRouteData(
            snapshotRouteDataAtMillis = previousSnapshot?.routeDataAtMillis ?: 0L,
            snapshotAlertIds = previousSnapshot?.alerts?.mapTo(HashSet()) { it.uniqueId } ?: emptySet(),
            currentAlertIds = alerts.mapTo(HashSet()) { it.uniqueId },
            nowMillis = nowMillis
        )
        val location = if (reuseRouteData) {
            previousSnapshot?.location
        } else runCatching {
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

        // Pre-filter candidate alerts before requesting walking routes: alerts that are
        // unreachable in time at any plausible walking speed can never match anyway.
        val candidateAlerts = WidgetAlertFilter.filterWithoutDistance(alerts, criteria).filter { alert ->
            val direct = origin?.let { WidgetAlertFilter.directDistanceMeters(it, alert) }
            if (criteria.maxDistanceKm > 0 && direct != null && direct > criteria.maxDistanceKm * 1000f) {
                return@filter false
            }
            val definition = criteria.filterDefinition
            if (definition != null && direct != null) {
                if (definition.maxDistanceKm > 0 && direct > definition.maxDistanceKm * 1000f) return@filter false
                if (definition.maxWalkingMinutes > 0 && direct > definition.maxWalkingMinutes * 60L * 1.8f) return@filter false
            }
            true
        }
        val topCandidates = if (origin != null) {
            candidateAlerts.sortedBy { WidgetAlertFilter.directDistanceMeters(origin, it) ?: Float.MAX_VALUE }
        } else {
            candidateAlerts
        }

        val walkingRoutes = when {
            reuseRouteData -> previousSnapshot?.walkingRoutes.orEmpty()
            location != null -> WalkingRouteRepository.getInstance().getWalkingRoutes(
                origin = location,
                alerts = topCandidates,
                timeoutMillis = WalkingRouteRepository.BACKGROUND_TIMEOUT_MILLIS
            )
            else -> emptyMap()
        }
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
            walkingRoutes = walkingRoutes,
            // Reuse republishes with the original stamp, or the window would never close.
            routeDataAtMillis = if (reuseRouteData) {
                previousSnapshot?.routeDataAtMillis ?: nowMillis
            } else {
                nowMillis
            }
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
