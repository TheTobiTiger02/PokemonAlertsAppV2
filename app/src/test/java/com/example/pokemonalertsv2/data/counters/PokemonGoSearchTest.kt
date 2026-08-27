package com.example.pokemonalertsv2.data.counters

import com.example.pokemonalertsv2.data.pokegenie.OwnedPokemon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The Pokémon GO search grammar.
 *
 * Pinned to a real Poké Genie string, because the shape is the whole point: `,` binds tighter
 * than `&`, so the query has to be one OR-group per attribute, ANDed — not one term per
 * Pokémon, which parses into a contradiction and matches nothing.
 */
class PokemonGoSearchTest {

    private fun counter(
        displayName: String,
        pokemonId: String,
        cp: Int?,
        quick: String?,
        charge: String?,
        // What the ranking scored, which in "Best potential" mode the Pokémon may not know.
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
            shadow = displayName.startsWith("Shadow"),
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

    // 1 Shadow Reshiram, 1 Shadow Groudon, 4 Shadow Chandelure — the user's own team.
    private fun reshiram() = counter(
        "Shadow Reshiram", "RESHIRAM_SHADOW_FORM", 3541, "Fire Spin", "Fusion Flare"
    )

    private fun groudon() = counter(
        "Shadow Groudon", "GROUDON_SHADOW_FORM", 3556, "Mud Shot", "Precipice Blades"
    )

    private fun chandelure(cp: Int) = counter(
        "Shadow Chandelure", "CHANDELURE_SHADOW_FORM", cp, "Fire Fang", "Overheat"
    )

    private fun team() = listOf(
        slot(reshiram()),
        slot(groudon()),
        slot(chandelure(4499), chandelure(4566), chandelure(3555), chandelure(3544))
    )

    private val dex = mapOf(
        "RESHIRAM_SHADOW_FORM" to 643,
        "GROUDON_SHADOW_FORM" to 383,
        "CHANDELURE_SHADOW_FORM" to 609
    )

    @Test
    fun `the query matches the shape Poke Genie emits`() {
        assertEquals(
            "643,383,609" +
                "&CP3541,CP3556,CP4499,CP4566,CP3555,CP3544" +
                "&@Fire Spin,@Mud Shot,@Fire Fang" +
                "&@Fusion Flare,@Precipice Blades,@Overheat",
            PokemonGoSearch.teamQuery(team(), dex)
        )
    }

    @Test
    fun `there is one CP per copy, not one per species`() {
        val query = PokemonGoSearch.teamQuery(team(), dex)
        val cps = query.split("&")[1].split(",")
        // Six Pokemon in the party, so six CPs; the four Chandelure are four distinct copies.
        assertEquals(6, cps.size)
        assertEquals(listOf("CP4499", "CP4566", "CP3555", "CP3544"), cps.drop(2))
    }

    @Test
    fun `species and moves are deduplicated`() {
        val groups = PokemonGoSearch.teamQuery(team(), dex).split("&")
        assertEquals(4, groups.size)
        assertEquals(3, groups[0].split(",").size)
        assertEquals(3, groups[2].split(",").size)
        assertEquals(3, groups[3].split(",").size)
    }

    @Test
    fun `species are dex numbers, so mega and shadow naming never comes up`() {
        val query = PokemonGoSearch.teamQuery(
            listOf(slot(counter("Mega Rayquaza", "RAYQUAZA_MEGA", 6458, "Dragon Tail", "Breaking Swipe"))),
            mapOf("RAYQUAZA_MEGA" to 384)
        )
        assertEquals("384&CP6458&@Dragon Tail&@Breaking Swipe", query)
    }

    @Test
    fun `moves come from the owned copy, never from the ranked moveset`() {
        val query = PokemonGoSearch.teamQuery(team(), dex)
        assertFalse(query.contains("RANKED_FAST"))
        assertFalse(query.contains("RANKED_CHARGED"))
    }

    @Test
    fun `one copy without a CP drops the whole CP group, not just that copy`() {
        // The groups are ANDed, so a Pokemon missing from the CP group would be excluded
        // from the entire result. Widening the query is the only correct degradation.
        val mixed = listOf(
            slot(reshiram()),
            slot(chandelure(4499), counter("Shadow Chandelure", "CHANDELURE_SHADOW_FORM", null, "Fire Fang", "Overheat"))
        )
        assertEquals(
            "643,609&@Fire Spin,@Fire Fang&@Fusion Flare,@Overheat",
            PokemonGoSearch.teamQuery(mixed, dex)
        )
    }

    @Test
    fun `a zero CP is missing data, not a real CP`() {
        val query = PokemonGoSearch.teamQuery(listOf(slot(chandelure(0))), dex)
        assertEquals("609&@Fire Fang&@Overheat", query)
    }

    @Test
    fun `a missing fast move drops only the fast group`() {
        val noFast = listOf(
            slot(counter("Shadow Chandelure", "CHANDELURE_SHADOW_FORM", 4499, null, "Overheat"))
        )
        assertEquals("609&CP4499&@Overheat", PokemonGoSearch.teamQuery(noFast, dex))
    }

    @Test
    fun `an unknown dex number falls back to the stripped species name`() {
        val query = PokemonGoSearch.teamQuery(
            listOf(slot(counter("Kyurem (Black)", "KYUREM_BLACK_FORM", 4000, "Dragon Breath", "Fusion Bolt"))),
            emptyMap()
        )
        assertEquals("kyurem&CP4000&@Dragon Breath&@Fusion Bolt", query)
    }

    @Test
    fun `the species fallback strips mega, shadow and form decorations`() {
        assertEquals("charizard", PokemonGoSearch.searchName("Mega Charizard Y"))
        assertEquals("gengar", PokemonGoSearch.searchName("Mega Gengar"))
        assertEquals("kyogre", PokemonGoSearch.searchName("Primal Kyogre"))
        assertEquals("kyurem", PokemonGoSearch.searchName("Kyurem (Black)"))
        assertEquals("machamp", PokemonGoSearch.searchName("Shadow Machamp"))
        assertEquals("machamp", PokemonGoSearch.searchName("Machamp"))
    }

    @Test
    fun `no shadow keyword, which would exclude every non-shadow member`() {
        assertFalse(PokemonGoSearch.teamQuery(team(), dex).contains("shadow"))
    }

    @Test
    fun `the species-only fallback is the first group alone`() {
        assertEquals("643,383,609", PokemonGoSearch.speciesQuery(team(), dex))
    }

    @Test
    fun `an empty team produces an empty query rather than stray separators`() {
        assertEquals("", PokemonGoSearch.teamQuery(emptyList(), dex))
        assertEquals("", PokemonGoSearch.speciesQuery(emptyList(), dex))
    }
}
