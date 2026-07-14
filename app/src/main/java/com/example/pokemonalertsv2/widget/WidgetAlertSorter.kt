package com.example.pokemonalertsv2.widget

import android.location.Location
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.SortPreference
import com.example.pokemonalertsv2.util.TimeUtils

internal object WidgetAlertSorter {
    fun sort(
        alerts: List<PokemonAlert>,
        preference: SortPreference,
        origin: WidgetAlertFilter.Origin?,
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
            else alerts.sortedBy { alert ->
                distanceMeters(origin, alert)
                    ?.takeUnless { it.isNaN() || it.isInfinite() || it < 0f }
                    ?: Float.MAX_VALUE
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
