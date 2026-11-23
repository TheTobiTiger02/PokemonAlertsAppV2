package com.example.pokemonalertsv2.ui.checklists

import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import com.example.pokemonalertsv2.data.godex.GoDexEvolutionEdge
import org.junit.Assert.assertEquals
import org.junit.Test

class ChecklistFiltersTest {
    private val entries = listOf(
        entry("0025-none", 25, null, "none", "Pikachu", needed = false),
        entry("0026_alola-female", 26, "alola", "female", "Alolan Raichu", needed = true),
        entry("0001-none", 1, null, "none", "Bulbasaur", needed = true),
        entry("0002-none", 2, null, "none", "Ivysaur", needed = true),
        entry("0503_hisui-none", 503, "hisui", "none", "Hisuian Samurott", needed = true)
    )

    private fun keys(filter: ChecklistFilter, localName: (GoDexEntryEntity) -> String? = { null }, families: Map<Int, Int> = emptyMap()) =
        filterChecklist(entries, filter, localName, typesOf = { typeOf[it.entryKey].orEmpty() }, families = families).map { it.entryKey }

    private val typeOf = mapOf(
        "0025-none" to listOf("ELECTRIC"),
        "0026_alola-female" to listOf("ELECTRIC", "PSYCHIC"),
        "0001-none" to listOf("GRASS", "POISON"),
        "0002-none" to listOf("GRASS", "POISON"),
        "0503_hisui-none" to listOf("WATER", "DARK")
    )

    @Test
    fun statusFilterSortsByDexByDefault() {
        assertEquals(listOf("0001-none", "0002-none", "0026_alola-female", "0503_hisui-none"), keys(ChecklistFilter()))
        assertEquals(listOf("0025-none"), keys(ChecklistFilter(status = ChecklistStatus.CAUGHT)))
        assertEquals(5, keys(ChecklistFilter(status = ChecklistStatus.ALL)).size)
    }

    @Test
    fun searchMatchesNumberFormGenderKeyAndTheLocalName() {
        val all = ChecklistFilter(status = ChecklistStatus.ALL)
        assertEquals(listOf("0026_alola-female"), keys(all.copy(query = "female")))
        assertEquals(listOf("0503_hisui-none"), keys(all.copy(query = "0503")))
        assertEquals(
            "German species names are searchable",
            listOf("0001-none"),
            keys(all.copy(query = "bisasam"), localName = { if (it.pokedexId == 1) "Bisasam" else null })
        )
    }

    @Test
    fun generationTypeAndFormFiltersNarrow() {
        val all = ChecklistFilter(status = ChecklistStatus.ALL)
        assertEquals(listOf("0503_hisui-none"), keys(all.copy(generation = 5)))
        assertEquals(listOf("0025-none", "0026_alola-female"), keys(all.copy(type = "ELECTRIC")))
        assertEquals(listOf("0026_alola-female", "0503_hisui-none"), keys(all.copy(formsOnly = true)))
        assertEquals(1, generationOf(151))
        assertEquals(9, generationOf(1025))
        assertEquals(null, generationOf(2000))
    }

    @Test
    fun familySortKeepsEvolutionLinesTogether() {
        val families = evolutionFamilies(listOf(GoDexEvolutionEdge(1, 2), GoDexEvolutionEdge(2, 3), GoDexEvolutionEdge(25, 26)))
        assertEquals(1, families[3])
        assertEquals(25, families[26])
        val sorted = keys(ChecklistFilter(status = ChecklistStatus.ALL, sort = ChecklistSort.FAMILY), families = families)
        assertEquals(listOf("0001-none", "0002-none", "0025-none", "0026_alola-female", "0503_hisui-none"), sorted)
        assertEquals(
            "select family takes the whole line",
            setOf("0025-none", "0026_alola-female"),
            familyKeys(entries, setOf("0026_alola-female"), families)
        )
    }

    @Test
    fun nameSortUsesTheLocalName() {
        val sorted = keys(
            ChecklistFilter(status = ChecklistStatus.ALL, sort = ChecklistSort.NAME),
            localName = { if (it.pokedexId == 25) "Aaa" else it.displayName }
        )
        assertEquals("0025-none", sorted.first())
    }

    @Test
    fun variantLabelsSkipAFormTheNameAlreadyStates() {
        assertEquals("Female", goDexEntryVariantLabel(entries[1]))
        assertEquals("", goDexEntryVariantLabel(entries[4]))
        assertEquals(
            "Rapid Strike",
            goDexEntryVariantLabel(entry("0892_rapid_strike-none", 892, "rapid_strike", "none", "Urshifu", needed = true))
        )
    }

    private fun entry(key: String, dex: Int, form: String?, gender: String, name: String, needed: Boolean) =
        GoDexEntryEntity(entryKey = key, pokedexId = dex, formSlug = form, gender = gender, displayName = name, needed = needed)
}
