package com.example.pokemonalertsv2.widget

import android.location.Location
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.SortPreference
import com.example.pokemonalertsv2.data.isDirectlyInRange
import com.example.pokemonalertsv2.util.TimeUtils
import com.example.pokemonalertsv2.util.WalkingRouteInfo

internal object WidgetAlertSorter {
    fun sort(
        alerts: List<PokemonAlert>,
        preference: SortPreference,
        origin: WidgetAlertFilter.Origin?,
        walkingRoutes: Map<String, WalkingRouteInfo> = emptyMap(),
        distanceMeters: (WidgetAlertFilter.Origin, PokemonAlert) -> Float? = ::distanceMeters
    ): List<PokemonAlert> = when (preference) {
        SortPreference.POSTED_TIME -> alerts.sortedWith(
            compareByDescending<PokemonAlert> { it.id?.toLong() ?: Long.MIN_VALUE }
                .thenByDescending { TimeUtils.parseEndTimeToMillis(it.endTime) ?: Long.MIN_VALUE }
        )

        SortPreference.TIME_REMAINING -> alerts.sortedBy {
            TimeUtils.parseEndTimeToMillis(it.endTime) ?: Long.MAX_VALUE
        }

        SortPreference.DISTANCE -> {
            if (origin == null) alerts
            else {
                class SortKey(
                    val alert: PokemonAlert,
                    val isInRange: Boolean,
                    val directDistance: Float,
                    val hasWalkingRoute: Boolean,
                    val effectiveDistance: Float
                )
                val keys = alerts.map { alert ->
                    val direct = distanceMeters(origin, alert)
                    val inRange = alert.isDirectlyInRange(direct)
                    val route = walkingRoutes[alert.uniqueId]
                    val effective = route?.distanceMeters?.toFloat()
                        ?: direct?.takeUnless { it.isNaN() || it.isInfinite() || it < 0f }
                        ?: Float.MAX_VALUE
                    SortKey(
                        alert = alert,
                        isInRange = inRange,
                        directDistance = direct ?: Float.MAX_VALUE,
                        hasWalkingRoute = route != null,
                        effectiveDistance = effective
                    )
                }
                keys.sortedWith { a, b ->
                    if (a.isInRange != b.isInRange) return@sortedWith if (a.isInRange) -1 else 1
                    if (a.isInRange) {
                        val cmp = a.directDistance.compareTo(b.directDistance)
                        if (cmp != 0) return@sortedWith cmp
                        return@sortedWith a.effectiveDistance.compareTo(b.effectiveDistance)
                    }
                    if (a.hasWalkingRoute != b.hasWalkingRoute) {
                        return@sortedWith if (a.hasWalkingRoute) -1 else 1
                    }
                    a.effectiveDistance.compareTo(b.effectiveDistance)
                }.map { it.alert }
            }
        }

        SortPreference.NAME -> alerts.sortedBy { it.name.lowercase() }
    }

    private fun distanceMeters(
        origin: WidgetAlertFilter.Origin,
        alert: PokemonAlert
    ): Float? {
        val latitude = alert.latitude ?: return null
        val longitude = alert.longitude ?: return null
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        if (latitude == 0.0 && longitude == 0.0) return null

        val result = FloatArray(1)
        return runCatching {
            Location.distanceBetween(
                origin.latitude,
                origin.longitude,
                latitude,
                longitude,
                result
            )
            result[0]
        }.getOrNull()
    }
}
