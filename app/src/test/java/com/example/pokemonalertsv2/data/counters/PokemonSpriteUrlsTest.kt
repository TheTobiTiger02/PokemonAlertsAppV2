package com.example.pokemonalertsv2.data.counters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PokemonSpriteUrlsTest {

    @Test
    fun `builds a base sprite url from the dex number`() {
        val urls = PokemonSpriteUrls.candidates(792, "LUNALA")
        assertEquals(1, urls.size)
        assertTrue(urls.single(), urls.single().endsWith("/pokemon/792.png"))
    }

    @Test
    fun `tries the evolution variant first for a mega`() {
        val urls = PokemonSpriteUrls.candidates(248, "TYRANITAR_MEGA")
        assertTrue(urls[0].endsWith("/pokemon/248_e1.png"))
        assertTrue(urls[1].endsWith("/pokemon/248.png"))
    }

    @Test
    fun `tries the alignment variant first for a shadow`() {
        val urls = PokemonSpriteUrls.candidates(248, "TYRANITAR_SHADOW_FORM")
        assertTrue(urls[0].endsWith("/pokemon/248_a1.png"))
        assertTrue(urls[1].endsWith("/pokemon/248.png"))
    }

    @Test
    fun `always ends with the plain dex sprite`() {
        listOf("LUNALA", "TYRANITAR_MEGA", "TYRANITAR_SHADOW_FORM", "MAROWAK_ALOLA_FORM")
            .forEach { id ->
                val urls = PokemonSpriteUrls.candidates(248, id)
                assertTrue(id, urls.last().endsWith("/pokemon/248.png"))
            }
    }

    @Test
    fun `uses the form sprite when the form id is known`() {
        // Necrozma Dawn Wings used to render as plain Necrozma.
        val urls = PokemonSpriteUrls.candidates(800, "NECROZMA_DAWN_WINGS_FORM", formId = 2719)
        assertTrue(urls[0], urls[0].endsWith("/pokemon/800_f2719.png"))
        assertTrue(urls.last().endsWith("/pokemon/800.png"))
    }

    @Test
    fun `combines form and shadow`() {
        val urls = PokemonSpriteUrls.candidates(487, "GIRATINA_SHADOW_FORM", formId = 90)
        assertTrue(urls[0], urls[0].endsWith("/pokemon/487_f90_a1.png"))
        assertTrue(urls.contains(urls.last()))
        assertTrue(urls.last().endsWith("/pokemon/487.png"))
    }

    @Test
    fun `uses the specific mega variant when known`() {
        // Mega Charizard X is _e2, not _e1 - taking the first evolution would show Mega Y.
        val x = PokemonSpriteUrls.candidates(6, "CHARIZARD_MEGA_X", megaEvoId = 2)
        assertTrue(x[0], x[0].endsWith("/pokemon/6_e2.png"))
        val y = PokemonSpriteUrls.candidates(6, "CHARIZARD_MEGA_Y", megaEvoId = 3)
        assertTrue(y[0], y[0].endsWith("/pokemon/6_e3.png"))
    }

    @Test
    fun `returns nothing without a usable dex number`() {
        assertTrue(PokemonSpriteUrls.candidates(null, "LUNALA").isEmpty())
        assertTrue(PokemonSpriteUrls.candidates(0, "LUNALA").isEmpty())
    }
}
