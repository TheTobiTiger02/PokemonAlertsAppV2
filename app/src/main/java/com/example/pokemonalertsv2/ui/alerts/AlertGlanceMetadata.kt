package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.data.PokemonAlert

/** Exact encounter CP to look for. Raid hundo ranges are intentionally excluded. */
val PokemonAlert.displayCp: Int?
    get() = when {
        isWeatherChange -> newCp?.takeIf { it > 0 }
        else -> cp?.takeIf { it > 0 }
    }

fun buildAlertGlanceMetadata(
    alert: PokemonAlert,
    distanceText: String? = null,
    walkingText: String? = null,
    includeCategory: Boolean = true,
    separator: String = " • "
): String = buildList {
    alert.displayCp?.let { add("CP $it") }
    distanceText?.takeIf { it.isNotBlank() }?.let(::add)
    walkingText?.takeIf { it.isNotBlank() }?.let(::add)
    if (includeCategory) add(resolveAlertVisualStyle(alert).label)
}.joinToString(separator)
