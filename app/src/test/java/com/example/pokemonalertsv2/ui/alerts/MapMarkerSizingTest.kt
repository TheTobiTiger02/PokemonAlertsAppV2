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
}
