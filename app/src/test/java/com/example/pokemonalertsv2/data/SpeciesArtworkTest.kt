package com.example.pokemonalertsv2.data

import com.example.pokemonalertsv2.data.database.PokemonSpeciesEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeciesArtworkTest {
    private fun species(id: Int, name: String) = PokemonSpeciesEntity(id, name, "https://img/$id.png")

    @Test
    fun defaultFormsAreAlsoFiledUnderTheirPlainName() {
        val artwork = speciesArtwork(
            listOf(
                species(492, "Shaymin-land"),
                species(10006, "Shaymin-sky"),
                species(137, "Porygon"),
                species(474, "Porygon-z"),
                species(1, "Bulbasaur")
            )
        )
        assertEquals("https://img/492.png", artwork[normalizeFilterToken("Shaymin")])
        // An alternate form (id above the national dex) keeps only its own name.
        assertEquals("https://img/10006.png", artwork[normalizeFilterToken("Shaymin-sky")])
        // A species that really has the plain name keeps it.
        assertEquals("https://img/137.png", artwork[normalizeFilterToken("Porygon")])
        assertEquals("https://img/474.png", artwork[normalizeFilterToken("Porygon-z")])
        assertEquals("https://img/1.png", artwork[normalizeFilterToken("Bulbasaur")])
    }
}
