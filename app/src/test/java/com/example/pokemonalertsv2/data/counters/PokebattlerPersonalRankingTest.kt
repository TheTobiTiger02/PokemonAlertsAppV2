package com.example.pokemonalertsv2.data.counters

import com.example.pokemonalertsv2.data.pokegenie.OwnedPokemon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PokebattlerPersonalRankingTest {

    private val owned = OwnedPokemon(
        displayName = "Necrozma Dawn Wings",
        form = "Dawn Wings",
        level = 37.5,
        atkIv = 0,
        defIv = 0,
        staIv = 0,
        cp = 3000,
        quickMove = "Shadow Claw",
        chargeMove = "Moongeist Beam",
        shadow = false,
        lucky = false,
        matchKeys = listOf("NECROZMA_DAWN_WINGS_FORM")
    )

    @Test
    fun `exact level uses Pokebattler level path and anonymous result ignores IVs`() {
        assertEquals("levels/37.5", AttackerSpec.ExactLevel(37.5).pathSegment)
        val move = PbByMove(
            move1 = "SHADOW_CLAW_FAST",
            move2 = "MOONGEIST_BEAM",
            result = PbResult(
                estimator = 1.25,
                overallRating = 0.25,
                power = 1.5,
                tdo = 220.0,
                effectiveDeaths = 1.5,
                totalCombatTime = 125_000.0
            )
        )
        val row = PbDefender(
            pokemonId = "NECROZMA_DAWN_WINGS_FORM",
            stats = PbStats(level = "37.5"),
            byMove = listOf(move)
        ).toPersonalCounter(owned, move, 37.5, movesetAssumed = false)

        assertTrue(row.serverBacked)
        assertTrue(row.rankingIgnoresIv)
        assertEquals(1.25, row.metrics.estimator!!, 1e-9)
        assertEquals(400.0, row.metrics.overallPercent!!, 1e-9)
        assertEquals(66.666666, row.metrics.powerPercent!!, 1e-5)
        assertEquals(125.0, row.metrics.timeToWinSeconds!!, 1e-9)
        assertEquals(37.5, row.evaluatedLevel!!, 1e-9)
    }

    @Test
    fun `current move matching is normalized while potential flags assumptions`() {
        assertTrue(personalMovesMatch("SHADOW_CLAW_FAST", "Shadow Claw"))
        assertTrue(personalMovesMatch("MOONGEIST_BEAM", "Moongeist Beam"))
        assertFalse(personalMovesMatch("PSYCHO_CUT_FAST", "Shadow Claw"))

        val candidate = PbByMove(
            move1 = "SHADOW_CLAW_FAST",
            move2 = "MOONGEIST_BEAM",
            result = PbResult(estimator = 1.0, totalCombatTime = 100_000.0)
        )
        val defender = PbDefender(
            pokemonId = "NECROZMA_DAWN_WINGS_FORM",
            byMove = listOf(candidate)
        )
        val current = defender.toPersonalCounter(owned, candidate, 37.5, movesetAssumed = false)
        val potential = defender.toPersonalCounter(owned.copy(chargeMove = null), candidate, 37.5, movesetAssumed = true)
        assertFalse(current.movesetAssumed)
        assertTrue(potential.movesetAssumed)
    }
}
