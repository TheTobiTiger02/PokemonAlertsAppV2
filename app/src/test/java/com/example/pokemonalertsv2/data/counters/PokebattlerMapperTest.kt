package com.example.pokemonalertsv2.data.counters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PokebattlerMapperTest {

    private fun move(fast: String, charged: String, rating: Double, est: Double, tdo: Double, power: Double) =
        PbByMove(fast, charged, PbResult(estimator = est, overallRating = rating, tdo = tdo, power = power))

    private val defender = PbDefender(
        pokemonId = "KYUREM_BLACK_FORM",
        cp = 4605,
        stats = PbStats(attack = 15, defense = 15, stamina = 15, level = "40"),
        total = PbResult(
            estimator = 2.2523618,
            overallRating = 0.5226068,
            tdo = 204.938,
            power = 2.0837,
            effectiveDeaths = 7.02,
            combatTime = 177951.0
        ),
        // Each sort has a different, unambiguous winner so the assertions below cannot
        // pass by accident.
        byMove = listOf(
            move("SHADOW_CLAW_FAST", "IRON_HEAD", rating = 0.60, est = 3.50, tdo = 100.0, power = 5.00),
            move("DRAGON_BREATH_FAST", "OUTRAGE", rating = 0.95, est = 3.00, tdo = 150.0, power = 1.00),
            move("IRON_TAIL_FAST", "CRUNCH", rating = 0.50, est = 2.00, tdo = 120.0, power = 2.00),
            move("BITE_FAST", "SURF", rating = 0.40, est = 4.00, tdo = 300.0, power = 1.50)
        )
    )

    private fun response(vararg defenders: PbDefender) = PokebattlerCountersResponse(
        attackers = listOf(
            PbAttackerBlock(
                pokemonId = "MEWTWO",
                cp = 54148,
                boss = "RAID_LEVEL_5",
                randomMove = PbMoveset("CONFUSION_FAST", "FOCUS_BLAST", defenders.toList())
            )
        )
    )

    @Test
    fun `reads counters from the inverted defenders field`() {
        val payload = response(defender).toPayload(PokebattlerSort.OVERALL)!!
        assertEquals("MEWTWO", payload.bossPokemonId)
        assertEquals(54148, payload.bossCp)
        // Boss moves are kept as raw ids, not display names: the local simulator has to
        // look them up in the game master. The card prettifies them at render time.
        assertEquals("CONFUSION_FAST", payload.bossMove1)
        assertEquals("FOCUS_BLAST", payload.bossMove2)
        assertEquals(1, payload.counters.size)

        val c = payload.counters.first()
        assertEquals(1, c.rank)
        assertEquals("KYUREM_BLACK_FORM", c.pokemonId)
        assertEquals("Kyurem (Black)", c.displayName)
        assertEquals(4605, c.cp)
        assertEquals("40", c.level)
        assertEquals(2.2523618, c.estimator!!, 1e-9)
        // combatTime is milliseconds on the wire.
        assertEquals(177.951, c.timeToWinSeconds!!, 1e-6)
    }

    @Test
    fun `ranks reverse the api order so rank one is the strongest`() {
        // Pokebattler emits `defenders` worst-first: verified against Lunala, where the array
        // runs estimator 1.60 -> 1.11 and the genuine best counter is the LAST element.
        val second = defender.copy(pokemonId = "ETERNATUS")
        val payload = response(defender, second).toPayload(PokebattlerSort.OVERALL)!!
        assertEquals(listOf(1, 2), payload.counters.map { it.rank })
        assertEquals(listOf("ETERNATUS", "KYUREM_BLACK_FORM"), payload.counters.map { it.pokemonId })
    }

    @Test
    fun `best moveset depends on the chosen sort`() {
        fun pick(sort: PokebattlerSort) = selectBestByMove(defender, sort)?.move1
        // Inside byMove, overallRating / estimator / power are COSTS - lower is better.
        // Only tdo is genuinely higher-is-better.
        assertEquals("BITE_FAST", pick(PokebattlerSort.OVERALL))
        assertEquals("DRAGON_BREATH_FAST", pick(PokebattlerSort.POWER))
        assertEquals("IRON_TAIL_FAST", pick(PokebattlerSort.ESTIMATOR))
        assertEquals("IRON_TAIL_FAST", pick(PokebattlerSort.TIME))
        assertEquals("BITE_FAST", pick(PokebattlerSort.TDO))
    }

    @Test
    fun `never headlines the worst moveset of a counter`() {
        // The real Shadow Honchkrow shape against Lunala: Snarl/Dark Pulse scores
        // overallRating 0.3622, Peck/Frustration 19.1847. Taking the maximum is what put
        // "Peck / Frustration" on a Dark counter facing a Psychic/Ghost boss.
        val honchkrow = PbDefender(
            pokemonId = "HONCHKROW_SHADOW_FORM",
            total = PbResult(overallRating = 0.3622, estimator = 1.6431),
            byMove = listOf(
                move("PECK_FAST", "FRUSTRATION", rating = 19.1847, est = 10.8389, tdo = 189.0, power = 9.792),
                move("SNARL_FAST", "DARK_PULSE", rating = 0.3622, est = 1.6431, tdo = 189.0, power = 1.443)
            )
        )
        assertEquals("SNARL_FAST", selectBestByMove(honchkrow, PokebattlerSort.OVERALL)?.move1)
        assertEquals("DARK_PULSE", selectBestByMove(honchkrow, PokebattlerSort.OVERALL)?.move2)
    }

    @Test
    fun `picks the moveset that achieves the total metric`() {
        // Verified against the live API: total.overallRating == min(byMove overallRating).
        val chosen = selectBestByMove(defender, PokebattlerSort.OVERALL)
        val minimum = defender.byMove.minOf { it.result!!.overallRating!! }
        assertEquals(minimum, chosen!!.result!!.overallRating!!, 1e-9)
    }

    @Test
    fun `the persisted moveset is the one the chosen sort headlines`() {
        val overall = response(defender).toPayload(PokebattlerSort.OVERALL)!!.counters.single()
        assertEquals("Bite", overall.fastMove)
        assertEquals("Surf", overall.chargedMove)
        val estimator = response(defender).toPayload(PokebattlerSort.ESTIMATOR)!!.counters.single()
        assertEquals("Iron Tail", estimator.fastMove)
        assertEquals("Crunch", estimator.chargedMove)
    }

    @Test
    fun `normalizes reciprocal percentage metrics`() {
        val counter = response(defender).toPayload(PokebattlerSort.OVERALL)!!.counters.single()
        assertEquals(100.0 / 0.5226068, counter.metrics().overallPercent!!, 1e-9)
        assertEquals(100.0 / 2.0837, counter.metrics().powerPercent!!, 1e-9)
        assertEquals(2.2523618, counter.metrics().estimator!!, 1e-9)
        assertEquals(204.938, counter.metrics().tdo!!, 1e-9)
        assertEquals(7.02, counter.metrics().deaths!!, 1e-9)
    }

    @Test
    fun `fastest chooses combat time rather than estimator`() {
        val timed = defender.copy(
            byMove = listOf(
                move("SLOW_FAST", "SLOW_CHARGE", rating = 1.0, est = 1.0, tdo = 100.0, power = 1.0)
                    .copy(result = PbResult(estimator = 1.0, totalCombatTime = 220_000.0)),
                move("FAST_FAST", "FAST_CHARGE", rating = 2.0, est = 3.0, tdo = 100.0, power = 2.0)
                    .copy(result = PbResult(estimator = 3.0, totalCombatTime = 150_000.0))
            )
        )
        assertEquals("FAST_FAST", selectBestByMove(timed, PokebattlerSort.TIME)?.move1)
    }

    @Test
    fun `exact boss moveset selection exposes average and concrete choices`() {
        val response = PokebattlerCountersResponse(
            attackers = listOf(
                PbAttackerBlock(
                    pokemonId = "MEWTWO",
                    randomMove = PbMoveset("RANDOM", "RANDOM", listOf(defender)),
                    byMove = listOf(
                        PbMoveset("CONFUSION_FAST", "FOCUS_BLAST", listOf(defender)),
                        PbMoveset("PSYCHO_CUT_FAST", "ICE_BEAM", listOf(defender))
                    )
                )
            )
        )
        val payload = response.toPayload(
            PokebattlerSort.ESTIMATOR,
            RaidBossMoveset("PSYCHO_CUT_FAST", "ICE_BEAM")
        )!!
        assertEquals("PSYCHO_CUT_FAST", payload.bossMove1)
        assertEquals("ICE_BEAM", payload.bossMove2)
        assertEquals(3, payload.availableBossMovesets.size)
    }

    @Test
    fun `survives a counter with no moveset breakdown`() {
        val bare = PbDefender(pokemonId = "DITTO")
        val payload = response(bare).toPayload(PokebattlerSort.OVERALL)!!
        val c = payload.counters.single()
        assertNull(c.fastMove)
        assertNull(c.chargedMove)
        assertNull(c.estimator)
    }

    @Test
    fun `returns null when the response carries no attacker block`() {
        assertNull(PokebattlerCountersResponse().toPayload(PokebattlerSort.OVERALL))
    }

    @Test
    fun `formats move names`() {
        assertEquals("Shadow Claw", prettifyMoveName("SHADOW_CLAW_FAST"))
        assertEquals("Iron Head", prettifyMoveName("IRON_HEAD"))
        assertEquals("V-create", prettifyMoveName("V_CREATE"))
        assertEquals("X-Scissor", prettifyMoveName("X_SCISSOR"))
        assertEquals("Power-Up Punch", prettifyMoveName("POWER_UP_PUNCH"))
        assertEquals("Hydro Pump", prettifyMoveName("HYDRO_PUMP_BLASTOISE"))
        assertNull(prettifyMoveName(null))
        assertNull(prettifyMoveName(" "))
    }

    @Test
    fun `formats pokemon names`() {
        assertEquals("Mewtwo", prettifyPokemonName("MEWTWO"))
        assertEquals("Kyurem (Black)", prettifyPokemonName("KYUREM_BLACK_FORM"))
        assertEquals("Shadow Houndoom", prettifyPokemonName("HOUNDOOM_SHADOW_FORM"))
        assertEquals("Mega Charizard X", prettifyPokemonName("CHARIZARD_MEGA_X"))
        assertEquals("Mega Gengar", prettifyPokemonName("GENGAR_MEGA"))
        assertEquals("Primal Groudon", prettifyPokemonName("GROUDON_PRIMAL"))
        assertEquals("Marowak (Alola)", prettifyPokemonName("MAROWAK_ALOLA_FORM"))
    }
}
