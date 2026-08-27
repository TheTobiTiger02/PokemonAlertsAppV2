package com.example.pokemonalertsv2.data.counters

import java.util.Locale

/**
 * Turns a suggested team into a Pokémon GO search query.
 *
 * The grammar is the part that is easy to get wrong. Pokémon GO evaluates `,` (OR) **before**
 * `&` (AND), and there are no parentheses to override it. So a query built as one complete
 * term per Pokémon —
 *
 * ```
 * necrozma&@Shadow Claw&cp4634,hydreigon&@Bite&cp4098
 * ```
 *
 * — actually parses as `necrozma AND @Shadow Claw AND (cp4634 OR hydreigon) AND @Bite AND
 * cp4098`, which asks for a single Pokémon that is both species at both CPs and matches
 * nothing at all.
 *
 * The shape that works is one OR-group **per attribute**, the groups ANDed together, which is
 * what Poké Genie emits:
 *
 * ```
 * 643,383,609&CP3556,CP3541,CP4499,CP4566,CP3555,CP3544&@Mud Shot,@Fire Spin,@Fire Fang&@Fusion Flare,@Precipice Blades,@Overheat
 * ```
 *
 * — *(one of these species) AND (one of these CPs) AND (one of these fast moves) AND (one of
 * these charged moves)*. Broader than the party in principle; exactly the party in practice.
 *
 * Species are dex **numbers**, which sidesteps naming entirely: a mega, a shadow and a
 * regional form all share the base species' number, and that is the Pokémon actually sitting
 * in the bag.
 *
 * Every value is read from the copy the trainer owns, never from the recommended moveset. In
 * "Best potential" mode the ranking scores a moveset the Pokémon may not know, and searching
 * for a move it does not have matches nothing.
 *
 * No `shadow` keyword, matching Poké Genie: it would be a fifth ANDed group and would exclude
 * every non-shadow member of a mixed team.
 */
object PokemonGoSearch {

    /** Species, CPs and both move slots — the query that selects exactly this party. */
    fun teamQuery(team: List<PersonalTeamSlot>, dexNumbers: Map<String, Int>): String {
        val copies = team.flatMap { it.copies }
        if (copies.isEmpty()) return ""

        val species = copies
            .map { counter ->
                dexNumbers[counter.pokemonId]?.takeIf { it > 0 }?.toString()
                    ?: searchName(counter.displayName)
            }
            .filter { it.isNotEmpty() }
            .distinct()
        if (species.isEmpty()) return ""

        return listOfNotNull(
            species.joinToString(","),
            // One entry per copy, not per species: four Chandelure are four different
            // Pokémon with four different CPs, and only their own CP will select them.
            groupOf(copies) { it.owned.cp?.takeIf { cp -> cp > 0 }?.let { cp -> "CP$cp" } },
            groupOf(copies) { it.owned.quickMove?.let { move -> "@$move" } },
            groupOf(copies) { it.owned.chargeMove?.let { move -> "@$move" } }
        ).joinToString("&")
    }

    /**
     * The species group alone.
     *
     * The escape hatch for a stale roster: a Pokémon powered up or TM'd since the import no
     * longer matches its recorded CP or moves, and [teamQuery] then selects nothing. The dex
     * numbers stay right forever.
     */
    fun speciesQuery(team: List<PersonalTeamSlot>, dexNumbers: Map<String, Int>): String =
        team.flatMap { it.copies }
            .map { counter ->
                dexNumbers[counter.pokemonId]?.takeIf { it > 0 }?.toString()
                    ?: searchName(counter.displayName)
            }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(",")

    /**
     * One ANDed group, or null when any member cannot contribute a value.
     *
     * This is the rule the grammar forces. The groups are ANDed, so a Pokémon absent from the
     * CP group is excluded from the entire result — meaning one copy with no recorded CP has
     * to drop the CP group altogether. Dropping it widens the query; keeping a partial group
     * would silently lose that Pokémon, which is worse.
     */
    private fun groupOf(
        copies: List<PersonalCounter>,
        value: (PersonalCounter) -> String?
    ): String? {
        val values = copies.map(value)
        if (values.any { it.isNullOrBlank() }) return null
        return values.filterNotNull().distinct().joinToString(",")
    }

    /**
     * `Mega Charizard Y` -> `charizard`, `Kyurem (Black)` -> `kyurem`.
     *
     * Only a fallback for a species the game master has no dex number for, which happens
     * before the first sync. Pokémon GO matches the base species name, so the mega, shadow
     * and form decorations all have to come off.
     */
    internal fun searchName(displayName: String): String {
        var name = displayName.substringBefore('(').trim()
        listOf("Shadow ", "Mega ", "Primal ").forEach { prefix ->
            if (name.startsWith(prefix, ignoreCase = true)) {
                name = name.removePrefix(name.take(prefix.length)).trim()
            }
        }
        // A trailing mega variant letter is not part of the species name either.
        name = name.removeSuffix(" X").removeSuffix(" Y").trim()
        return name.lowercase(Locale.ROOT)
    }
}
