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
 * Parentheses would make the intended query straightforward:
 *
 * ```
 * (609&shadow&CP3555&@Fire Spin&@Overheat),(643&CP4499&@Fire Fang&@Fusion Flare)
 * ```
 *
 * Pokémon GO does not support those parentheses, so [teamQuery] distributes the OR-of-AND
 * terms into an AND-of-OR query. That keeps every copy's species, shadow state, CP and moves
 * correlated instead of accepting arbitrary cross-combinations between different team members.
 *
 * Species are dex **numbers**, which sidesteps naming entirely: a mega, a shadow and a
 * regional form all share the base species' number, and that is the Pokémon actually sitting
 * in the bag.
 *
 * Every value is read from the copy the trainer owns, never from the recommended moveset. In
 * "Best potential" mode the ranking scores a moveset the Pokémon may not know, and searching
 * for a move it does not have matches nothing.
 *
 * The distribution and ordering intentionally match https://mongo.lebeg134.hu. In particular,
 * a `shadow` predicate can live inside only the terms that need it without excluding regular
 * members of a mixed team.
 */
object PokemonGoSearch {

    /** Species, CPs and both move slots — the query that selects exactly this party. */
    fun teamQuery(team: List<PersonalTeamSlot>, dexNumbers: Map<String, Int>): String {
        val copies = team.flatMap { it.copies }
        if (copies.isEmpty()) return ""

        val terms = copies.mapNotNull { counter ->
            buildList {
                speciesToken(counter, dexNumbers).takeIf { it.isNotEmpty() }?.let(::add)
                if (counter.owned.shadow) add("shadow")
                counter.owned.cp?.takeIf { it > 0 }?.let { add("CP$it") }
                counter.owned.quickMove
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { add("@$it") }
                counter.owned.chargeMove
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { add("@$it") }
            }
                .takeIf { it.isNotEmpty() }
        }
        return expandTerms(terms)
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
            .map { counter -> speciesToken(counter, dexNumbers) }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(",")

    /**
     * Converts `(A&a),(B&b)` into `B,A&B,a&b,A&b,a`.
     *
     * Each input list is one parenthesized AND term. The website enumerates the last term
     * first, prepends its selected token to the clause, and removes repeated tokens inside a
     * clause while preserving insertion order. It does not remove duplicate clauses, so this
     * implementation deliberately retains them for byte-for-byte parity.
     */
    internal fun expandTerms(terms: List<List<String>>): String {
        val usable = terms.map { term -> term.filter { it.isNotEmpty() } }.filter { it.isNotEmpty() }
        if (usable.isEmpty()) return ""

        val reversed = usable.asReversed()
        val selected = ArrayList<String>(reversed.size)
        val result = StringBuilder()

        fun appendClauses(termIndex: Int) {
            if (termIndex == reversed.size) {
                if (result.isNotEmpty()) result.append('&')
                selected.distinct().joinTo(result, separator = ",")
                return
            }
            for (token in reversed[termIndex]) {
                selected += token
                appendClauses(termIndex + 1)
                selected.removeAt(selected.lastIndex)
            }
        }

        appendClauses(0)
        return result.toString()
    }

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
}
