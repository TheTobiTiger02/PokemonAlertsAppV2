package com.example.pokemonalertsv2.ui.alerts

import androidx.compose.runtime.Immutable
import com.example.pokemonalertsv2.data.PokemonAlert

@Immutable
data class AlertVisualStyle(
    val label: String,
    val shortCode: String,
    val category: AlertCategory
)

enum class AlertCategory(val accentArgb: Long) {
    HUNDO(0xFFFFB300),
    NUNDO(0xFF55C7E8),
    PVP(0xFF9B7BFF),
    RAID(0xFFFF7A45),
    QUEST(0xFF39B975),
    RARE(0xFFE75AA7),
    SPAWN(0xFF45B8A5),
    ROCKET(0xFFE5484D),
    KECLEON(0xFF22B8A7),
    WEATHER(0xFF4CB7F5),
    GENERIC(0xFF8793A6)
}

fun resolveAlertVisualStyle(type: String?): AlertVisualStyle {
    val normalized = type.orEmpty().lowercase()
    return when {
        "weather" in normalized -> AlertVisualStyle("Weather", "WX", AlertCategory.WEATHER)
        "raid" in normalized -> AlertVisualStyle("Raid", "RD", AlertCategory.RAID)
        "quest" in normalized -> AlertVisualStyle("Quest", "QS", AlertCategory.QUEST)
        "hundo" in normalized -> AlertVisualStyle("Hundo", "100", AlertCategory.HUNDO)
        "nundo" in normalized -> AlertVisualStyle("Nundo", "000", AlertCategory.NUNDO)
        "pvp" in normalized -> AlertVisualStyle("PvP", "PVP", AlertCategory.PVP)
        "rocket" in normalized || "shadow" in normalized ->
            AlertVisualStyle("Rocket", "RKT", AlertCategory.ROCKET)
        "kecleon" in normalized -> AlertVisualStyle("Kecleon", "KCL", AlertCategory.KECLEON)
        "rare" in normalized -> AlertVisualStyle("Rare", "RAR", AlertCategory.RARE)
        "spawn" in normalized -> AlertVisualStyle("Spawn", "SPN", AlertCategory.SPAWN)
        else -> AlertVisualStyle("Alert", "ALT", AlertCategory.GENERIC)
    }
}

fun resolveAlertVisualStyle(alert: PokemonAlert): AlertVisualStyle = when {
    alert.isWeatherChange -> resolveAlertVisualStyle("weather")
    alert.isPerfect -> resolveAlertVisualStyle("hundo")
    alert.isNundo -> resolveAlertVisualStyle("nundo")
    alert.gruntType != null -> resolveAlertVisualStyle("rocket")
    else -> resolveAlertVisualStyle(alert.type?.joinToString(" "))
}
