package com.example.pokemonalertsv2.data.pokegenie

import com.example.pokemonalertsv2.data.counters.FORM_PREFERENCE
import com.example.pokemonalertsv2.data.counters.PokebattlerNameNormalizer
import java.util.Locale

/**
 * The Pokebattler species ids the game master actually knows about.
 *
 * `PokebattlerNameNormalizer` can only guess how Pokebattler spells a form, because it works
 * from a static table that is only as current as the last person to edit it. This arbitrates
 * those guesses against the downloaded catalogue, the way `resolveBossFromCatalogue` already
 * does for raid bosses -- so a form that ships after this code was written still resolves,
 * and a guess that names nothing real is recognized as a miss instead of silently degrading
 * into the bare species.
 *
 * An empty catalogue (the game master has not synced yet) makes [resolve] return null, which
 * leaves every caller with exactly the behaviour it had before.
 */
class SpeciesCatalogue(ids: Collection<String>) {

    private val byId: Set<String> = ids.mapNotNull {
        it.uppercase(Locale.ROOT).takeIf(String::isNotBlank)
    }.toSet()

    /**
     * Every id that carries a suffix, grouped by its first word.
     *
     * Grouping keeps the prefix and word-subset searches to the handful of ids that share a
     * species instead of scanning all ~2400 for every candidate of every imported row.
     */
    private val formsByBase: Map<String, List<String>> =
        byId.filter { it.contains('_') }.groupBy { it.substringBefore('_') }

    private val byLoose: Map<String, String> = byId
        .groupBy { PokebattlerNameNormalizer.looseKey(it) }
        .mapValues { (_, siblings) -> siblings.minWith(FORM_PREFERENCE) }

    val isEmpty: Boolean get() = byId.isEmpty()

    /** True when [id] is one the game master actually carries. */
    fun contains(id: String): Boolean = id.uppercase(Locale.ROOT) in byId

    /**
     * The best real id for one ordered, best-first candidate list, or null when none names
     * anything the game master carries.
     *
     * Every strategy is exhausted on one candidate before moving to the next, because the
     * candidates are ordered most-specific-first: a word-subset hit on ZACIAN_CROWNED_SWORD
     * must beat an exact hit on the bare ZACIAN that follows it.
     */
    fun resolve(candidates: List<String>): String? {
        if (byId.isEmpty()) return null
        candidates.forEach { candidate ->
            val upper = candidate.uppercase(Locale.ROOT)
            if (upper in byId) return upper
            byLoose[PokebattlerNameNormalizer.looseKey(upper)]?.let { return it }
            val siblings = formsByBase[upper.substringBefore('_')].orEmpty()
            // The catalogue spells some forms more fully than we guessed, e.g.
            // DARMANITAN_GALARIAN -> DARMANITAN_GALARIAN_STANDARD_FORM.
            siblings.filter { it.startsWith(upper + "_") }
                .minWithOrNull(FORM_PREFERENCE)
                ?.let { return it }
            // ...and some bury the guess in the middle, where no prefix can reach it:
            // Poke Genie writes Zacian's form as "Sword", Pokebattler as CROWNED_SWORD.
            wordSubsetMatch(upper, siblings)?.let { return it }
        }
        return null
    }

    /**
     * The sibling form whose words are a superset of [candidate]'s.
     *
     * Every word of the guess has to appear, so "Sword" reaches ZACIAN_CROWNED_SWORD_FORM
     * while a genuinely unknown label ("Kariyushi") matches nothing and is left to fall
     * through to the species.
     */
    private fun wordSubsetMatch(candidate: String, siblings: List<String>): String? {
        val wanted = formWords(candidate.substringAfter('_', ""))
        if (wanted.isEmpty()) return null
        return siblings
            .filter { formWords(it.substringAfter('_', "")).containsAll(wanted) }
            .minWithOrNull(FORM_PREFERENCE)
    }

    private fun formWords(suffix: String): Set<String> = suffix
        .split("_")
        .filter { it.isNotEmpty() && it != "FORM" }
        .toSet()
}
