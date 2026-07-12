package com.example.pokemonalertsv2.widget

import com.example.pokemonalertsv2.data.PokemonAlert
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

        assertEquals(WidgetAlertImageSource.REMOTE_IMAGE, widgetAlertImageSource(alert))
    }

    @Test
    fun mapThumbnailIsUsedWhenAlertImageIsMissing() {
        val alert = PokemonAlert(
            name = "Rolycoly",
            latitude = 49.74,
            longitude = 8.62,
            thumbnailUrl = "https://example.test/map.png"
        )

        assertEquals(WidgetAlertImageSource.MAP_THUMBNAIL, widgetAlertImageSource(alert))
    }

    @Test
    fun placeholderIsAlwaysAvailableWhenNoRemoteImageCanBeSelected() {
        assertEquals(
            WidgetAlertImageSource.PLACEHOLDER,
            widgetAlertImageSource(PokemonAlert(name = "Rolycoly"))
        )
    }
}
