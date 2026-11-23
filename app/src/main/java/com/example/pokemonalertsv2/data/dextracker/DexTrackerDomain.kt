package com.example.pokemonalertsv2.data.dextracker

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** A Dex Tracker collection (`users/{uid}/collections/{id}`). */
data class DexTrackerCollection(val id: String, val title: String, val archived: Boolean, val createdAt: Double)

/** One entry of a collection: a Pokémon line and how many of it the user has. */
data class DexTrackerEntry(
    val id: String,
    val pokemonId: Int,
    val name: String,
    val target: Int,
    /** Copies per evolution stage (stage id as text). */
    val owned: Map<String, Int>,
    val unassigned: Int,
    val order: Int
)

/** What a "+1 caught" writes, as Dex Tracker's own quick edit does (store.ts quickEdit, domain.ts). */
data class DexTrackerPlusOne(
    /** Field increments on the entry document. */
    val increments: Map<String, Long>,
    /** Change in copies counting towards the target: the day's activity counter. */
    val gained: Int,
    /** The activity log event (`events/{id}`). */
    val event: Map<String, Any>,
    val day: String
)

/**
 * The few rules of Dex Tracker (src/domain.ts in PokemonGoChecklist) the app needs: progress, and a
 * new catch added to the main account as an unassigned copy, with its activity counter and event.
 */
object DexTrackerDomain {
    fun ownedCount(entry: DexTrackerEntry): Int = entry.unassigned + entry.owned.values.sum()

    /** Copies that count towards the target; extra copies beyond it do not. */
    fun credited(entry: DexTrackerEntry): Int = minOf(ownedCount(entry), entry.target)

    /** Credited and target summed over a collection's entries. */
    fun progress(entries: List<DexTrackerEntry>): Pair<Int, Int> = entries.sumOf(::credited) to entries.sumOf { it.target }

    /** Local calendar day as YYYY-MM-DD (domain.ts dayKey). */
    fun dayKey(date: LocalDate = LocalDate.now()): String = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

    fun plusOne(entry: DexTrackerEntry, at: Long, eventId: String, day: String = dayKey()): DexTrackerPlusOne {
        val gained = credited(entry.copy(unassigned = entry.unassigned + 1)) - credited(entry)
        return DexTrackerPlusOne(
            increments = mapOf("unassigned" to 1L, "revision" to 1L),
            gained = gained,
            event = mapOf(
                "id" to eventId,
                "at" to at,
                "entryId" to entry.id,
                "entryName" to entry.name,
                "pokemonId" to entry.pokemonId,
                "kind" to "caught",
                "count" to 1,
                "change" to gained
            ),
            day = day
        )
    }
}
