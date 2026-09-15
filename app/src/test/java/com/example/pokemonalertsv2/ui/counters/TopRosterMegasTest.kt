package com.example.pokemonalertsv2.ui.counters

import com.example.pokemonalertsv2.data.counters.PersonalCounter
import com.example.pokemonalertsv2.data.counters.syntheticChargedMove
import com.example.pokemonalertsv2.data.counters.syntheticFastMove
import com.example.pokemonalertsv2.data.gamemaster.MegaSpecies
import com.example.pokemonalertsv2.data.pokegenie.OwnedPokemon
import org.junit.Assert.assertEquals
import org.junit.Test

class TopRosterMegasTest {
    private fun counter(pokemonId: String, dps: Double) = PersonalCounter(
        owned = OwnedPokemon(displayName = pokemonId, form = null, level = 40.0, atkIv = 15, defIv = 15, staIv = 15, cp = 3000,
            quickMove = "Counter", chargeMove = "Dynamic Punch", shadow = false, lucky = false, matchKeys = listOf(pokemonId)),
        pokemonId = pokemonId, displayName = pokemonId, fastMove = syntheticFastMove("COUNTER"),
        chargedMove = syntheticChargedMove("DYNAMIC_PUNCH"), movesetAssumed = false, dps = dps, tdo = 100.0, rating = 50.0,
        estimatedAttackers = 6.0
    )

    private val megas = listOf("LUCARIO_MEGA", "GENGAR_MEGA", "CHARIZARD_MEGA_Y", "BLAZIKEN_MEGA", "GARCHOMP_MEGA", "ALAKAZAM_MEGA", "MEWTWO_MEGA_X")
        .map { MegaSpecies(it, it.lowercase(), it.substringBefore("_MEGA")) }

    @Test
    fun `megas come in ranking order, once each, with their overall rank and best copy`() {
        val ranked = listOf(
            counter("MACHAMP_SHADOW_FORM", 20.0), counter("LUCARIO_MEGA", 19.0), counter("CONKELDURR", 18.0),
            counter("LUCARIO_MEGA", 17.0), counter("blaziken_mega", 16.0), counter("GENGAR_MEGA", 15.0)
        )
        val top = topRosterMegas(ranked, megas)
        assertEquals(listOf("LUCARIO_MEGA", "BLAZIKEN_MEGA", "GENGAR_MEGA"), top.map { it.mega.pokemonId })
        assertEquals(listOf(2, 5, 6), top.map { it.overallRank })
        assertEquals(19.0, top.first().best.dps, 0.0)
    }

    @Test
    fun `the shortlist stops at six and is empty without megas in the ranking`() {
        val ranked = megas.mapIndexed { i, mega -> counter(mega.pokemonId, 30.0 - i) }
        assertEquals(TOP_MEGA_COUNT, topRosterMegas(ranked, megas).size)
        assertEquals(emptyList<TopMega>(), topRosterMegas(listOf(counter("MACHAMP", 20.0)), megas))
    }
}
