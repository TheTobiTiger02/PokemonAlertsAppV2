package com.example.pokemonalertsv2.data.pokegenie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeciesCatalogueTest {

    private val catalogue = SpeciesCatalogue(
        listOf(
            "ZACIAN", "ZACIAN_HERO_FORM", "ZACIAN_CROWNED_SWORD_FORM", "ZACIAN_GIGANTAMAX",
            "AEGISLASH", "AEGISLASH_SHIELD_FORM", "AEGISLASH_BLADE_FORM",
            "DARMANITAN", "DARMANITAN_STANDARD_FORM", "DARMANITAN_ZEN_FORM",
            "DARMANITAN_GALARIAN_STANDARD_FORM", "DARMANITAN_GALARIAN_ZEN_FORM",
            "ORICORIO", "ORICORIO_POMPOM_FORM",
            "PIKACHU", "PIKACHU_FLYING_01_FORM",
            "MACHAMP"
        )
    )

    private fun resolve(vararg candidates: String) = catalogue.resolve(candidates.toList())

    @Test
    fun `an exact id wins`() {
        assertEquals("AEGISLASH_SHIELD_FORM", resolve("AEGISLASH_SHIELD_FORM", "AEGISLASH"))
        assertEquals("MACHAMP", resolve("MACHAMP"))
    }

    @Test
    fun `underscores do not have to agree`() {
        assertEquals("ORICORIO_POMPOM_FORM", resolve("ORICORIO_POM_POM_FORM", "ORICORIO"))
    }

    @Test
    fun `a prefix reaches a fuller catalogue spelling`() {
        assertEquals(
            "DARMANITAN_GALARIAN_STANDARD_FORM",
            resolve("DARMANITAN_GALARIAN_FORM", "DARMANITAN_GALARIAN", "DARMANITAN")
        )
    }

    @Test
    fun `a word subset reaches a form the guess only half names`() {
        // The case this class exists for: Poke Genie writes "Sword", Pokebattler writes
        // CROWNED_SWORD, and no prefix of one is a prefix of the other.
        assertEquals(
            "ZACIAN_CROWNED_SWORD_FORM",
            resolve("ZACIAN_SWORD_FORM", "ZACIAN_SWORD", "ZACIAN")
        )
    }

    @Test
    fun `a more specific candidate beats an exact hit further down the list`() {
        // ZACIAN is real and last, so a naive "first exact match" would pick it.
        assertEquals("ZACIAN_CROWNED_SWORD_FORM", resolve("ZACIAN_SWORD", "ZACIAN"))
    }

    @Test
    fun `sibling forms break the tie the same way boss resolution does`() {
        // Both Galarian ids contain GALARIAN; _STANDARD is the default form.
        assertEquals("DARMANITAN_STANDARD_FORM", resolve("DARMANITAN_STANDARD_FORM"))
        assertEquals("DARMANITAN_GALARIAN_STANDARD_FORM", resolve("DARMANITAN_GALARIAN"))
    }

    @Test
    fun `an unknown form falls through to the species`() {
        assertEquals("PIKACHU", resolve("PIKACHU_KARIYUSHI_FORM", "PIKACHU_KARIYUSHI", "PIKACHU"))
    }

    @Test
    fun `a name in no catalogue at all is a miss`() {
        assertNull(resolve("MISSINGNO_FORM", "MISSINGNO"))
    }

    @Test
    fun `an empty catalogue has no opinion`() {
        val empty = SpeciesCatalogue(emptyList())
        assertNull(empty.resolve(listOf("ZACIAN_SWORD", "ZACIAN")))
    }
}
