package com.example.pokemonalertsv2.ui.settings

import com.example.pokemonalertsv2.data.pokegenie.OwnedPokemon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The labels and the search predicate behind the imported-roster screen. */
class RosterEntryTest {

    private fun entry(
        name: String = "Pinsir",
        form: String? = null,
        level: Double? = 50.0,
        cp: Int? = 4728,
        iv: Int? = 15,
        quick: String? = "Fury Cutter",
        charged: String? = "X-Scissor",
        shadow: Boolean = false,
        lucky: Boolean = false
    ) = RosterEntry(
        key = "$name-0",
        owned = OwnedPokemon(
            displayName = name,
            form = form,
            level = level,
            atkIv = iv,
            defIv = iv,
            staIv = iv,
            cp = cp,
            quickMove = quick,
            chargeMove = charged,
            shadow = shadow,
            lucky = lucky,
            matchKeys = listOf(name.uppercase())
        ),
        pokemonId = name.uppercase()
    )

    @Test
    fun `a row without CP is the base form synthesized from a mega`() {
        // MegaBaseExpander cannot derive the base form's CP, so it leaves it out; every real
        // Poke Genie scan has one.
        assertTrue(entry(cp = null).synthesized)
        assertFalse(entry().synthesized)
    }

    @Test
    fun `copies under the ranking floor are marked`() {
        assertTrue(entry(level = 28.0).belowRankingLevel)
        assertFalse(entry(level = 40.0).belowRankingLevel)
    }

    @Test
    fun `the title carries shadow, form and lucky`() {
        assertEquals("Pinsir", entry().title)
        assertEquals("Pinsir (Mega)", entry(form = "Mega").title)
        assertEquals("Shadow Pinsir", entry(shadow = true).title)
        assertEquals("Pinsir ✨", entry(lucky = true).title)
        // Poke Genie writes "Normal" for a plain Pokemon; that is not a form worth showing.
        assertEquals("Pinsir", entry(form = "Normal").title)
    }

    @Test
    fun `the stat line reads level, IVs, IV percent and CP`() {
        assertEquals("L50 · 15/15/15 (100%) · CP 4728", entry().statLine)
        assertEquals("L50 · 15/15/15 (100%)", entry(cp = null).statLine)
        assertEquals("L50", entry(cp = null, iv = null).statLine)
        assertEquals("No stats recorded", entry(level = null, cp = null, iv = null).statLine)
    }

    @Test
    fun `search matches the name, the form and either move`() {
        val mega = entry(form = "Mega")

        assertTrue(mega.haystack.contains("pinsir"))
        assertTrue(mega.haystack.contains("mega"))
        assertTrue(mega.haystack.contains("x-scissor"))
        assertTrue(mega.haystack.contains("fury cutter"))
        assertFalse(mega.haystack.contains("kartana"))
    }

    @Test
    fun `both charged moves are listed`() {
        val entry = RosterEntry(
            key = "k",
            owned = OwnedPokemon(
                displayName = "Kartana",
                form = null,
                level = 50.0,
                atkIv = 15,
                defIv = 15,
                staIv = 15,
                cp = 4156,
                quickMove = "Razor Leaf",
                chargeMove = "Leaf Blade",
                chargeMove2 = "X-Scissor",
                shadow = false,
                lucky = false,
                matchKeys = listOf("KARTANA")
            ),
            pokemonId = "KARTANA"
        )

        assertEquals(listOf("Razor Leaf", "Leaf Blade", "X-Scissor"), entry.moves)
    }
}
