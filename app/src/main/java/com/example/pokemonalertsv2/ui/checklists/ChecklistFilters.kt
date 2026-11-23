package com.example.pokemonalertsv2.ui.checklists

import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import com.example.pokemonalertsv2.data.godex.GoDexEvolutionEdge
import java.util.Locale

/** Which entries the editor shows by caught state. */
internal enum class ChecklistStatus { NEEDED, CAUGHT, ALL }

internal enum class ChecklistSort { DEX, NAME, FAMILY }

/** Everything the editor filters and sorts by; saved across rotation as plain values. */
internal data class ChecklistFilter(
    val status: ChecklistStatus = ChecklistStatus.NEEDED,
    val query: String = "",
    /** 1..9, or null for every generation. */
    val generation: Int? = null,
    /** A bare type name (`FIRE`), or null. */
    val type: String? = null,
    /** Only regional and other alternate forms. */
    val formsOnly: Boolean = false,
    val sort: ChecklistSort = ChecklistSort.DEX
) {
    val narrowed: Boolean get() = generation != null || type != null || formsOnly
}

/** National dex ranges per generation. */
internal val GENERATION_RANGES: List<IntRange> = listOf(
    1..151, 152..251, 252..386, 387..493, 494..649, 650..721, 722..809, 810..905, 906..1025
)

internal fun generationOf(pokedexId: Int): Int? =
    GENERATION_RANGES.indexOfFirst { pokedexId in it }.takeIf { it >= 0 }?.plus(1)

/**
 * Each species' family, as the smallest dex number connected to it by evolution, so a family
 * sorts together and "select family" finds the whole line.
 */
internal fun evolutionFamilies(edges: List<GoDexEvolutionEdge>): Map<Int, Int> {
    val parent = HashMap<Int, Int>()
    fun find(x: Int): Int {
        var root = x
        while (parent[root] != null && parent[root] != root) root = parent.getValue(root)
        var node = x
        while (node != root) {
            val next = parent[node] ?: root
            parent[node] = root
            node = next
        }
        return root
    }
    edges.forEach { edge ->
        val a = find(edge.from)
        val b = find(edge.to)
        if (a != b) {
            val (low, high) = if (a < b) a to b else b to a
            parent[high] = low
            parent.putIfAbsent(low, low)
        }
    }
    return parent.keys.associateWith(::find)
}

/** The family key of a species: its family root, or itself when it does not evolve. */
internal fun Map<Int, Int>.familyOf(pokedexId: Int): Int = this[pokedexId] ?: pokedexId

internal val REGIONAL_FORMS = setOf("alola", "galar", "hisui", "paldea")

/** The regional form an entry is (alola, galar, hisui, paldea), or null. */
internal fun regionalFormOf(entry: GoDexEntryEntity): String? =
    entry.formSlug?.lowercase(Locale.ROOT)?.takeIf { it in REGIONAL_FORMS }

/**
 * The entries [filter] keeps, in its order. [localName] is the name in the app language (German
 * names are searchable too); [typesOf] the entry's types as bare names, empty when unknown.
 */
internal fun filterChecklist(
    entries: List<GoDexEntryEntity>,
    filter: ChecklistFilter,
    localName: (GoDexEntryEntity) -> String? = { null },
    typesOf: (GoDexEntryEntity) -> List<String> = { emptyList() },
    families: Map<Int, Int> = emptyMap()
): List<GoDexEntryEntity> {
    val query = filter.query.trim().lowercase(Locale.ROOT)
    val kept = entries.filter { entry ->
        when (filter.status) {
            ChecklistStatus.NEEDED -> entry.needed
            ChecklistStatus.CAUGHT -> !entry.needed
            ChecklistStatus.ALL -> true
        } &&
            (filter.generation == null || generationOf(entry.pokedexId) == filter.generation) &&
            (filter.type == null || filter.type in typesOf(entry)) &&
            (!filter.formsOnly || entry.formSlug != null) &&
            (query.isEmpty() || searchTexts(entry, localName(entry)).any { it.contains(query) })
    }
    val byDex = compareBy(GoDexEntryEntity::pokedexId, GoDexEntryEntity::entryKey)
    return when (filter.sort) {
        ChecklistSort.DEX -> kept.sortedWith(byDex)
        ChecklistSort.NAME -> kept.sortedWith(compareBy<GoDexEntryEntity> { (localName(it) ?: it.displayName).lowercase(Locale.ROOT) }.then(byDex))
        ChecklistSort.FAMILY -> kept.sortedWith(compareBy<GoDexEntryEntity> { families.familyOf(it.pokedexId) }.then(byDex))
    }
}

private fun searchTexts(entry: GoDexEntryEntity, localName: String?): List<String> = listOfNotNull(
    entry.displayName,
    localName,
    entry.pokedexId.toString(),
    entry.pokedexId.toString().padStart(4, '0'),
    entry.formSlug,
    entry.gender,
    entry.entryKey
).map { it.lowercase(Locale.ROOT) }

/** Every entry of the families the [selected] entries belong to. */
internal fun familyKeys(entries: List<GoDexEntryEntity>, selected: Set<String>, families: Map<Int, Int>): Set<String> {
    val wanted = entries.filter { it.entryKey in selected }.mapTo(HashSet()) { families.familyOf(it.pokedexId) }
    return entries.filter { families.familyOf(it.pokedexId) in wanted }.mapTo(LinkedHashSet()) { it.entryKey }
}
