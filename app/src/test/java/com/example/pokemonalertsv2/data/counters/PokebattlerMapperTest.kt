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
        assertEquals("Confusion", payload.bossMove1)
        assertEquals("Focus Blast", payload.bossMove2)
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
    fun `ranks are one based and follow api order`() {
        val second = defender.copy(pokemonId = "ETERNATUS")
        val payload = response(defender, second).toPayload(PokebattlerSort.OVERALL)!!
        assertEquals(listOf(1, 2), payload.counters.map { it.rank })
        assertEquals(listOf("KYUREM_BLACK_FORM", "ETERNATUS"), payload.counters.map { it.pokemonId })
    }

    @Test
    fun `best moveset depends on the chosen sort`() {
        fun pick(sort: PokebattlerSort) = selectBestByMove(defender, sort)?.move1
        // Higher is better for rating, power and damage.
        assertEquals("DRAGON_BREATH_FAST", pick(PokebattlerSort.OVERALL))
        assertEquals("SHADOW_CLAW_FAST", pick(PokebattlerSort.POWER))
        assertEquals("BITE_FAST", pick(PokebattlerSort.TDO))
        // Lower is better for the estimator: fewer trainers needed to win.
        assertEquals("IRON_TAIL_FAST", pick(PokebattlerSort.ESTIMATOR))
        assertEquals("IRON_TAIL_FAST", pick(PokebattlerSort.TIME))
    }

    @Test
    fun `the persisted moveset is the one the chosen sort headlines`() {
        val overall = response(defender).toPayload(PokebattlerSort.OVERALL)!!.counters.single()
        assertEquals("Dragon Breath", overall.fastMove)
        assertEquals("Outrage", overall.chargedMove)
        val tdo = response(defender).toPayload(PokebattlerSort.TDO)!!.counters.single()
        assertEquals("Bite", tdo.fastMove)
        assertEquals("Surf", tdo.chargedMove)
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
