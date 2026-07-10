package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.runtime.Immutable
import com.example.pokemonalertsv2.data.PokemonAlert

@Immutable
data class AlertVisualStyle(
    val label: String,
    val shortCode: String
)

fun resolveAlertVisualStyle(type: String?): AlertVisualStyle {
    val normalized = type.orEmpty().lowercase()
    return when {
        "weather" in normalized -> AlertVisualStyle("Weather", "WX")
        "raid" in normalized -> AlertVisualStyle("Raid", "RD")
        "quest" in normalized -> AlertVisualStyle("Quest", "QS")
        "hundo" in normalized -> AlertVisualStyle("Hundo", "100")
        "nundo" in normalized -> AlertVisualStyle("Nundo", "000")
        "pvp" in normalized -> AlertVisualStyle("PvP", "PVP")
        "rocket" in normalized || "shadow" in normalized ->
            AlertVisualStyle("Rocket", "RKT")
        "kecleon" in normalized -> AlertVisualStyle("Kecleon", "KCL")
        "rare" in normalized || "spawn" in normalized ->
            AlertVisualStyle("Spawn", "SPN")
        else -> AlertVisualStyle("Alert", "ALT")
    }
}

fun resolveAlertVisualStyle(alert: PokemonAlert): AlertVisualStyle = when {
    alert.isWeatherChange -> resolveAlertVisualStyle("weather")
    alert.isPerfect -> resolveAlertVisualStyle("hundo")
    alert.isNundo -> resolveAlertVisualStyle("nundo")
    alert.gruntType != null -> resolveAlertVisualStyle("rocket")
    else -> resolveAlertVisualStyle(alert.type?.joinToString(" "))
}
