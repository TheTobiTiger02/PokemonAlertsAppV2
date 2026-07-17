package com.example.pokemonalertsv2.data.godex

import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.database.GoDexEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoDexMatcherTest {
    @Test
    fun matchesBaseAndRegionalFormsExactly() {
        val entries = listOf(
            entry("0026-none", 26, null, needed = false),
            entry("0026_alola-none", 26, "alola", needed = true)
        )

        assertEquals(GoDexMatchStatus.COLLECTED, match(26, null, entries))
        assertEquals(GoDexMatchStatus.NEEDED, match(26, "Alolan", entries))
        assertEquals(GoDexMatchStatus.UNKNOWN, match(26, "Galarian", entries))
    }

    @Test
    fun normalizesNamedSeaStripeUnownAndSpindaForms() {
        assertEquals("baile", GoDexMatcher.normalizedAlertForm("Baile Style"))
        assertEquals("east", GoDexMatcher.normalizedAlertForm("East Sea"))
        assertEquals("blue", GoDexMatcher.normalizedAlertForm("Blue Striped"))
        assertEquals("g", GoDexMatcher.normalizedAlertForm("Unown G"))
        assertEquals("07", GoDexMatcher.normalizedAlertForm("Spinda 7"))
    }

    @Test
    fun requiresExactGenderWhenGoDexDistinguishesIt() {
        val entries = listOf(
            entry("0593-male", 593, null, "male", needed = false),
            entry("0593-female", 593, null, "female", needed = true)
        )

        assertEquals(GoDexMatchStatus.COLLECTED, match(593, null, entries, "Male"))
        assertEquals(GoDexMatchStatus.NEEDED, match(593, null, entries, "Female"))
        assertEquals(GoDexMatchStatus.UNKNOWN, match(593, null, entries))
    }

    @Test
    fun baseOnlyEntryAppliesToUnrepresentedCostume() {
        val entries = listOf(entry("0025-none", 25, null, needed = true))

        assertEquals(GoDexMatchStatus.NEEDED, match(25, "Holiday 2025", entries))
    }

    @Test
    fun missingDexAndUnrecognizedDistinguishedFormAreUnknown() {
        val entries = listOf(entry("0550_blue-none", 550, "blue", needed = false))

        assertEquals(
            GoDexMatchStatus.UNKNOWN,
            GoDexMatcher.match(PokemonAlert(name = "Unknown"), entries, configured = true).status
        )
        assertEquals(GoDexMatchStatus.UNKNOWN, match(550, "Mystery Stripe", entries))
    }

    @Test
    fun matchesPumpkabooAndGourgeistSizesExactly() {
        val entries = listOf(
            entry("0710a_small-none", 710, "small", needed = true),
            entry("0710b_average-none", 710, "average", needed = false),
            entry("0710c_large-none", 710, "large", needed = false),
            entry("0710d_super-none", 710, "super", needed = false),
            entry("0711a_small-none", 711, "small", needed = true)
        )

        assertEquals(GoDexMatchStatus.NEEDED, match(710, "Small", entries))
        assertEquals(GoDexMatchStatus.COLLECTED, match(710, "Average", entries))
        assertEquals(GoDexMatchStatus.COLLECTED, match(710, "Large", entries))
        assertEquals(GoDexMatchStatus.COLLECTED, match(710, "Super", entries))
        assertEquals(GoDexMatchStatus.NEEDED, match(711, "Small", entries))
    }

    @Test
    fun collectedPokemonNeedsAnyMissingReachableDescendant() {
        val graph = GoDexEvolutionGraph.forTests(
            listOf(GoDexEvolutionEdge(4, 5), GoDexEvolutionEdge(5, 6))
        )
        val entries = listOf(
            entry("0004-none", 4, null, needed = false, name = "Charmander"),
            entry("0005-none", 5, null, needed = false, name = "Charmeleon"),
            entry("0006-none", 6, null, needed = true, name = "Charizard")
        )

        val charmander = matchResult(4, null, entries, graph = graph)
        val charmeleon = matchResult(5, null, entries, graph = graph)

        assertEquals(GoDexMatchStatus.EVOLUTION_NEEDED, charmander.status)
        assertEquals("Charizard", charmander.compactEvolutionLabel)
        assertEquals(2, charmander.evolutionTargets.single().distance)
        assertEquals(GoDexMatchStatus.EVOLUTION_NEEDED, charmeleon.status)
        assertEquals(1, charmeleon.evolutionTargets.single().distance)
    }

    @Test
    fun ordersBranchedTargetsByDistanceThenPokedexNumber() {
        val graph = GoDexEvolutionGraph.forTests(
            listOf(
                GoDexEvolutionEdge(133, 135),
                GoDexEvolutionEdge(133, 134),
                GoDexEvolutionEdge(134, 700)
            )
        )
        val entries = listOf(
            entry("0133-none", 133, null, needed = false, name = "Eevee"),
            entry("0134-none", 134, null, needed = true, name = "Vaporeon"),
            entry("0135-none", 135, null, needed = true, name = "Jolteon"),
            entry("0700-none", 700, null, needed = true, name = "Sylveon")
        )

        val result = matchResult(133, null, entries, graph = graph)

        assertEquals(listOf(134, 135, 700), result.evolutionTargets.map { it.pokedexId })
        assertEquals("Vaporeon +2", result.compactEvolutionLabel)
    }

    @Test
    fun enforcesGenderRestrictedEvolutionPaths() {
        val graph = GoDexEvolutionGraph.forTests(
            listOf(
                GoDexEvolutionEdge(415, 416, sourceGender = "female"),
                GoDexEvolutionEdge(281, 475, sourceGender = "male"),
                GoDexEvolutionEdge(361, 478, sourceGender = "female"),
                GoDexEvolutionEdge(757, 758, sourceGender = "female")
            )
        )
        fun family(source: Int, target: Int, sourceName: String, targetName: String) = listOf(
            entry("$source-none", source, null, needed = false, name = sourceName),
            entry("$target-none", target, null, needed = true, name = targetName)
        )

        assertEquals(GoDexMatchStatus.EVOLUTION_NEEDED, matchResult(415, null, family(415, 416, "Combee", "Vespiquen"), "Female", graph).status)
        assertEquals(GoDexMatchStatus.COLLECTED, matchResult(415, null, family(415, 416, "Combee", "Vespiquen"), "Male", graph).status)
        assertEquals(GoDexMatchStatus.EVOLUTION_NEEDED, matchResult(281, null, family(281, 475, "Kirlia", "Gallade"), "Male", graph).status)
        assertEquals(GoDexMatchStatus.COLLECTED, matchResult(281, null, family(281, 475, "Kirlia", "Gallade"), "Female", graph).status)
        assertEquals(GoDexMatchStatus.EVOLUTION_NEEDED, matchResult(361, null, family(361, 478, "Snorunt", "Froslass"), "Female", graph).status)
        assertEquals(GoDexMatchStatus.EVOLUTION_NEEDED, matchResult(757, null, family(757, 758, "Salandit", "Salazzle"), "Female", graph).status)
    }

    @Test
    fun regionalPathRequiresExactSourceForm() {
        val graph = GoDexEvolutionGraph.forTests(
            listOf(
                GoDexEvolutionEdge(211, 904, fromForm = "hisui"),
                GoDexEvolutionEdge(264, 862, fromForm = "galar")
            )
        )
        val entries = listOf(
            entry("0211-none", 211, null, needed = false, name = "Qwilfish"),
            entry("0211_hisui-none", 211, "hisui", needed = false, name = "Hisuian Qwilfish"),
            entry("0904-none", 904, null, needed = true, name = "Overqwil")
        )

        assertEquals(GoDexMatchStatus.COLLECTED, matchResult(211, null, entries, graph = graph).status)
        assertEquals(GoDexMatchStatus.EVOLUTION_NEEDED, matchResult(211, "Hisuian", entries, graph = graph).status)

        val linooneEntries = listOf(
            entry("0264-none", 264, null, needed = false, name = "Linoone"),
            entry("0264_galar-none", 264, "galar", needed = false, name = "Galarian Linoone"),
            entry("0862-none", 862, null, needed = true, name = "Obstagoon")
        )
        assertEquals(GoDexMatchStatus.COLLECTED, matchResult(264, null, linooneEntries, graph = graph).status)
        assertEquals(GoDexMatchStatus.EVOLUTION_NEEDED, matchResult(264, "Galarian", linooneEntries, graph = graph).status)
    }

    @Test
    fun directNeededPrecedesEvolutionAndCostumeFallbackDoesNotEvolve() {
        val graph = GoDexEvolutionGraph.forTests(listOf(GoDexEvolutionEdge(25, 26)))
        val neededEntries = listOf(
            entry("0025-none", 25, null, needed = true, name = "Pikachu"),
            entry("0026-none", 26, null, needed = true, name = "Raichu")
        )
        assertEquals(GoDexMatchStatus.NEEDED, matchResult(25, null, neededEntries, graph = graph).status)

        val costumeEntries = neededEntries.map { if (it.pokedexId == 25) it.copy(needed = false) else it }
        assertEquals(
            GoDexMatchStatus.COLLECTED,
            matchResult(25, "Holiday 2025", costumeEntries, graph = graph).status
        )
    }

    private fun match(
        dex: Int,
        form: String?,
        entries: List<GoDexEntryEntity>,
        gender: String? = null
    ) = GoDexMatcher.match(
        PokemonAlert(name = "Pokemon", pokedexId = dex, pokemonForm = form, gender = gender),
        entries,
        configured = true
    ).status

    private fun matchResult(
        dex: Int,
        form: String?,
        entries: List<GoDexEntryEntity>,
        gender: String? = null,
        graph: GoDexEvolutionGraph
    ) = GoDexMatcher.match(
        PokemonAlert(name = "Pokemon", pokedexId = dex, pokemonForm = form, gender = gender),
        entries,
        configured = true,
        evolutionGraph = graph
    )

    private fun entry(
        key: String,
        dex: Int,
        form: String?,
        gender: String = "none",
        needed: Boolean,
        name: String = "Pokemon"
    ) = GoDexEntryEntity(key, dex, form, gender, name, needed)
}
