package com.example.pokemonalertsv2.data.counters

import com.example.pokemonalertsv2.data.pokegenie.OwnedPokemon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The suggested six.
 *
 * The rule that matters: a mega has to already be evolved to enter a raid, so only the one
 * the trainer says is active may take a slot, and none may when there is no active mega.
 */
class PersonalTeamBuilderTest {

    private fun owned(name: String) = OwnedPokemon(
        displayName = name,
        form = null,
        level = 40.0,
        atkIv = 15,
        defIv = 15,
        staIv = 15,
        cp = 3000,
        quickMove = "Counter",
        chargeMove = "Dynamic Punch",
        shadow = false,
        lucky = false,
        matchKeys = listOf(name)
    )

    private fun counter(pokemonId: String, fast: String = "COUNTER", charged: String = "DYNAMIC_PUNCH") =
        PersonalCounter(
            owned = owned(pokemonId),
            pokemonId = pokemonId,
            displayName = pokemonId,
            fastMove = syntheticFastMove(fast),
            chargedMove = syntheticChargedMove(charged),
            movesetAssumed = false,
            dps = 10.0,
            tdo = 100.0,
            rating = 50.0,
            estimatedAttackers = 6.0
        )

    private fun ids(team: List<PersonalTeamSlot>) = team.map { it.counter.pokemonId }

    private fun PersonalCounter.withCp(cp: Int) = copy(owned = owned.copy(cp = cp))

    @Test
    fun `mega and primal ids are recognised, plain ones are not`() {
        assertTrue("CHARIZARD_MEGA_Y".isMegaOrPrimalId())
        assertTrue("GENGAR_MEGA".isMegaOrPrimalId())
        assertTrue("KYOGRE_PRIMAL".isMegaOrPrimalId())
        assertFalse("CHARIZARD".isMegaOrPrimalId())
        assertFalse("MACHAMP_SHADOW_FORM".isMegaOrPrimalId())
    }

    @Test
    fun `no active mega means no mega takes a slot`() {
        val ranked = listOf(
            counter("CHARIZARD_MEGA_Y"),
            counter("GENGAR_MEGA"),
            counter("MACHAMP"),
            counter("RHYPERIOR")
        )
        assertEquals(listOf("MACHAMP", "RHYPERIOR"), ids(suggestTeam(ranked, activeMegaId = null)))
    }

    @Test
    fun `only the active mega is admitted`() {
        val ranked = listOf(
            counter("CHARIZARD_MEGA_Y"),
            counter("GENGAR_MEGA"),
            counter("MACHAMP")
        )
        val team = suggestTeam(ranked, activeMegaId = "GENGAR_MEGA")
        assertEquals(listOf("GENGAR_MEGA", "MACHAMP"), ids(team))
    }

    @Test
    fun `the active mega is matched case insensitively`() {
        val ranked = listOf(counter("GENGAR_MEGA"), counter("MACHAMP"))
        assertEquals(
            listOf("GENGAR_MEGA", "MACHAMP"),
            ids(suggestTeam(ranked, activeMegaId = "gengar_mega"))
        )
    }

    @Test
    fun `a blank setting is treated as no mega active`() {
        val ranked = listOf(counter("GENGAR_MEGA"), counter("MACHAMP"))
        assertEquals(listOf("MACHAMP"), ids(suggestTeam(ranked, activeMegaId = "   ")))
    }

    @Test
    fun `an active mega the roster cannot field simply never appears`() {
        val ranked = listOf(counter("MACHAMP"), counter("RHYPERIOR"))
        assertEquals(
            listOf("MACHAMP", "RHYPERIOR"),
            ids(suggestTeam(ranked, activeMegaId = "GENGAR_MEGA"))
        )
    }

    @Test
    fun `the active mega takes at most one slot even with several copies`() {
        val ranked = listOf(
            counter("GENGAR_MEGA"),
            counter("GENGAR_MEGA"),
            counter("MACHAMP")
        )
        val team = suggestTeam(ranked, activeMegaId = "GENGAR_MEGA")
        assertEquals(listOf("GENGAR_MEGA", "MACHAMP"), ids(team))
        assertEquals(1, team.first { it.counter.pokemonId == "GENGAR_MEGA" }.count)
    }

    @Test
    fun `duplicate non-megas are kept and grouped with a count`() {
        val ranked = List(4) { counter("MACHAMP") } + counter("RHYPERIOR")
        val team = suggestTeam(ranked, activeMegaId = null)
        assertEquals(listOf("MACHAMP", "RHYPERIOR"), ids(team))
        assertEquals(4, team.first().count)
    }

    @Test
    fun `a slot keeps every copy, not just the representative`() {
        // The Pokemon GO search needs each copy's own CP, so collapsing duplicates to a
        // representative plus a count silently lost four Chandelure down to one.
        val ranked = listOf(
            counter("CHANDELURE").withCp(4499),
            counter("CHANDELURE").withCp(4566),
            counter("CHANDELURE").withCp(3555)
        )
        val slot = suggestTeam(ranked, activeMegaId = null).single()
        assertEquals(3, slot.count)
        assertEquals(listOf(4499, 4566, 3555), slot.copies.map { it.owned.cp })
    }

    @Test
    fun `copies with different movesets are separate slots`() {
        val ranked = listOf(
            counter("MACHAMP", charged = "DYNAMIC_PUNCH"),
            counter("MACHAMP", charged = "CROSS_CHOP")
        )
        val team = suggestTeam(ranked, activeMegaId = null)
        assertEquals(2, team.size)
        assertTrue(team.all { it.count == 1 })
    }

    @Test
    fun `the party never exceeds six`() {
        val ranked = List(20) { index -> counter("MON_$index") }
        assertEquals(6, suggestTeam(ranked, activeMegaId = null).sumOf { it.count })
    }

    @Test
    fun `an empty ranking produces an empty team`() {
        assertEquals(emptyList<PersonalTeamSlot>(), suggestTeam(emptyList(), activeMegaId = null))
    }
}
