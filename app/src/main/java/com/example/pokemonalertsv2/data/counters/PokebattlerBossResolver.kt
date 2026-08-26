package com.example.pokemonalertsv2.data.counters

import com.example.pokemonalertsv2.data.RaidTier
import java.util.Locale

/**
 * One raid boss from Pokebattler's `/raids` catalogue, flattened.
 *
 * Deliberately a plain type rather than the Room entity so resolution stays
 * unit-testable without a database.
 */
data class RaidBossCatalogEntry(
    val tier: String,
    val pokemonId: String,
    val displayName: String,
    val cp: Int?
)

sealed interface BossResolution {
    data class Resolved(
        val pokemonId: String,
        val raidLevel: String,
        val displayName: String,
        val bossCp: Int?
    ) : BossResolution

    data class Unresolved(val attemptedName: String, val reason: String) : BossResolution
}

/** The catalogue's catch-all bucket of every Pokémon; not a queryable raid tier. */
private const val TIER_UNSET = "RAID_LEVEL_UNSET"

/**
 * Matches an alert's boss against the downloaded catalogue.
 *
 * Two responsibilities, resolved independently:
 *
 *  - **Which id** — walk [candidates] best-first and take the first the catalogue knows.
 *    Candidate *order* is what disambiguates `ALAKAZAM_SHADOW_FORM` from `ALAKAZAM`, so
 *    the tier is not consulted here.
 *  - **Which tier** — the alert wins when it states one, because it describes the raid
 *    happening right now. The catalogue is only a fallback, since most of its 3300 rows
 *    sit in the [TIER_UNSET] bucket (every Pokémon, raid boss or not) and many real
 *    bosses appear only as `_LEGACY`.
 */
internal fun resolveBossFromCatalogue(
    candidates: List<String>,
    parsedTier: RaidTier?,
    catalogue: List<RaidBossCatalogEntry>,
    attemptedName: String,
    /** Species rarity by Pokebattler id; empty until the game master has synced. */
    rarityFor: (String) -> String? = { null }
): BossResolution {
    if (candidates.isEmpty()) {
        return BossResolution.Unresolved(attemptedName, "No usable name on the alert")
    }
    if (catalogue.isEmpty()) {
        return BossResolution.Unresolved(attemptedName, "Raid boss list not downloaded yet")
    }

    val byId = catalogue.groupBy { it.pokemonId.uppercase(Locale.ROOT) }
    val byLoose = catalogue.groupBy { PokebattlerNameNormalizer.looseKey(it.pokemonId) }

    val matches = findMatches(candidates, byId, byLoose, catalogue)
        ?: return BossResolution.Unresolved(attemptedName, "No Pokebattler raid boss matched")

    val entry = matches.bestEntry()
    // The alert wins when it states a tier; otherwise take the catalogue's, and only then
    // fall back to a tier inferred from the species. Refusing here used to lose 34 of 252
    // real alerts, including every Shadow Palkia.
    val raidLevel = parsedTier?.pokebattlerRaidLevel
        ?: matches.firstRealTier()
        ?: fallbackRaidLevel(entry.pokemonId, rarityFor(entry.pokemonId))

    return BossResolution.Resolved(entry.pokemonId, raidLevel, entry.displayName, entry.cp)
}

private fun findMatches(
    candidates: List<String>,
    byId: Map<String, List<RaidBossCatalogEntry>>,
    byLoose: Map<String, List<RaidBossCatalogEntry>>,
    catalogue: List<RaidBossCatalogEntry>
): List<RaidBossCatalogEntry>? {
    // Exhaust every strategy on one candidate before falling to the next. Candidates are
    // ordered most-specific-first, so a prefix hit on DARMANITAN_GALARIAN must beat an
    // exact hit on the bare DARMANITAN that follows it.
    candidates.forEach { candidate ->
        val upper = candidate.uppercase(Locale.ROOT)

        byId[upper]?.takeIf { it.isNotEmpty() }?.let { return it }

        byLoose[PokebattlerNameNormalizer.looseKey(candidate)]?.takeIf { it.isNotEmpty() }?.let { return it }

        // The catalogue spells some forms more fully than we guessed, e.g.
        // DARMANITAN_GALARIAN -> DARMANITAN_GALARIAN_STANDARD_FORM.
        val hits = catalogue.filter { it.pokemonId.uppercase(Locale.ROOT).startsWith(upper + "_") }
        if (hits.isNotEmpty()) {
            val chosenId = hits.map { it.pokemonId }.distinct().minWithOrNull(FORM_PREFERENCE)!!
            return hits.filter { it.pokemonId == chosenId }
        }
    }
    return null
}

/**
 * Picks between sibling forms sharing a prefix. Prefers the base "standard" form, then the
 * shortest id, so the choice is deterministic rather than dependent on catalogue order.
 */
private val FORM_PREFERENCE = compareBy<String>(
    { if (it.contains("_STANDARD")) 0 else 1 },
    { it.length },
    { it }
)

/** Prefers a currently-active entry over a future, legacy, or unset one. */
private fun List<RaidBossCatalogEntry>.bestEntry(): RaidBossCatalogEntry =
    minByOrNull { tierRank(it.tier) }!!

/** The best real (queryable, non-unset) tier among the matched entries, if any. */
private fun List<RaidBossCatalogEntry>.firstRealTier(): String? = this
    .filter { isQueryableTier(it.tier) }
    .minByOrNull { tierRank(it.tier) }
    ?.let { normalizeTier(it.tier) }

/**
 * Whether a catalogue tier can actually be asked about on the raids endpoint.
 *
 * [TIER_UNSET] is the 2172-row "every Pokemon" bucket and was never queryable. `_MAX` tiers
 * are Max Battles, a different game mode: `RAID_LEVEL_1_MAX` answers 504 while plain
 * `RAID_LEVEL_1` answers in under a second. Excluding them matters because a Pokemon often
 * carries both -- CHARMANDER is listed at `RAID_LEVEL_1_LEGACY` *and* `RAID_LEVEL_1_MAX`,
 * and [tierRank] rates a bare `_MAX` as current, so it used to win and lose the counters.
 */
private fun isQueryableTier(tier: String): Boolean {
    val upper = tier.uppercase(Locale.ROOT)
    return upper != TIER_UNSET && !upper.contains("_MAX")
}

private fun tierRank(tier: String): Int {
    val upper = tier.uppercase(Locale.ROOT)
    return when {
        upper == TIER_UNSET -> 4
        !isQueryableTier(upper) -> 3
        upper.endsWith("_LEGACY") -> 2
        upper.endsWith("_FUTURE") -> 1
        else -> 0
    }
}

/** `RAID_LEVEL_5_FUTURE` and `RAID_LEVEL_5_LEGACY` are both queried as `RAID_LEVEL_5`. */
internal fun normalizeTier(tier: String): String = tier.uppercase(Locale.ROOT)
    .removeSuffix("_FUTURE")
    .removeSuffix("_LEGACY")
