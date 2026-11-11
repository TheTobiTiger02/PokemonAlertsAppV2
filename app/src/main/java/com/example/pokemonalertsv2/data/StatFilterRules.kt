package com.example.pokemonalertsv2.data

import kotlinx.serialization.Serializable

/**
 * Minimum IV, CP and level for Pokémon alerts. Every limit is 0 when off.
 *
 * An alert that does not carry a value passes that rule: a sighting nobody encountered has no
 * IV, and hiding it for missing data would hide most live sightings. The IV floor is not
 * applied to Hundo and Nundo alerts, whose IV is what they are.
 */
@Serializable
data class StatFilterRules(
    val minIvPercent: Int = 0,
    val minCp: Int = 0,
    val minLevel: Int = 0
) {
    val isActive: Boolean get() = minIvPercent > 0 || minCp > 0 || minLevel > 0

    val ruleCount: Int get() = listOf(minIvPercent, minCp, minLevel).count { it > 0 }

    fun accepts(alert: PokemonAlert, applyIvFloor: Boolean): Boolean {
        if (applyIvFloor && minIvPercent > 0) {
            val iv = alert.ivPercent()
            if (iv != null && iv < minIvPercent) return false
        }
        if (minCp > 0) {
            val cp = alert.cp
            if (cp != null && cp < minCp) return false
        }
        if (minLevel > 0) {
            val level = alert.level
            if (level != null && level < minLevel) return false
        }
        return true
    }

    companion object {
        val None = StatFilterRules()
        val IV_PRESETS = listOf(0, 80, 90, 96)
        val CP_PRESETS = listOf(0, 1500, 2500)
        val LEVEL_PRESETS = listOf(0, 20, 30, 35)
    }
}

/** IV as a whole percentage of 45, or null when any of the three values is unknown. */
fun PokemonAlert.ivPercent(): Int? {
    val attack = ivAttack ?: return null
    val defense = ivDefense ?: return null
    val stamina = ivStamina ?: return null
    return ((attack + defense + stamina) * 100) / 45
}
