package com.example.pokemonalertsv2.data.counters

import com.example.pokemonalertsv2.data.pokegenie.OwnedPokemon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for compact recommended-team query generation. */
class PokemonGoSearchTest {

    private fun counter(
        displayName: String,
        pokemonId: String,
        cp: Int?,
        quick: String?,
        charge: String?,
        shadow: Boolean = displayName.startsWith("Shadow"),
        rankedFast: String = "RANKED_FAST",
        rankedCharged: String = "RANKED_CHARGED"
    ) = PersonalCounter(
        owned = OwnedPokemon(
            displayName = displayName,
            form = null,
            level = 40.0,
            atkIv = 15,
            defIv = 15,
            staIv = 15,
            cp = cp,
            quickMove = quick,
            chargeMove = charge,
            shadow = shadow,
            lucky = false,
            matchKeys = listOf(pokemonId)
        ),
        pokemonId = pokemonId,
        displayName = displayName,
        fastMove = syntheticFastMove(rankedFast),
        chargedMove = syntheticChargedMove(rankedCharged),
        movesetAssumed = false,
        dps = 10.0,
        tdo = 100.0,
        rating = 50.0,
        estimatedAttackers = 6.0
    )

    private fun slot(vararg copies: PersonalCounter) =
        PersonalTeamSlot(copies.first(), copies.size, copies.toList())

    private val shadowChandelure = counter(
        "Shadow Chandelure", "CHANDELURE_SHADOW_FORM", 3555, "Fire Spin", "Overheat"
    )
    private val shadowReshiram = counter(
        "Shadow Reshiram", "RESHIRAM_SHADOW_FORM", 4499, "Fire Fang", "Fusion Flare"
    )
    private val regularChandelure = counter(
        "Chandelure", "CHANDELURE", 3268, "Hex", "Shadow Ball", shadow = false
    )
    private val websiteTeam = listOf(
        slot(shadowChandelure),
        slot(shadowReshiram),
        slot(regularChandelure)
    )
    private val dex = mapOf(
        "CHANDELURE_SHADOW_FORM" to 609,
        "RESHIRAM_SHADOW_FORM" to 643,
        "CHANDELURE" to 609
    )

    @Test
    fun `the app team is a compact grouped Pokemon GO query`() {
        val query = PokemonGoSearch.teamQuery(websiteTeam, dex)

        assertEquals(
            "609,643&CP3555,CP4499,CP3268&@Fire Spin,@Fire Fang,@Hex&" +
                "@Overheat,@Fusion Flare,@Shadow Ball",
            query
        )
    }

    @Test
    fun `a single shadow Pokemon needs no shadow predicate when CP identifies the copy`() {
        assertEquals(
            "609&CP3555&@Fire Spin&@Overheat",
            PokemonGoSearch.teamQuery(listOf(slot(shadowChandelure)), dex)
        )
    }

    @Test
    fun `regular Pokemon do not receive a negative shadow predicate`() {
        assertEquals(
            "609&CP3268&@Hex&@Shadow Ball",
            PokemonGoSearch.teamQuery(listOf(slot(regularChandelure)), dex)
        )
    }

    @Test
    fun `moves come from the owned copy and never from the ranked moveset`() {
        val query = PokemonGoSearch.teamQuery(websiteTeam, dex)
        assertFalse(query.contains("RANKED_FAST"))
        assertFalse(query.contains("RANKED_CHARGED"))
        assertTrue(query.contains("@Fire Spin"))
        assertTrue(query.contains("@Fusion Flare"))
    }

    @Test
    fun `missing data weakens only the affected Pokemon term`() {
        val missingCp = counter(
            "Shadow Chandelure", "CHANDELURE_SHADOW_FORM", null, "Fire Fang", "Overheat"
        )
        val missingFast = counter(
            "Shadow Chandelure", "CHANDELURE_SHADOW_FORM", 4499, null, "Overheat"
        )

        assertEquals(
            "609&@Fire Fang&@Overheat",
            PokemonGoSearch.teamQuery(listOf(slot(missingCp)), dex)
        )
        assertEquals(
            "609&CP4499&@Overheat",
            PokemonGoSearch.teamQuery(listOf(slot(missingFast)), dex)
        )
    }

    @Test
    fun `zero CP and blank moves are treated as missing data`() {
        val sparse = counter(
            "Shadow Chandelure", "CHANDELURE_SHADOW_FORM", 0, " ", "Overheat"
        )
        assertEquals(
            "609&@Overheat",
            PokemonGoSearch.teamQuery(listOf(slot(sparse)), dex)
        )
    }

    @Test
    fun `an unknown dex number falls back to the stripped species name`() {
        val query = PokemonGoSearch.teamQuery(
            listOf(
                slot(counter("Kyurem (Black)", "KYUREM_BLACK_FORM", 4000, "Dragon Breath", "Fusion Bolt"))
            ),
            emptyMap()
        )
        assertEquals("kyurem&CP4000&@Dragon Breath&@Fusion Bolt", query)
    }

    @Test
    fun `the species fallback strips mega shadow and form decorations`() {
        assertEquals("charizard", PokemonGoSearch.searchName("Mega Charizard Y"))
        assertEquals("gengar", PokemonGoSearch.searchName("Mega Gengar"))
        assertEquals("kyogre", PokemonGoSearch.searchName("Primal Kyogre"))
        assertEquals("kyurem", PokemonGoSearch.searchName("Kyurem (Black)"))
        assertEquals("machamp", PokemonGoSearch.searchName("Shadow Machamp"))
        assertEquals("machamp", PokemonGoSearch.searchName("Machamp"))
    }

    @Test
    fun `the species-only fallback remains compact and deduplicated`() {
        assertEquals("609,643", PokemonGoSearch.speciesQuery(websiteTeam, dex))
    }

    @Test
    fun `species are stable in dex order even when ranking order changes`() {
        val reverseRanked = listOf(
            slot(counter("Eternatus", "ETERNATUS", 4966, "Dragon Tail", "Dynamax Cannon")),
            slot(counter("Kyurem", "KYUREM_BLACK_FORM", 5206, "Dragon Tail", "Freeze Shock")),
            slot(counter("Necrozma", "NECROZMA_DAWN_WINGS", 4634, "Shadow Claw", "Moongeist Beam")),
            slot(counter("Garchomp", "GARCHOMP", 4370, "Dragon Tail", "Breaking Swipe"))
        )
        val dexNumbers = mapOf(
            "GARCHOMP" to 445,
            "KYUREM_BLACK_FORM" to 646,
            "NECROZMA_DAWN_WINGS" to 800,
            "ETERNATUS" to 890
        )

        assertTrue(PokemonGoSearch.teamQuery(reverseRanked, dexNumbers).startsWith("445,646,800,890&"))
        assertEquals("445,646,800,890", PokemonGoSearch.speciesQuery(reverseRanked, dexNumbers))
    }

    @Test
    fun `shadow Giratina Altered recommended team has the requested grouped shape`() {
        val team = listOf(
            slot(counter("Garchomp", "GARCHOMP", 4370, "Dragon Tail", "Breaking Swipe")),
            slot(counter("Kyurem", "KYUREM_BLACK_FORM", 5206, "Dragon Tail", "Freeze Shock")),
            slot(counter("Necrozma", "NECROZMA_DAWN_WINGS", 4634, "Shadow Claw", "Moongeist Beam")),
            slot(counter("Garchomp", "GARCHOMP", 4436, "Dragon Tail", "Breaking Swipe")),
            slot(counter("Garchomp", "GARCHOMP", 4459, "Dragon Tail", "Breaking Swipe")),
            slot(counter("Eternatus", "ETERNATUS", 4966, "Dragon Tail", "Dynamax Cannon"))
        )

        assertEquals(
            "445,646,800,890&CP4370,CP5206,CP4634,CP4436,CP4459,CP4966&" +
                "@Dragon Tail,@Shadow Claw&" +
                "@Breaking Swipe,@Freeze Shock,@Moongeist Beam,@Dynamax Cannon",
            PokemonGoSearch.teamQuery(
                team,
                mapOf(
                    "GARCHOMP" to 445,
                    "KYUREM_BLACK_FORM" to 646,
                    "NECROZMA_DAWN_WINGS" to 800,
                    "ETERNATUS" to 890
                )
            )
        )
    }

    @Test
    fun `an empty team produces no stray separators`() {
        assertEquals("", PokemonGoSearch.teamQuery(emptyList(), dex))
        assertEquals("", PokemonGoSearch.speciesQuery(emptyList(), dex))
    }
}
