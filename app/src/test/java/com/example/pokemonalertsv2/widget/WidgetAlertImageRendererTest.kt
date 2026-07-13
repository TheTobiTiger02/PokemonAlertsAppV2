package com.example.pokemonalertsv2.widget

import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.util.AlertImageSource
import com.example.pokemonalertsv2.util.orderedAlertImageSources
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetAlertImageRendererTest {
    @Test
    fun imageUrlTakesPriorityOverMapThumbnail() {
        val alert = PokemonAlert(
            name = "Rolycoly",
            imageUrl = "https://example.test/alert.png",
            latitude = 49.74,
            longitude = 8.62,
            thumbnailUrl = "https://example.test/map.png"
        )

        assertEquals(
            listOf(
                AlertImageSource.REMOTE_IMAGE,
                AlertImageSource.MAP_FALLBACK,
                AlertImageSource.THUMBNAIL,
                AlertImageSource.PLACEHOLDER
            ),
            orderedAlertImageSources(alert)
        )
    }

    @Test
    fun mapThumbnailIsUsedWhenAlertImageIsMissing() {
        val alert = PokemonAlert(
            name = "Rolycoly",
            latitude = 49.74,
            longitude = 8.62,
            thumbnailUrl = "https://example.test/map.png"
        )

        assertEquals(
            listOf(
                AlertImageSource.MAP_FALLBACK,
                AlertImageSource.THUMBNAIL,
                AlertImageSource.PLACEHOLDER
            ),
            orderedAlertImageSources(alert)
        )
    }

    @Test
    fun placeholderIsAlwaysAvailableWhenNoRemoteImageCanBeSelected() {
        assertEquals(
            listOf(AlertImageSource.PLACEHOLDER),
            orderedAlertImageSources(PokemonAlert(name = "Rolycoly"))
        )
    }
}
