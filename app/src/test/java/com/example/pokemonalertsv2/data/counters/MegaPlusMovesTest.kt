package com.example.pokemonalertsv2.data.counters

import com.example.pokemonalertsv2.data.pokegenie.OwnedPokemon
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MegaPlusMovesTest {
    private fun owned(id: String = "MEWTWO_MEGA_Y") = OwnedPokemon(
        displayName = prettifyPokemonName(id), form = null, level = 40.0,
        atkIv = 15, defIv = 15, staIv = 15, cp = 5000,
        quickMove = "Psycho Cut", chargeMove = "Psystrike", chargeMove2 = "Shadow Ball",
        shadow = false, lucky = false, matchKeys = listOf(id)
    )

    private fun move(charge: String, estimator: Double = 1.0, fast: String = "PSYCHO_CUT_FAST") =
        PbByMove(fast, charge, PbResult(estimator = estimator, tdo = estimator * 100.0))

    private fun choose(
        mine: OwnedPokemon,
        moves: List<PbByMove>,
        mode: PersonalMovesMode = PersonalMovesMode.CURRENT,
        id: String = mine.matchKeys.first(),
        metric: CounterMetric = CounterMetric.ESTIMATOR
    ) = selectOwnedMoves(PbDefender(id, byMove = moves), mine, mode, metric)

    @Test
    fun `special variants normalize without conflating ordinary moves or levels`() {
        assertTrue(personalMovesMatch("FUTURE_SIGHT_PLUS", "Future Sight+"))
        assertTrue(personalMovesMatch(" future_sight_plus_2 ", "Future Sight++"))
        assertTrue(personalMovesMatch("FUTURE_SIGHT_PLUS_1", "Future Sight +"))
        assertTrue(personalMovesMatch("SHADOW_CLAW_FAST", "Shadow Claw"))
        assertTrue(personalMovesMatch("FUTURESIGHT", "Future Sight"))
        assertFalse(personalMovesMatch("FUTURESIGHT", "Future Sight+"))
        assertFalse(personalMovesMatch("FUTURE_SIGHT_PLUS", "FUTURE_SIGHT_PLUS_2"))
        assertNull(parsePlusMove("PLUS"))
        assertNull(parsePlusMove("FUTURE_SIGHT_PLUS_0"))
        assertNull(parsePlusMove("FUTURE_SIGHT_PLUS_EXTRA"))
    }

    @Test
    fun `special move labels preserve the plus level and existing punctuation`() {
        assertEquals("Future Sight+", prettifyMoveName("FUTURE_SIGHT_PLUS"))
        assertEquals("Dynamic Punch++++", prettifyMoveName("DYNAMIC_PUNCH_PLUS_4"))
        assertEquals("Future Sight++", prettifyMoveName("Future Sight++"))
        assertEquals("X-Scissor+", prettifyMoveName("X_SCISSOR_PLUS"))
        assertEquals("Shadow Ball", prettifyMoveName("SHADOW_BALL"))
    }

    @Test
    fun `returned special moves are available in both modes without editing ownership`() {
        val mine = owned()
        val original = mine.copy()
        for (mode in PersonalMovesMode.entries) {
            for (charge in listOf("FUTURE_SIGHT_PLUS", "FUTURE_SIGHT_PLUS_4", "Future Sight++", "NEW_MOVE_PLUS")) {
                val candidate = move(charge)
                val choice = checkNotNull(choose(mine, listOf(move("PSYSTRIKE", 2.0), candidate), mode))
                assertSame(candidate, choice.move)
                assertFalse(choice.assumed)
                assertFalse(choice.unlisted)
                assertEquals(original, mine)
            }
        }
    }

    @Test
    fun `missing recorded charges can be satisfied by the returned special move only`() {
        for (mode in PersonalMovesMode.entries) {
            for (blank in listOf(null, "")) {
                val mine = owned().copy(chargeMove = blank, chargeMove2 = blank)
                val plus = move("FUTURE_SIGHT_PLUS", 2.0)
                val choice = checkNotNull(choose(mine, listOf(move("PSYCHIC", 1.0), plus), mode))
                assertSame(plus, choice.move)
                assertFalse(choice.assumed)
                assertFalse(choice.unlisted)
            }
        }
        assertNull(choose(owned().copy(chargeMove = null, chargeMove2 = null), listOf(move("PSYCHIC"))))
        assertNull(choose(owned().copy(quickMove = null), listOf(move("FUTURE_SIGHT_PLUS"))))
    }

    @Test
    fun `recorded fast move is still required and ordinary charges can win`() {
        for (mode in PersonalMovesMode.entries) {
            val mine = owned()
            val recorded = move("PSYSTRIKE", 2.0)
            val wrongFastPlus = move("FUTURE_SIGHT_PLUS", 0.5, "CONFUSION_FAST")
            assertSame(recorded, choose(mine, listOf(recorded, wrongFastPlus), mode)!!.move)
            val secondCharge = move("SHADOW_BALL", 0.5)
            assertSame(secondCharge, choose(mine, listOf(recorded, move("FUTURE_SIGHT_PLUS"), secondCharge), mode)!!.move)
        }
        val floor = choose(owned(), listOf(move("FUTURE_SIGHT_PLUS", fast = "CONFUSION_FAST")))!!
        assertTrue(floor.unlisted)
        val potential = choose(owned(), listOf(move("FUTURE_SIGHT_PLUS", fast = "CONFUSION_FAST")), PersonalMovesMode.BEST_POTENTIAL)!!
        assertTrue(potential.assumed)
    }

    @Test
    fun `selection uses the requested metric and keeps the exact special variant`() {
        val basic = move("FUTURE_SIGHT_PLUS", 1.0)
        val numbered = move("FUTURE_SIGHT_PLUS_2", 2.0)
        assertSame(basic, choose(owned(), listOf(basic, numbered))!!.move)
        assertSame(numbered, choose(owned(), listOf(basic, numbered), metric = CounterMetric.TDO)!!.move)
    }

    @Test
    fun `automatic availability does not leak to base shadow primal or unrelated mega forms`() {
        val regular = move("PSYSTRIKE", 2.0)
        val plus = move("FUTURE_SIGHT_PLUS")
        val cases = listOf(
            owned("MEWTWO") to "MEWTWO",
            owned("MEWTWO_SHADOW_FORM").copy(shadow = true) to "MEWTWO_SHADOW_FORM",
            owned("MEWTWO_MEGA_Y_SHADOW_FORM").copy(shadow = true) to "MEWTWO_MEGA_Y_SHADOW_FORM",
            owned("KYOGRE_PRIMAL") to "KYOGRE_PRIMAL",
            owned("MEWTWO_MEGA_X") to "MEWTWO_MEGA_Y"
        )
        for ((mine, id) in cases) {
            for (mode in PersonalMovesMode.entries) {
                assertSame(id, regular, choose(mine, listOf(regular, plus), mode, id)!!.move)
            }
        }
        val baseWithOrdinary = owned("MEWTWO").copy(chargeMove = "Future Sight", chargeMove2 = null)
        assertSame(regular, choose(baseWithOrdinary, listOf(regular, plus))!!.move)
        assertTrue(choose(baseWithOrdinary, listOf(regular, plus))!!.unlisted)
    }

    /** Live capture on 2026-09-02: TERRAKION / RAID_LEVEL_5 / levels/40 /
     * CINEMATIC_ATTACK_WHEN_POSSIBLE / DEFENSE_RANDOM_MC, trimmed to Mega Mewtwo X/Y. */
    @Test
    fun `captured Pokebattler recommendation retains metrics ownership and displayed special move`() {
        val fixture = checkNotNull(javaClass.classLoader?.getResourceAsStream("terrakion_mega_mewtwo_level40.json"))
            .bufferedReader().use { it.readText() }
        val response = Json { ignoreUnknownKeys = true }.decodeFromString<PokebattlerCountersResponse>(fixture)
        val roster = listOf(owned("MEWTWO_MEGA_X"), owned())
        for (mode in PersonalMovesMode.entries) {
            val ranked = rankPersonalLevel(40.0, roster, response, mode, CounterMetric.ESTIMATOR, null)
            assertEquals(2, ranked.size)
            for ((index, counter) in ranked.withIndex()) {
                val expectedId = if (index == 0) "DYNAMIC_PUNCH_PLUS" else "FUTURE_SIGHT_PLUS"
                val expectedLabel = if (index == 0) "Dynamic Punch+" else "Future Sight+"
                assertEquals(expectedId, counter.chargedMove.moveId)
                assertEquals(expectedLabel, prettifyMoveName(counter.chargedMove.moveId))
                assertEquals("PSYCHO_CUT_FAST", counter.fastMove.moveId)
                assertSame(roster[index], counter.owned)
                assertEquals("Psystrike", counter.owned.chargeMove)
                assertEquals("Shadow Ball", counter.owned.chargeMove2)
                assertFalse(counter.movesetAssumed)
                assertFalse(counter.movesetUnlisted)
                val sourceMove = response.attackers.single().randomMove!!.defenders
                    .single { it.pokemonId == counter.pokemonId }.byMove
                    .single { it.move1 == "PSYCHO_CUT_FAST" && it.move2 == expectedId }
                assertEquals(sourceMove.result.toNormalizedMetrics(), counter.metrics)
                assertTrue(counter.serverBacked)
            }
            assertEquals("FUTURE_SIGHT_PLUS", suggestTeam(ranked, "MEWTWO_MEGA_Y").single().counter.chargedMove.moveId)
            assertTrue(suggestTeam(ranked, null).isEmpty())
        }
        assertTrue(rankPersonalLevel(40.0, listOf(owned("MEWTWO")), response, PersonalMovesMode.CURRENT,
            CounterMetric.ESTIMATOR, null).isEmpty())
    }
}
