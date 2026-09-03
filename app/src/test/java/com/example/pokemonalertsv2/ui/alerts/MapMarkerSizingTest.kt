package com.example.pokemonalertsv2.ui.alerts

import org.junit.Assert.assertEquals
import org.junit.Test

class MapMarkerSizingTest {
    @Test
    fun fullMapMarkersRemainAtExistingSizes() {
        assertEquals(48f, mapAlertMarkerSizeDp(compactPictureInPicture = false, emphasized = false))
        assertEquals(48f, mapAlertMarkerSizeDp(compactPictureInPicture = false, emphasized = true))
        assertEquals(40f, mapClusterMarkerSizeDp(compactPictureInPicture = false))
    }

    @Test
    fun pictureInPictureUsesCompactAndEmphasizedSizes() {
        assertEquals(36f, mapAlertMarkerSizeDp(compactPictureInPicture = true, emphasized = false))
        assertEquals(44f, mapAlertMarkerSizeDp(compactPictureInPicture = true, emphasized = true))
        assertEquals(34f, mapClusterMarkerSizeDp(compactPictureInPicture = true))
    }

    @Test
    fun dynamicZoomScalesAlertMarkerSizes() {
        assertEquals(36f, mapAlertMarkerSizeDp(compactPictureInPicture = false, emphasized = false, zoom = 11.5f))
        assertEquals(44f, mapAlertMarkerSizeDp(compactPictureInPicture = false, emphasized = false, zoom = 14f))
        assertEquals(50f, mapAlertMarkerSizeDp(compactPictureInPicture = false, emphasized = false, zoom = 16.5f))
    }

    @Test
    fun questQuantityExtractionWorksCorrectly() {
        assertEquals("x3", extractQuestQuantity("3 Pinap Berry"))
        assertEquals("x10", extractQuestQuantity("10 Ultra Balls"))
        assertEquals("500", extractQuestQuantity("500 Stardust"))
        assertEquals("1000", extractQuestQuantity("1000 Stardust"))
        assertEquals(null, extractQuestQuantity("Gengar"))
        assertEquals(null, extractQuestQuantity(null))
    }
}
