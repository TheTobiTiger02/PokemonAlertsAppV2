package com.example.pokemonalertsv2.megaboost

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MegaBoostTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val poison = "POKEMON_TYPE_POISON"
    private val flying = "POKEMON_TYPE_FLYING"
    private val normal = "POKEMON_TYPE_NORMAL"
    private val fire = "POKEMON_TYPE_FIRE"
    private val ghost = "POKEMON_TYPE_GHOST"
    private val electric = "POKEMON_TYPE_ELECTRIC"
    private val psychic = "POKEMON_TYPE_PSYCHIC"

    /** Zubat Poison/Flying, Pidgey Normal/Flying, Rattata Normal, Charmander Fire; Raichu Electric, Alolan Raichu Electric/Psychic. */
    private val types = mapOf(
        LiveSpecies(41) to listOf(poison, flying), LiveSpecies(16) to listOf(normal, flying),
        LiveSpecies(19) to listOf(normal), LiveSpecies(4) to listOf(fire),
        LiveSpecies(26) to listOf(electric), LiveSpecies(26, 50) to listOf(electric, psychic),
    )
    private fun s(dex: Int, form: Int? = null) = LiveSpecies(dex, form)

    private val response = json.decodeFromString<LiveSpeciesResponse>(
        """{"generatedAt":"2026-09-16T12:00:00.000Z","areas":[
            {"area":"Alsbach","total":4,"species":[{"pokemonId":41,"count":2},{"pokemonId":26,"formId":50,"count":1},{"pokemonId":26,"formId":null,"count":1}]},
            {"area":"Darmstadt-North","total":6,"species":[{"pokemonId":41,"formId":null,"count":3},{"pokemonId":16,"count":2},{"pokemonId":999,"count":1}],"extra":1}
        ]}""")

    private fun mega(id: String, vararg types: String, owned: Boolean = false) = MegaCandidate(id, id, types.toList(), owned)

    @Test fun `counts come per area, or summed for all areas`() {
        assertEquals(mapOf(s(41) to 2, s(26, 50) to 1, s(26) to 1), liveCounts(response, "Alsbach"))
        assertEquals(mapOf(s(41) to 5, s(16) to 2, s(999) to 1, s(26, 50) to 1, s(26) to 1), liveCounts(response, ALL_AREAS))
        assertTrue(liveCounts(response, "Nowhere").isEmpty())
    }

    @Test fun `a dual type Pokemon adds to both of its types, unknown species to none`() {
        assertEquals(listOf(flying to 5, poison to 3, normal to 2), typeTotals(liveCounts(response, "Darmstadt-North"), types))
    }

    @Test fun `a Pokemon matching both of a mega's types is boosted once`() {
        val counts = mapOf(s(41) to 3, s(16) to 2, s(19) to 4, s(4) to 1)
        val ranking = rankMegaBoost(counts, types, listOf(
            mega("BEEDRILL_MEGA", "POKEMON_TYPE_BUG", poison),
            mega("PIDGEOT_MEGA", normal, flying),
            mega("CHARIZARD_MEGA_Y", fire, flying),
            mega("GENGAR_MEGA", ghost, poison),
        ))
        assertEquals(listOf("PIDGEOT_MEGA", "CHARIZARD_MEGA_Y", "BEEDRILL_MEGA", "GENGAR_MEGA"), ranking.map { it.mega.pokemonId })
        // Pidgeot: Zubat 3 (Flying) + Pidgey 2 (both types, once) + Rattata 4 = 9 of 10.
        assertEquals(9, ranking[0].boosted)
        assertEquals(10, ranking[0].total)
        assertEquals(0.9, ranking[0].share, 1e-9)
        // Charizard Y: Zubat 3 + Pidgey 2 + Charmander 1.
        assertEquals(6, ranking[1].boosted)
        assertEquals(3, ranking.first { it.mega.pokemonId == "GENGAR_MEGA" }.boosted)
    }

    @Test fun `a regional form counts with its own types, an unknown form with the species' types`() {
        // Alolan Raichu is Psychic, plain Raichu is not; form 49 has no entry of its own.
        val counts = mapOf(s(26, 50) to 2, s(26) to 1, s(26, 49) to 4)
        assertEquals(listOf(electric to 7, psychic to 2), typeTotals(counts, types))
        val ranking = rankMegaBoost(counts, types, listOf(mega("ALAKAZAM_MEGA", psychic), mega("MANECTRIC_MEGA", electric)))
        assertEquals(listOf(7, 2), ranking.map { it.boosted })
    }

    @Test fun `Primal Groudon, Primal Kyogre and Mega Rayquaza boost their weather's types`() {
        val grass = "POKEMON_TYPE_GRASS"
        val bug = "POKEMON_TYPE_BUG"
        val weatherTypes = types + mapOf(s(1) to listOf(grass, poison), s(10) to listOf(bug), s(63) to listOf(psychic))
        // Bulbasaur 2, Charmander 1, Caterpie 3, Raichu 4, Abra 5, Pidgey 6.
        val counts = mapOf(s(1) to 2, s(4) to 1, s(10) to 3, s(26) to 4, s(63) to 5, s(16) to 6)
        val ranking = rankMegaBoost(counts, weatherTypes, listOf(
            mega("GROUDON_PRIMAL", "POKEMON_TYPE_GROUND", fire),
            mega("KYOGRE_PRIMAL", "POKEMON_TYPE_WATER"),
            mega("RAYQUAZA_MEGA", "POKEMON_TYPE_DRAGON", flying),
        )).associate { it.mega.pokemonId to it.boosted }
        assertEquals(mapOf("RAYQUAZA_MEGA" to 11, "KYOGRE_PRIMAL" to 7, "GROUDON_PRIMAL" to 3), ranking)
        assertEquals(listOf(fire, grass, "POKEMON_TYPE_GROUND"), mega("GROUDON_PRIMAL", "POKEMON_TYPE_GROUND").boostTypes)
        assertEquals(listOf(ghost), mega("GENGAR_MEGA", ghost).boostTypes)
    }

    @Test fun `megas in the roster come first, then most boosted, then name`() {
        val counts = mapOf(s(41) to 3, s(19) to 4)
        val ranking = rankMegaBoost(counts, types, listOf(
            mega("PIDGEOT_MEGA", normal, flying),
            mega("GENGAR_MEGA", ghost, poison, owned = true),
            mega("B_MEGA", fire, owned = true),
            mega("A_MEGA", fire, owned = true),
        ))
        assertEquals(listOf("GENGAR_MEGA", "A_MEGA", "B_MEGA", "PIDGEOT_MEGA"), ranking.map { it.mega.pokemonId })
        assertEquals(0.0, rankMegaBoost(emptyMap(), types, listOf(mega("A_MEGA", fire))).single().share, 0.0)
    }
}
