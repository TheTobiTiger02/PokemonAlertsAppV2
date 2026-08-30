package com.example.pokemonalertsv2.data.counters

import java.util.Locale

/**
 * Turns a suggested team into a Pokémon GO search query.
 *
 * [teamQuery] deliberately uses the compact native Pokémon GO shape:
 *
 * ```
 * 643,383,609&CP3556,CP3545&@Mud Shot,@Fire Spin&@Fusion Flare,@Overheat
 * ```
 *
 * Each attribute is one OR group and the groups are joined with AND. CP normally identifies
 * the exact owned copies while the species and move groups protect against unrelated Pokémon
 * at the same CP. This is intentionally not the MonGO parenthesis expansion: distributing all
 * five attributes for six members grows combinatorially and produces a query too long to use.
 *
 * Species are dex **numbers**, which sidesteps naming entirely: a mega, a shadow and a
 * regional form all share the base species' number, and that is the Pokémon actually sitting
 * in the bag.
 *
 * Every value is read from the copy the trainer owns, never from the recommended moveset. In
 * "Best potential" mode the ranking scores a moveset the Pokémon may not know, and searching
 * for a move it does not have matches nothing.
 *
 * Tokens retain the suggested team's order and are deduplicated within each group.
 */
object PokemonGoSearch {

    /** Species, CPs and both move slots in compact native Pokémon GO groups. */
    fun teamQuery(team: List<PersonalTeamSlot>, dexNumbers: Map<String, Int>): String {
        val copies = team.flatMap { it.copies }
        if (copies.isEmpty()) return ""

        val groups = listOf(
            sortedSpeciesTokens(copies, dexNumbers),
            copies.mapNotNull { it.owned.cp?.takeIf { cp -> cp > 0 }?.let { cp -> "CP$cp" } },
            copies.mapNotNull { it.owned.quickMove.asMoveToken() },
            copies.mapNotNull { it.owned.chargeMove.asMoveToken() }
        )
        return groups.map { tokens ->
            tokens.filter { it.isNotEmpty() }.distinct().joinToString(",")
        }.filter { it.isNotEmpty() }.joinToString("&")
    }

    /**
     * The species group alone.
     *
     * The escape hatch for a stale roster: a Pokémon powered up or TM'd since the import no
     * longer matches its recorded CP or moves, and [teamQuery] then selects nothing. The dex
     * numbers stay right forever.
     */
    fun speciesQuery(team: List<PersonalTeamSlot>, dexNumbers: Map<String, Int>): String =
        sortedSpeciesTokens(team.flatMap { it.copies }, dexNumbers)
            .joinToString(",")

    private fun sortedSpeciesTokens(
        copies: List<PersonalCounter>,
        dexNumbers: Map<String, Int>
    ): List<String> = copies
        .map { counter -> speciesToken(counter, dexNumbers) }
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedWith(compareBy<String> { it.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it })

    private fun speciesToken(counter: PersonalCounter, dexNumbers: Map<String, Int>): String =
        dexNumbers[counter.pokemonId]?.takeIf { it > 0 }?.toString()
            ?: searchName(counter.displayName)

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

    private fun String?.asMoveToken(): String? = this
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { "@$it" }
}
