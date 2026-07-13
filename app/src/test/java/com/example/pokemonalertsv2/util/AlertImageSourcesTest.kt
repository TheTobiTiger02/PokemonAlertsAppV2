package com.example.pokemonalertsv2.util

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertImageSourcesTest {
    @Test
    fun coordinatesSelectMapEvenWithoutPokemonThumbnail() {
        val alert = PokemonAlert(
            name = "Darmstadt alert",
            latitude = 49.88146124315846,
            longitude = 8.64371495798815
        )

        assertEquals(
            listOf(AlertImageSource.MAP_FALLBACK, AlertImageSource.PLACEHOLDER),
            orderedAlertImageSources(alert)
        )
    }

    @Test
    fun invalidCoordinatesContinueToThumbnail() {
        val alert = PokemonAlert(
            name = "Invalid location",
            latitude = 0.0,
            longitude = 0.0,
            thumbnailUrl = "https://example.test/pokemon.png"
        )

        assertEquals(
            listOf(AlertImageSource.THUMBNAIL, AlertImageSource.PLACEHOLDER),
            orderedAlertImageSources(alert)
        )
        assertNull(validAlertCoordinates(alert))
    }

    @Test
    fun locationLabelPrefersExactStreetThenArea() {
        assertEquals(
            "Pallaswiesenstraße Darmstadt",
            fallbackLocationLabel(
                PokemonAlert(
                    name = "Street alert",
                    pokemonLocation = "Pallaswiesenstraße Darmstadt",
                    area = "Darmstadt"
                )
            )
        )
        assertEquals(
            "Darmstadt",
            fallbackLocationLabel(PokemonAlert(name = "Area alert", area = "Darmstadt"))
        )
        assertNull(fallbackLocationLabel(PokemonAlert(name = "Unknown location")))
    }
}
