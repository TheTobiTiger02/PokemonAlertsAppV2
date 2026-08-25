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
    fun `returns nothing without a usable dex number`() {
        assertTrue(PokemonSpriteUrls.candidates(null, "LUNALA").isEmpty())
        assertTrue(PokemonSpriteUrls.candidates(0, "LUNALA").isEmpty())
    }
}
