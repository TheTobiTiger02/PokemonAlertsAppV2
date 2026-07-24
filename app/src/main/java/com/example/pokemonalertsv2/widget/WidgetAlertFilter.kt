package com.example.pokemonalertsv2.widget

import android.location.Location
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.alerts.resolveAlertVisualStyle
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
        distanceMeters: (origin: Origin, alert: PokemonAlert) -> Float? = ::directDistanceMeters
    ): Result {
        val distanceFilterApplied = criteria.maxDistanceKm <= 0 || origin != null

        return Result.Filtered(
            alerts.filter { alert ->
                isVisible(
                    alert = alert,
                    criteria = criteria,
                    origin = origin,
                    distanceMeters = distanceMeters,
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
        applyDistance: Boolean = true
    ): Boolean {
        val end = TimeUtils.parseEndTimeToMillis(alert.endTime) ?: Long.MAX_VALUE
        if (end <= criteria.nowMillis) return false
        if (alert.isInvalidated) return false
        if (alert.uniqueId in criteria.dismissedAlertIds) return false
        if (criteria.selectedArea != "All" && alert.area != criteria.selectedArea) return false
        if (!matchesWidgetTypes(alert, criteria.widgetFilterTypes)) return false

        if (applyDistance && criteria.maxDistanceKm > 0 && origin != null) {
            val meters = distanceMeters(origin, alert)
            if (meters != null && !meters.isNaN() && meters > criteria.maxDistanceKm * 1000) {
                return false
            }
        }

        return true
    }

    private fun matchesWidgetTypes(alert: PokemonAlert, filterTypes: Set<String>): Boolean {
        if (filterTypes.isEmpty()) return true
        val semanticTypes = buildSet {
            addAll(alert.type.orEmpty())
            add(resolveAlertVisualStyle(alert).label)
            if (alert.isPerfect) add("Hundo")
            if (alert.isNundo) add("Nundo")
            if (alert.isWeatherChange) add("Weather")
        }
        if (semanticTypes.isEmpty()) return true
        return semanticTypes.any { type ->
            filterTypes.any { filter ->
                type.contains(filter, ignoreCase = true) ||
                    filter.contains(type, ignoreCase = true)
            }
        }
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
