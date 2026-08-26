package com.example.pokemonalertsv2.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PokemonTypeColorsTest {

    private val allTypes = listOf(
        "NORMAL", "FIRE", "WATER", "ELECTRIC", "GRASS", "ICE", "FIGHTING", "POISON",
        "GROUND", "FLYING", "PSYCHIC", "BUG", "ROCK", "GHOST", "DRAGON", "DARK",
        "STEEL", "FAIRY"
    )

    @Test
    fun `every type has a hue, in both spellings the game master uses`() {
        allTypes.forEach { type ->
            assertNotNull(type, typeColor(type))
            assertNotNull(type, typeColor("POKEMON_TYPE_$type"))
        }
        assertEquals(18, allTypes.size)
    }

    @Test
    fun `hues are distinct so two types never read as the same`() {
        assertEquals(allTypes.size, allTypes.mapNotNull { typeColor(it) }.distinct().size)
    }

    @Test
    fun `an unknown or absent type has no hue rather than a wrong one`() {
        assertNull(typeColor(null))
        assertNull(typeColor(""))
        assertNull(typeColor("   "))
        assertNull(typeColor("POKEMON_TYPE_STELLAR"))
        assertNull(typeLabel("POKEMON_TYPE_STELLAR"))
    }

    @Test
    fun `labels are title cased for display`() {
        assertEquals("Fire", typeLabel("POKEMON_TYPE_FIRE"))
        assertEquals("Fighting", typeLabel("fighting"))
    }
}
