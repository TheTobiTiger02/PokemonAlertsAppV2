package com.example.pokemonalertsv2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeciesArtTest {
    private fun sprite(id: Int) = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$id.png"

    // Keys as speciesArtwork() files PokeAPI names.
    private val artwork = mapOf(
        "arcanine" to sprite(59),
        "arcanine hisui" to sprite(10230),
        "charizard" to sprite(6),
        "charizard mega x" to sprite(10034),
        "kirlia" to sprite(281),
        "dragonite" to sprite(149),
        "kyogre" to sprite(382),
        "kyogre primal" to sprite(10077),
        "slowpoke" to sprite(79),
        "slowpoke galar" to sprite(10164)
    )

    @Test
    fun regionalFormsResolveFromEitherWordOrder() {
        listOf("Arcanine Hisuian", "Hisuian Arcanine").forEach { name ->
            val art = speciesArt(name, artwork)
            assertEquals(sprite(10230), art.url)
            assertEquals(sprite(59), art.fallbackUrl)
            assertEquals(59, art.dex)
            assertEquals("Hisuian", art.form)
        }
        assertEquals(sprite(10164), speciesArt("Slowpoke Galarian", artwork).url)
    }

    @Test
    fun megaAndPrimalFormsUseTheirOwnSprite() {
        val charizard = speciesArt("Mega Charizard X", artwork)
        assertEquals(sprite(10034), charizard.url)
        assertEquals(6, charizard.dex)
        assertEquals("Mega X", charizard.form)
        assertEquals(sprite(10077), speciesArt("Primal Kyogre", artwork).url)
    }

    @Test
    fun missingFormsFallBackToTheBaseSpecies() {
        val dragonite = speciesArt("Mega Dragonite", artwork)
        assertEquals(sprite(149), dragonite.url)
        assertNull(dragonite.fallbackUrl)
        assertEquals("Mega", dragonite.form)

        val kirlia = speciesArt("Shadow Kirlia", artwork)
        assertEquals(sprite(281), kirlia.url)
        assertEquals(281, kirlia.dex)
        assertEquals("Shadow", kirlia.form)
    }

    @Test
    fun plainSpeciesHaveNoBadge() {
        val art = speciesArt("Charizard", artwork)
        assertEquals(sprite(6), art.url)
        assertNull(art.form)
        assertEquals(6, art.dex)
    }
}
