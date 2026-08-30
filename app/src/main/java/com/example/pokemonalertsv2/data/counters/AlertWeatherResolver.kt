package com.example.pokemonalertsv2.data.counters

import com.example.pokemonalertsv2.data.CurrentWeatherResponse
import com.example.pokemonalertsv2.data.PokemonAlert

/** A weather reading plus whether its source vouched for it. */
data class ResolvedWeather(val weather: PokebattlerWeather, val confirmed: Boolean)

/**
 * The weather to rank a raid under, in order of trustworthiness.
 *
 * 1. what the alert itself reports, 2. a weather-change alert's destination weather, and 3. the
 * area's last known weather from `/api/current-weather`. Returns null when nothing is known, so
 * the caller keeps the user's saved default rather than guessing.
 *
 * An unconfirmed reading is still returned — the last known weather beats assuming none — and
 * carries [ResolvedWeather.confirmed] false so the screen can say so.
 *
 * @param areaWeather the fallback lookup, only consulted when the alert carries no weather.
 */
suspend fun resolveAlertWeather(
    alert: PokemonAlert,
    areaWeather: suspend (String?) -> CurrentWeatherResponse?
): ResolvedWeather? {
    PokebattlerWeather.fromAlertWeather(alert.currentWeather)?.let {
        return ResolvedWeather(it, alert.currentWeatherConfirmed != false)
    }
    PokebattlerWeather.fromAlertWeather(alert.weatherTo ?: alert.weatherFrom)?.let {
        // A weather-change alert states the transition it just observed.
        return ResolvedWeather(it, confirmed = true)
    }
    val area = areaWeather(alert.area) ?: return null
    return PokebattlerWeather.fromAlertWeather(area.currentWeather)
        ?.let { ResolvedWeather(it, area.confirmed) }
}
