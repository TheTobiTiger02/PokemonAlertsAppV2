package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.data.AffectedAlert
import com.example.pokemonalertsv2.data.PokemonAlert
import java.util.Locale

internal fun weatherTransitionLabel(alert: PokemonAlert): String? {
    val from = alert.weatherFrom?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val to = alert.weatherTo?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return "$from → $to"
}

internal fun affectedAlertSummary(alert: AffectedAlert): String {
    val pokemon = listOfNotNull(
        alert.pokemon?.trim()?.takeIf { it.isNotEmpty() },
        alert.pokemonForm?.trim()?.takeIf { it.isNotEmpty() }
    ).joinToString(" ")
        .takeIf { it.isNotEmpty() }
        ?: alert.name?.trim()?.takeIf { it.isNotEmpty() }
        ?: "Affected Pokémon"

    return buildList {
        add(pokemon)
        alert.cp?.let { add("CP $it") }
        alert.type
            .orEmpty()
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
            ?.let(::add)
    }.joinToString(" • ")
}

internal fun affectedAlertCardLines(
    alert: PokemonAlert,
    limit: Int = 3
): List<String> = alert.affectedAlerts
    .take(limit.coerceAtLeast(0))
    .map(::affectedAlertSummary)

internal fun affectedAlertOverflowCount(
    alert: PokemonAlert,
    limit: Int = 3
): Int = (alert.affectedAlerts.size - limit.coerceAtLeast(0)).coerceAtLeast(0)

internal fun invalidationBadgeText(alert: PokemonAlert): String? =
    "Invalidated by weather".takeIf { alert.isInvalidated }

internal fun PokemonAlert.matchesAlertSearch(rawQuery: String): Boolean {
    val query = rawQuery.trim().lowercase(Locale.ROOT)
    if (query.isEmpty()) return true

    return name.lowercase(Locale.ROOT).contains(query) ||
        pokemon?.lowercase(Locale.ROOT)?.contains(query) == true ||
        cleanPokemonName.lowercase(Locale.ROOT).contains(query) ||
        locationDisplay?.lowercase(Locale.ROOT)?.contains(query) == true ||
        affectedAlerts.any { affected ->
            affected.name?.lowercase(Locale.ROOT)?.contains(query) == true ||
                affected.pokemon?.lowercase(Locale.ROOT)?.contains(query) == true ||
                affected.pokemonForm?.lowercase(Locale.ROOT)?.contains(query) == true ||
                affected.area?.lowercase(Locale.ROOT)?.contains(query) == true ||
                affected.type.orEmpty().any {
                    it.lowercase(Locale.ROOT).contains(query)
                }
        }
}
