package com.example.pokemonalertsv2.data.pokegenie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MegaBaseExpanderTest {

    private fun mega(
        name: String = "Charizard",
        form: String = "Mega Y",
        level: Double? = 52.0,
        cp: Int? = 5000,
        iv: Int = 15
    ) = PokeGenieRow(
        name = name,
        form = form,
        cp = cp,
        hp = 200,
        atkIv = iv,
        defIv = iv,
        staIv = iv,
        level = level,
        levelMin = level,
        levelMax = level,
        quickMove = "Fire Spin",
        chargeMove = "Blast Burn"
    )

    private fun synthesized(rows: List<PokeGenieRow>) =
        rows.singleOrNull { PokeGenieMatcher.matchKeysFor(it).first() == "CHARIZARD" }

    @Test
    fun `a boosted mega also imports its base form at the capped level`() {
        // Mega Level 4 adds +2 levels, so the exported 52 is really a level 50 base.
        val result = MegaBaseExpander.expand(listOf(mega(level = 52.0)))

        assertEquals(1, result.synthesizedBaseCount)
        val base = synthesized(result.rows)!!
        assertEquals(50.0, base.level!!, 1e-9)
        assertEquals(50.0, base.levelMin!!, 1e-9)
        assertEquals(50.0, base.levelMax!!, 1e-9)
        assertNull(base.form)
        assertEquals("Charizard", base.name)
    }

    @Test
    fun `the mega's identity is not copied onto the base row`() {
        val base = synthesized(MegaBaseExpander.expand(listOf(mega())).rows)!!

        assertNull(base.scanIndex)
        assertNull(base.cp)
        assertNull(base.hp)
        // IVs and moves are the same Pokémon's, so they carry over.
        assertEquals(15, base.atkIv)
        assertEquals("Fire Spin", base.quickMove)
        assertEquals("Blast Burn", base.chargeMove)
    }

    @Test
    fun `an unboosted mega level passes through unchanged`() {
        val base = synthesized(MegaBaseExpander.expand(listOf(mega(level = 40.0))).rows)!!
        assertEquals(40.0, base.level!!, 1e-9)
    }

    @Test
    fun `a mega without a known level still yields a base row`() {
        val result = MegaBaseExpander.expand(listOf(mega(level = null)))
        assertEquals(1, result.synthesizedBaseCount)
        assertNull(synthesized(result.rows)!!.level)
    }

    @Test
    fun `both mega variants yield a single base row`() {
        val result = MegaBaseExpander.expand(
            listOf(mega(form = "Mega X", level = 51.5), mega(form = "Mega Y", level = 52.0))
        )

        assertEquals(1, result.synthesizedBaseCount)
        assertEquals(50.0, synthesized(result.rows)!!.level!!, 1e-9)
    }

    @Test
    fun `a base form already in the export is not duplicated`() {
        // Same level and IVs as the mega's derived base, so the real scan wins on CP.
        val result = MegaBaseExpander.expand(
            listOf(
                mega(),
                PokeGenieRow(
                    name = "Charizard",
                    level = 50.0,
                    atkIv = 15,
                    defIv = 15,
                    staIv = 15,
                    cp = 2800
                )
            )
        )

        assertEquals(0, result.synthesizedBaseCount)
        assertEquals(2, result.rows.size)
    }

    @Test
    fun `a weaker scanned base does not suppress the mega's base form`() {
        // The real case this was written for: a level 50 Mega Pinsir and a level 28 plain
        // Pinsir. Suppressing the synthesized level 50 base left the roster with only a copy
        // the counter ranking then discarded for being under level 40 — so no Pinsir at all.
        val result = MegaBaseExpander.expand(
            listOf(mega(), PokeGenieRow(name = "Charizard", level = 28.0, cp = 1200))
        )

        assertEquals(1, result.synthesizedBaseCount)
        assertEquals(3, result.rows.size)
        val bases = result.rows.filter { PokeGenieMatcher.matchKeysFor(it).first() == "CHARIZARD" }
        assertEquals(listOf(28.0, 50.0), bases.map { it.level }.sortedBy { it })
    }

    @Test
    fun `a better scanned base wins on IVs at the same level`() {
        val result = MegaBaseExpander.expand(
            listOf(
                mega(level = 50.0, iv = 10),
                PokeGenieRow(
                    name = "Charizard",
                    level = 50.0,
                    atkIv = 15,
                    defIv = 15,
                    staIv = 15,
                    cp = 3200
                )
            )
        )

        assertEquals(0, result.synthesizedBaseCount)
        assertEquals(2, result.rows.size)
    }

    @Test
    fun `a shadow base does not stand in for the mega's base form`() {
        // Shadow and non-shadow are kept strictly separate by the matching, and a shadow
        // Pokémon can never mega evolve, so the mega's base must be its own row.
        val result = MegaBaseExpander.expand(
            listOf(mega(), PokeGenieRow(name = "Charizard", level = 45.0, shadowState = ShadowState.SHADOW))
        )

        assertEquals(1, result.synthesizedBaseCount)
        assertEquals(3, result.rows.size)
    }

    @Test
    fun `a primal gets the same treatment`() {
        val result = MegaBaseExpander.expand(
            listOf(PokeGenieRow(name = "Kyogre", form = "Primal", level = 52.0))
        )

        assertEquals(1, result.synthesizedBaseCount)
        val base = result.rows.last()
        assertEquals("Kyogre", base.name)
        assertEquals(50.0, base.level!!, 1e-9)
        assertTrue(PokeGenieMatcher.matchKeysFor(base).contains("KYOGRE"))
    }

    @Test
    fun `a mega written into the name column is handled too`() {
        val result = MegaBaseExpander.expand(listOf(mega(name = "Mega Charizard X", form = "")))

        assertEquals(1, result.synthesizedBaseCount)
        val base = synthesized(result.rows)!!
        assertEquals("Charizard", base.name)
        assertTrue(PokeGenieMatcher.matchKeysFor(base).contains("CHARIZARD"))
    }

    @Test
    fun `plain rows are untouched`() {
        val plain = PokeGenieRow(name = "Machamp", level = 42.0, cp = 2500)
        val result = MegaBaseExpander.expand(listOf(plain))

        assertEquals(0, result.synthesizedBaseCount)
        assertEquals(listOf(plain), result.rows)
    }

    @Test
    fun `the base row lands right after its mega`() {
        val result = MegaBaseExpander.expand(
            listOf(PokeGenieRow(name = "Machamp"), mega())
        )

        assertEquals("Charizard", result.rows[2].name)
        assertEquals("Charizard", result.rows[1].name)
        assertEquals("Machamp", result.rows[0].name)
    }
}
