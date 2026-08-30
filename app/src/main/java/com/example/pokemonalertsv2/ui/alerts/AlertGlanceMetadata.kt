package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.data.PokemonAlert

internal data class QuestAlertPresentation(
    val task: String?,
    val reward: String?,
    val requiresAr: Boolean
)

/** Shared, whitespace-safe quest copy for cards, map details, and widgets. */
internal fun questAlertPresentation(alert: PokemonAlert): QuestAlertPresentation? {
    if (!alert.hasTypeContaining("quest")) return null
    return QuestAlertPresentation(
        task = alert.questTask?.trim()?.takeIf(String::isNotEmpty),
        reward = alert.questReward?.trim()?.takeIf(String::isNotEmpty),
        requiresAr = alert.requiresAR == true
    )
}

/** Exact encounter CP to look for. Raid hundo ranges are intentionally excluded. */
val PokemonAlert.displayCp: Int?
    get() = when {
        isWeatherChange -> newCp?.takeIf { it > 0 }
        else -> cp?.takeIf { it > 0 }
    }

/** Compact CP metadata for the 16:9 picture-in-picture overlay. */
internal fun buildPipCpText(alert: PokemonAlert): String? {
    if (!alert.hasTypeContaining("raid")) {
        return alert.displayCp?.let { "CP $it" }
    }

    val perfectCatchValues = buildList {
        alert.hundoCP?.level20?.takeIf { it > 0 }?.let { add("L20 $it") }
        alert.hundoCP?.level25?.takeIf { it > 0 }?.let { add("L25 $it") }
    }
    return perfectCatchValues
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = " \u00B7 ", prefix = "100% CP \u00B7 ")
}

fun buildAlertGlanceMetadata(
    alert: PokemonAlert,
    distanceText: String? = null,
    walkingText: String? = null,
    includeCategory: Boolean = true,
    separator: String = " • "
): String = buildList {
    alert.displayCp?.let { add("CP $it") }
    // Glance lines are single-line, so unconfirmed weather gets the compact caveat rather
    // than a second line of its own.
    currentWeatherDisplay(alert)
        ?.takeUnless { alert.isWeatherChange }
        ?.let { add(it.compactLabel) }
    distanceText?.takeIf { it.isNotBlank() }?.let(::add)
    walkingText?.takeIf { it.isNotBlank() }?.let(::add)
    if (includeCategory) add(resolveAlertVisualStyle(alert).label)
}.joinToString(separator)
