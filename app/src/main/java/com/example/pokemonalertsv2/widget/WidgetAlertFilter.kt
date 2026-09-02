package com.example.pokemonalertsv2.widget

import android.location.Location
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.AlertFilterMatcher
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterMatchContext
import com.example.pokemonalertsv2.ui.alerts.alertCategories
import com.example.pokemonalertsv2.util.TimeUtils

internal object WidgetAlertFilter {
    data class Origin(
        val latitude: Double,
        val longitude: Double
    )

    data class Criteria(
        val dismissedAlertIds: Set<String>,
        val selectedArea: String,
        val maxDistanceKm: Int,
        val widgetFilterTypes: Set<String>,
        val filterDefinition: FilterDefinition? = null,
        val nowMillis: Long = System.currentTimeMillis()
    )

    sealed class Result {
        data class Filtered(
            val alerts: List<PokemonAlert>,
            val distanceFilterApplied: Boolean
        ) : Result()
    }

    fun filterAlerts(
        alerts: List<PokemonAlert>,
        criteria: Criteria,
        origin: Origin?,
        distanceMeters: (origin: Origin, alert: PokemonAlert) -> Float? = ::directDistanceMeters,
        walkingDurationSeconds: (alert: PokemonAlert) -> Long? = { null }
    ): Result {
        val distanceFilterApplied = criteria.maxDistanceKm <= 0 || origin != null

        return Result.Filtered(
            alerts.filter { alert ->
                isVisible(
                    alert = alert,
                    criteria = criteria,
                    origin = origin,
                    distanceMeters = distanceMeters,
                    walkingDurationSeconds = walkingDurationSeconds,
                    applyDistance = distanceFilterApplied
                )
            },
            distanceFilterApplied = distanceFilterApplied
        )
    }

    fun filterWithoutDistance(
        alerts: List<PokemonAlert>,
        criteria: Criteria
    ): List<PokemonAlert> {
        return alerts.filter { alert ->
            isVisible(
                alert = alert,
                criteria = criteria,
                origin = null,
                distanceMeters = { _, _ -> null },
                walkingDurationSeconds = { null },
                applyDistance = false
            )
        }
    }

    fun originFrom(location: Location): Origin = Origin(
        latitude = location.latitude,
        longitude = location.longitude
    )

    private fun isVisible(
        alert: PokemonAlert,
        criteria: Criteria,
        origin: Origin?,
        distanceMeters: (origin: Origin, alert: PokemonAlert) -> Float?,
        walkingDurationSeconds: (alert: PokemonAlert) -> Long?,
        applyDistance: Boolean = true
    ): Boolean {
        val end = TimeUtils.parseEndTimeToMillis(alert.endTime) ?: Long.MAX_VALUE
        if (end <= criteria.nowMillis) return false
        if (alert.isInvalidated) return false
        if (alert.uniqueId in criteria.dismissedAlertIds) return false
        if (criteria.selectedArea != "All" && alert.area != criteria.selectedArea) return false
        if (criteria.filterDefinition == null && !matchesWidgetTypes(alert, criteria.widgetFilterTypes)) return false

        var effectiveDistance: Float? = null
        if (applyDistance && criteria.maxDistanceKm > 0 && origin != null) {
            val meters = distanceMeters(origin, alert)
            effectiveDistance = meters
            if (meters != null && !meters.isNaN() && meters > criteria.maxDistanceKm * 1000) {
                return false
            }
        }

        criteria.filterDefinition?.let { definition ->
            if (effectiveDistance == null && applyDistance && origin != null) {
                effectiveDistance = distanceMeters(origin, alert)
            }
            if (!AlertFilterMatcher.matches(
                    alert,
                    definition,
                    FilterMatchContext(effectiveDistance, walkingDurationSeconds(alert))
                )
            ) {
                return false
            }
        }

        return true
    }

    /**
     * [filterTypes] holds [com.example.pokemonalertsv2.ui.alerts.AlertCategory] names
     * (muted categories are absent — same "empty means everything" rule as the feed and map).
     */
    private fun matchesWidgetTypes(alert: PokemonAlert, filterTypes: Set<String>): Boolean {
        if (filterTypes.isEmpty()) return true
        val categories = alert.alertCategories()
        if (categories.isEmpty()) return true
        return categories.none { it.name in filterTypes }
    }

    fun directDistanceMeters(origin: Origin, alert: PokemonAlert): Float? {
        val latitude = alert.latitude ?: return null
        val longitude = alert.longitude ?: return null
        val results = FloatArray(1)
        runCatching {
            Location.distanceBetween(origin.latitude, origin.longitude, latitude, longitude, results)
        }.getOrNull() ?: return null
        return results.getOrNull(0)?.takeUnless { it.isNaN() }
    }
}
