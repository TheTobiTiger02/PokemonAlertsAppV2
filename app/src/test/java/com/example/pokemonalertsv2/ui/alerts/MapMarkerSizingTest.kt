package com.example.pokemonalertsv2.ui.alerts

import org.junit.Assert.assertEquals
import org.junit.Test

class MapMarkerSizingTest {
    @Test
    fun fullMapMarkersRemainAtExistingSizes() {
        assertEquals(68f, mapAlertMarkerSizeDp(compactPictureInPicture = false, emphasized = false))
        assertEquals(68f, mapAlertMarkerSizeDp(compactPictureInPicture = false, emphasized = true))
        assertEquals(48f, mapClusterMarkerSizeDp(compactPictureInPicture = false))
    }

    @Test
    fun pictureInPictureUsesCompactAndEmphasizedSizes() {
        assertEquals(44f, mapAlertMarkerSizeDp(compactPictureInPicture = true, emphasized = false))
        assertEquals(50f, mapAlertMarkerSizeDp(compactPictureInPicture = true, emphasized = true))
        assertEquals(44f, mapClusterMarkerSizeDp(compactPictureInPicture = true))
    }
}
