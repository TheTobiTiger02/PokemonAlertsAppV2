package com.example.pokemonalertsv2.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPolishTest {

    @Test
    fun `an ended raid says so and where the walk goes next`() {
        assertEquals("Mewtwo raid ended · next: Eevee", endedTargetNote("Mewtwo", isRaid = true, nextName = "Eevee"))
        assertEquals("Pikachu despawned", endedTargetNote("Pikachu", isRaid = false, nextName = null))
        assertEquals("Pikachu despawned", endedTargetNote("Pikachu", isRaid = false, nextName = " "))
    }

    @Test
    fun `the bubble snaps to the nearer edge`() {
        // 1280 wide screen, 200 px bubble, 20 px margin.
        assertEquals(20, snapToEdgeX(x = 300, width = 200, screenWidth = 1280, margin = 20))
        assertEquals(1060, snapToEdgeX(x = 700, width = 200, screenWidth = 1280, margin = 20))
        assertEquals(1060, snapToEdgeX(x = 1500, width = 200, screenWidth = 1280, margin = 20))
        assertEquals(20, snapToEdgeX(x = -100, width = 200, screenWidth = 1280, margin = 20))
    }

    @Test
    fun `the window follows the app theme, and the system only in system mode`() {
        assertFalse(overlayIsDark(storedThemeMode = 0, systemDark = false))
        assertTrue(overlayIsDark(storedThemeMode = 0, systemDark = true))
        assertFalse(overlayIsDark(storedThemeMode = 1, systemDark = true))
        assertTrue(overlayIsDark(storedThemeMode = 2, systemDark = false))
        assertEquals(OverlayColors.Dark, OverlayColors.forDark(true))
        assertEquals(OverlayColors.Light, OverlayColors.forDark(false))
    }
}
