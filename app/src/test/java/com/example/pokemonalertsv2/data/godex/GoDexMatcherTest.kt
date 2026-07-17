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
    fun matchesAllFlabebeFamilyFlowerColorsFromShortAndFullLabels() {
        val colors = listOf("red", "orange", "yellow", "white", "blue")

        (669..671).forEach { dex ->
            colors.forEachIndexed { index, color ->
                val entries = listOf(
                    entry(
                        "${dex}_${color}_flower-none",
                        dex,
                        "${color}_flower",
                        needed = index % 2 == 0
                    )
                )
                val expected = if (index % 2 == 0) GoDexMatchStatus.NEEDED else GoDexMatchStatus.COLLECTED

                assertEquals(expected, match(dex, color.replaceFirstChar(Char::uppercase), entries))
                assertEquals(expected, match(dex, "${color.replaceFirstChar(Char::uppercase)} Flower", entries))
            }
        }
    }

    @Test
    fun matchesFurfrouNaturalFormAndEveryTrim() {
        val entries = listOf(
            entry("0676-none", 676, null, needed = false),
            entry("0676_heart-none", 676, "heart", needed = true),
            entry("0676_star-none", 676, "star", needed = true),
            entry("0676_diamond-none", 676, "diamond", needed = true),
            entry("0676_debutante-none", 676, "debutante", needed = true),
            entry("0676_matron-none", 676, "matron", needed = true),
            entry("0676_dandy-none", 676, "dandy", needed = true),
            entry("0676_lareine-none", 676, "lareine", needed = true),
            entry("0676_kabuki-none", 676, "kabuki", needed = true),
            entry("0676_pharaoh-none", 676, "pharaoh", needed = true)
        )

        listOf("Natural", "Natural Form", "Normal Form", "Default Form", "Base Form").forEach {
            assertEquals(GoDexMatchStatus.COLLECTED, match(676, it, entries))
        }
        listOf(
            "Heart Trim", "Star Trim", "Diamond Trim", "Debutante Trim", "Matron Trim",
            "Dandy Trim", "La Reine Trim", "Kabuki Trim", "Pharaoh Trim"
        ).forEach {
            assertEquals(GoDexMatchStatus.NEEDED, match(676, it, entries))
        }
    }

    @Test
    fun collectedNaturalFurfrouReportsOnlyMissingTrims() {
        val graph = GoDexFormChangeGraph.forTests(
            listOf(
                GoDexFormChangeEdge(676, null, "heart"),
                GoDexFormChangeEdge(676, null, "star"),
                GoDexFormChangeEdge(676, null, "la_reine")
            )
        )
        val entries = listOf(
            entry("0676-none", 676, null, needed = false, name = "Furfrou (Natural)"),
            entry("0676_heart-none", 676, "heart", needed = true, name = "Furfrou (Heart)"),
            entry("0676_star-none", 676, "star", needed = false, name = "Furfrou (Star)"),
            entry("0676_lareine-none", 676, "lareine", needed = true, name = "Furfrou (La Reine)")
        )

        val result = matchResult(676, "Natural Form", entries, formChangeGraph = graph)

        assertEquals(GoDexMatchStatus.FORM_CHANGE_NEEDED, result.status)
        assertEquals(listOf("0676_heart-none", "0676_lareine-none"), result.formChangeTargets.map { it.entryKey })
        assertEquals("Furfrou (Heart) +1", result.compactFormChangeLabel)
    }

    @Test
    fun naturalFurfrouIsCollectedWhenEveryReachableTrimIsCollected() {
        val graph = GoDexFormChangeGraph.forTests(
            listOf(GoDexFormChangeEdge(676, null, "heart"))
        )
        val collectedEntries = listOf(
            entry("0676-none", 676, null, needed = false),
            entry("0676_heart-none", 676, "heart", needed = false)
        )
        val naturalNeededEntries = collectedEntries.map {
            if (it.formSlug == null) it.copy(needed = true) else it
        }

        assertEquals(
            GoDexMatchStatus.COLLECTED,
            matchResult(676, "Natural", collectedEntries, formChangeGraph = graph).status
        )
        assertEquals(
            GoDexMatchStatus.NEEDED,
            matchResult(676, "Natural", naturalNeededEntries, formChangeGraph = graph).status
        )
    }

    @Test
    fun formChangesTraverseCyclesAndCollectedIntermediatesSafely() {
        val graph = GoDexFormChangeGraph.forTests(
            listOf(
                GoDexFormChangeEdge(676, null, "heart"),
                GoDexFormChangeEdge(676, "heart", "star"),
                GoDexFormChangeEdge(676, "star", null),
                GoDexFormChangeEdge(676, null, "diamond")
            )
        )
        val entries = listOf(
            entry("0676-none", 676, null, needed = false),
            entry("0676_heart-none", 676, "heart", needed = false),
            entry("0676_star-none", 676, "star", needed = true)
        )

        val result = matchResult(676, "Natural", entries, formChangeGraph = graph)

        assertEquals(GoDexMatchStatus.FORM_CHANGE_NEEDED, result.status)
        assertEquals("0676_star-none", result.formChangeTargets.single().entryKey)
        assertEquals(2, result.formChangeTargets.single().distance)
    }

    @Test
    fun reportsCombinedEvolutionAndFormChangeTargets() {
        val evolutionGraph = GoDexEvolutionGraph.forTests(listOf(GoDexEvolutionEdge(1, 2)))
        val formChangeGraph = GoDexFormChangeGraph.forTests(listOf(GoDexFormChangeEdge(1, null, "alternate")))
        val entries = listOf(
            entry("0001-none", 1, null, needed = false, name = "Source"),
            entry("0001_alternate-none", 1, "alternate", needed = true, name = "Alternate Source"),
            entry("0002-none", 2, null, needed = true, name = "Evolution")
        )

        val result = matchResult(
            dex = 1,
            form = null,
            entries = entries,
            graph = evolutionGraph,
            formChangeGraph = formChangeGraph
        )

        assertEquals(GoDexMatchStatus.EVOLUTION_AND_FORM_CHANGE_NEEDED, result.status)
        assertEquals("0002-none", result.evolutionTargets.single().entryKey)
        assertEquals("0001_alternate-none", result.formChangeTargets.single().entryKey)
    }

    @Test
    fun matchesExistingDescriptiveFormFamiliesAgainstImportedSlugs() {
        val cases = listOf(
            Triple(19, "Alolan Form", "alola"),
            Triple(741, "Pom-Pom Style", "pom"),
            Triple(422, "East Sea", "east"),
            Triple(550, "Blue Striped", "blue"),
            Triple(412, "Plant Cloak", "plant"),
            Triple(585, "Spring Form", "spring"),
            Triple(710, "Small Size", "small")
        )

        cases.forEach { (dex, alertForm, entryForm) ->
            assertEquals(
                GoDexMatchStatus.NEEDED,
                match(dex, alertForm, listOf(entry("$dex-$entryForm", dex, entryForm, needed = true)))
            )
        }
        assertEquals(
            GoDexMatchStatus.NEEDED,
            match(201, "Unown G", listOf(entry("0201_g-none", 201, "g", needed = true)))
        )
        assertEquals(
            GoDexMatchStatus.NEEDED,
            match(327, "Spinda 7", listOf(entry("0327_07-none", 327, "07", needed = true)))
        )
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
    fun unrepresentedCostumeDoesNotFallBackToBaseEntry() {
        val entries = listOf(entry("0025-none", 25, null, needed = true))

        assertEquals(GoDexMatchStatus.UNKNOWN, match(25, "Holiday 2025", entries))
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
    fun directNeededPrecedesEvolutionAndUnknownCostumeDoesNotEvolve() {
        val graph = GoDexEvolutionGraph.forTests(listOf(GoDexEvolutionEdge(25, 26)))
        val neededEntries = listOf(
            entry("0025-none", 25, null, needed = true, name = "Pikachu"),
            entry("0026-none", 26, null, needed = true, name = "Raichu")
        )
        assertEquals(GoDexMatchStatus.NEEDED, matchResult(25, null, neededEntries, graph = graph).status)

        val costumeEntries = neededEntries.map { if (it.pokedexId == 25) it.copy(needed = false) else it }
        assertEquals(
            GoDexMatchStatus.UNKNOWN,
            matchResult(25, "Holiday 2025", costumeEntries, graph = graph).status
        )
    }

    @Test
    fun equivalentFlowerSlugsFindNeededFloetteForEveryColor() {
        listOf("red", "orange", "yellow", "white", "blue").forEach { color ->
            val graphForm = "${color}_flower"
            val graph = GoDexEvolutionGraph.forTests(
                listOf(
                    GoDexEvolutionEdge(669, 670, fromForm = graphForm, toForm = graphForm),
                    GoDexEvolutionEdge(670, 671, fromForm = graphForm, toForm = graphForm)
                )
            )
            val entries = listOf(
                entry("0669_$color-none", 669, color, needed = false, name = "Flabebe $color"),
                entry("0670_$color-none", 670, color, needed = true, name = "Floette $color"),
                entry("0671_$color-none", 671, color, needed = false, name = "Florges $color")
            )
            val alertForm = "${color.replaceFirstChar(Char::uppercase)} Flower"

            val flabebe = matchResult(669, alertForm, entries, graph = graph)

            assertEquals(GoDexMatchStatus.EVOLUTION_NEEDED, flabebe.status)
            assertEquals("0670_$color-none", flabebe.evolutionTargets.single().entryKey)
            assertEquals(GoDexMatchStatus.NEEDED, match(670, alertForm, entries))
            assertEquals(GoDexMatchStatus.COLLECTED, match(671, alertForm, entries))
        }
    }

    @Test
    fun equivalentFlowerSlugsTraverseCollectedIntermediateToNeededDescendant() {
        val graph = GoDexEvolutionGraph.forTests(
            listOf(
                GoDexEvolutionEdge(669, 670, fromForm = "red_flower", toForm = "red_flower"),
                GoDexEvolutionEdge(670, 671, fromForm = "red_flower", toForm = "red_flower")
            )
        )
        val entries = listOf(
            entry("0669_red-none", 669, "red", needed = false, name = "Flabebe Red"),
            entry("0670_red-none", 670, "red", needed = false, name = "Floette Red"),
            entry("0671_red-none", 671, "red", needed = true, name = "Florges Red")
        )

        val result = matchResult(669, "Red", entries, graph = graph)

        assertEquals(GoDexMatchStatus.EVOLUTION_NEEDED, result.status)
        assertEquals("0671_red-none", result.evolutionTargets.single().entryKey)
        assertEquals(2, result.evolutionTargets.single().distance)
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
        graph: GoDexEvolutionGraph? = null,
        formChangeGraph: GoDexFormChangeGraph? = null
    ) = GoDexMatcher.match(
        PokemonAlert(name = "Pokemon", pokedexId = dex, pokemonForm = form, gender = gender),
        entries,
        configured = true,
        evolutionGraph = graph,
        formChangeGraph = formChangeGraph
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
