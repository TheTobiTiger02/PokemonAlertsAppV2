package com.example.pokemonalertsv2.data

import java.text.Normalizer
import java.util.Locale

/**
 * Raid tiers as understood by both the alert feed and Pokebattler.
 *
 * [pokebattlerRaidLevel] is the `RAID_LEVEL_*` token that goes into the
 * `fight.pokebattler.com` URL path. Pokebattler validates these strictly —
 * an unknown token returns HTTP 404 rather than falling back to a default.
 */
enum class RaidTier(val displayLabel: String, val pokebattlerRaidLevel: String) {
    TIER_1("1", "RAID_LEVEL_1"),
    TIER_2("2", "RAID_LEVEL_2"),
    TIER_3("3", "RAID_LEVEL_3"),
    TIER_4("4", "RAID_LEVEL_4"),
    TIER_5("5", "RAID_LEVEL_5"),
    TIER_6("6", "RAID_LEVEL_6"),
    MEGA("Mega", "RAID_LEVEL_MEGA"),
    MEGA_LEGENDARY("Mega Legendary", "RAID_LEVEL_MEGA_5"),
    // Pokebattler has no RAID_LEVEL_PRIMAL: primals live in the mega-legendary tier
    // (GROUDON_PRIMAL is catalogued under RAID_LEVEL_MEGA_5_LEGACY).
    PRIMAL("Primal", "RAID_LEVEL_MEGA_5"),
    ELITE("Elite", "RAID_LEVEL_ELITE"),
    ULTRA_BEAST("Ultra Beast", "RAID_LEVEL_ULTRA_BEAST"),
    SHADOW_1("Shadow 1", "RAID_LEVEL_1_SHADOW"),
    SHADOW_3("Shadow 3", "RAID_LEVEL_3_SHADOW"),
    SHADOW_5("Shadow 5", "RAID_LEVEL_5_SHADOW");

    companion object {
        fun fromPokebattlerRaidLevel(raidLevel: String?): RaidTier? {
            if (raidLevel.isNullOrBlank()) return null
            // Catalogue tiers carry _FUTURE / _LEGACY suffixes that are not part of the
            // queryable level token; strip them before matching.
            val base = raidLevel.uppercase(Locale.ROOT)
                .removeSuffix("_FUTURE")
                .removeSuffix("_LEGACY")
            // MEGA_5 is shared by MEGA_LEGENDARY and PRIMAL; first match wins by declaration order.
            return entries.firstOrNull { it.pokebattlerRaidLevel == base }
        }
    }
}

/**
 * Resolves the raid tier of an alert from its `type` list.
 *
 * The feed encodes the tier as a bare string inside [PokemonAlert.type] — e.g.
 * `["Raid", "5"]` or `["Raid", "Mega"]`. There is no dedicated field.
 *
 * The notification filter also uses this parser for its excluded-raid-tiers check, so
 * "Elite"/"Primal" exclusions behave the same there as everywhere else.
 */
object RaidTierParser {

    fun isRaid(alert: PokemonAlert): Boolean = isRaid(alert.type, alert.name)

    fun parse(alert: PokemonAlert): RaidTier? = parse(alert.type, alert.name)

    internal fun isRaid(types: List<String>?, fallbackName: String? = null): Boolean {
        val tokens = normalizedTokens(types)
        if (tokens.any { it.contains("raid") }) return true
        return normalizedToken(fallbackName)?.contains("raid") == true
    }

    /**
     * @param types the alert's raw `type` list
     * @param fallbackName scanned only when [types] yields nothing, for feeds that put
     *   the tier in the title instead ("Tier 5 Raid", "5-Star Raid")
     */
    internal fun parse(types: List<String>?, fallbackName: String? = null): RaidTier? {
        matchTier(normalizedTokens(types))?.let { return it }
        val nameToken = normalizedToken(fallbackName) ?: return null
        return matchTier(listOf(nameToken))
    }

    /**
     * Matches most-specific-first, across the whole token set rather than per token, so
     * `["Raid", "Mega", "5"]` resolves to [RaidTier.MEGA_LEGENDARY] and not [RaidTier.TIER_5].
     */
    private fun matchTier(tokens: List<String>): RaidTier? {
        if (tokens.isEmpty()) return null
        val joined = tokens.joinToString(" ")
        val words = joined.split(" ").filter { it.isNotEmpty() }.toSet()

        val hasMega = words.contains("mega")
        val hasLegendary = words.contains("legendary")
        val hasShadow = words.contains("shadow")
        val digit = firstTierDigit(joined, words)

        return when {
            words.contains("primal") -> RaidTier.PRIMAL
            joined.contains("ultra beast") || words.contains("ub") -> RaidTier.ULTRA_BEAST
            words.contains("elite") -> RaidTier.ELITE
            hasMega && (hasLegendary || digit == 5) -> RaidTier.MEGA_LEGENDARY
            hasMega -> RaidTier.MEGA
            hasShadow && digit != null -> shadowTierForDigit(digit)
            digit != null -> tierForDigit(digit)
            else -> null
        }
    }

    /** Accepts a bare digit, "tier 5", "t5" and "5 star" spellings. */
    private fun firstTierDigit(joined: String, words: Set<String>): Int? {
        words.firstOrNull { it.length == 1 && it[0].isDigit() }
            ?.let { return it.toInt() }
        TIER_DIGIT_PATTERNS.forEach { pattern ->
            pattern.find(joined)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        }
        return null
    }

    private fun shadowTierForDigit(digit: Int): RaidTier? = when (digit) {
        1 -> RaidTier.SHADOW_1
        3 -> RaidTier.SHADOW_3
        5 -> RaidTier.SHADOW_5
        // Pokebattler only publishes shadow tiers for 1, 3 and 5; fall back to the plain tier.
        else -> tierForDigit(digit)
    }

    private fun tierForDigit(digit: Int): RaidTier? = when (digit) {
        1 -> RaidTier.TIER_1
        2 -> RaidTier.TIER_2
        3 -> RaidTier.TIER_3
        4 -> RaidTier.TIER_4
        5 -> RaidTier.TIER_5
        6 -> RaidTier.TIER_6
        else -> null
    }

    private val TIER_DIGIT_PATTERNS = listOf(
        Regex("""\btier\s*(\d)\b"""),
        Regex("""\bt(\d)\b"""),
        Regex("""\b(\d)\s*star\b"""),
        Regex("""\blevel\s*(\d)\b""")
    )

    private fun normalizedTokens(types: List<String>?): List<String> =
        types.orEmpty().mapNotNull { normalizedToken(it) }

    /** Mirrors `GoDexMatcher.normalizedToken()` so both matchers agree on spelling. */
    private fun normalizedToken(value: String?): String? = value
        ?.let { Normalizer.normalize(it, Normalizer.Form.NFD) }
        ?.replace(Regex("""\p{M}+"""), "")
        ?.lowercase(Locale.ROOT)
        ?.replace("'", "")
        ?.replace(Regex("""[^a-z0-9]+"""), " ")
        ?.trim()
        ?.replace(Regex("""\s+"""), " ")
        ?.takeIf { it.isNotEmpty() }
}
