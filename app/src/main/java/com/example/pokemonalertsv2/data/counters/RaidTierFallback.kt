package com.example.pokemonalertsv2.data.counters

import java.util.Locale

/**
 * A queryable tier for a boss the catalogue only lists under `RAID_LEVEL_UNSET`.
 *
 * The alert feed sends `type = ["Raid"]` with no tier token, and 34 of 252 real alerts
 * resolve to a valid id whose only catalogue row is that catch-all bucket — Shadow Palkia,
 * Solgaleo, costume Pikachu, Poltchageist, Gengar. Pokebattler accepts any boss at any tier
 * and the tier only sets the boss HP and CPM, so an approximate tier always beats refusing
 * to show counters at all.
 */
internal fun fallbackRaidLevel(pokemonId: String, rarity: String?): String {
    val id = pokemonId.uppercase(Locale.ROOT)
    val rare = rarity?.uppercase(Locale.ROOT).orEmpty()
    val legendary = rare.contains("LEGENDARY") || rare.contains("MYTHIC")

    val base = when {
        id.contains("_PRIMAL") -> "RAID_LEVEL_MEGA_5"
        // A mega legendary boss has roughly double the HP of an ordinary mega.
        id.contains("_MEGA") && legendary -> "RAID_LEVEL_MEGA_5"
        id.contains("_MEGA") -> "RAID_LEVEL_MEGA"
        rare.contains("ULTRA_BEAST") -> "RAID_LEVEL_ULTRA_BEAST"
        legendary -> "RAID_LEVEL_5"
        // Costumed Pikachu only ever appear as tier-1 raids, and tier 1 versus tier 3 is a
        // fourfold difference in boss HP, which would skew both the estimator and the team
        // simulation.
        PokebattlerNameNormalizer.baseSpeciesId(id).startsWith("PIKACHU") -> "RAID_LEVEL_1"
        // Middle of the 1..5 range, so the worst case is bounded in either direction.
        else -> "RAID_LEVEL_3"
    }

    val isShadow = id.endsWith("_SHADOW_FORM") || id.endsWith("_SHADOW")
    return if (isShadow) shadowVariant(base) else base
}

/** Shadow raids exist at tiers 1, 3 and 5 only; anything else keeps its base tier. */
private fun shadowVariant(raidLevel: String): String = when (raidLevel) {
    "RAID_LEVEL_1" -> "RAID_LEVEL_1_SHADOW"
    "RAID_LEVEL_3" -> "RAID_LEVEL_3_SHADOW"
    "RAID_LEVEL_5" -> "RAID_LEVEL_5_SHADOW"
    else -> raidLevel
}
