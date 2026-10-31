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
    fun `the map is padded by the chrome, and less of it in a small window`() {
        assertEquals(
            com.example.pokemonalertsv2.ui.alerts.MapContentInsets(8, 80, 8, 72),
            floatingMapChromeInsets(mapHeightPx = 400, topPx = 80, bottomPx = 72, sidePx = 8)
        )
        // 200 px tall: at most 120 px of padding, split in proportion.
        val small = floatingMapChromeInsets(mapHeightPx = 200, topPx = 100, bottomPx = 100, sidePx = 8)
        assertEquals(60, small.top)
        assertEquals(60, small.bottom)
    }

    @Test
    fun `a bounds fit always leaves room for the bounds`() {
        val insets = com.example.pokemonalertsv2.ui.alerts.MapContentInsets(12, 113, 12, 103)
        // Plenty of room: the chrome plus the margin, untouched.
        assertEquals(listOf(84, 185, 84, 175), com.example.pokemonalertsv2.ui.alerts.fitBoundsPadding(insets, 72, 1000f, 1600f).toList())
        // A 360 px tall window: the vertical padding is cut to three quarters of it, in proportion.
        val small = com.example.pokemonalertsv2.ui.alerts.fitBoundsPadding(insets, 72, 660f, 360f)
        assertTrue(small[1] + small[3] <= 270)
        assertTrue(small[1] > small[3])
    }

    @Test
    fun `a dragged window stays where the system can draw it`() {
        // 1080 wide screen, 600 px window.
        assertEquals(300, keepInside(offset = 300, size = 600, screen = 1080))
        assertEquals(0, keepInside(offset = -550, size = 600, screen = 1080))
        assertEquals(480, keepInside(offset = 1000, size = 600, screen = 1080))
        assertEquals(0, keepInside(offset = 50, size = 1200, screen = 1080))
    }

    @Test
    fun `readouts tick every second only in a target's last minute`() {
        val now = 1_000_000L
        assertEquals(1_000L, finalMinuteTickDelay(listOf(now + 45_000L), now, fallbackMillis = 10_000L))
        assertEquals(5_000L, finalMinuteTickDelay(listOf(now + 65_000L), now, fallbackMillis = 10_000L))
        assertEquals(10_000L, finalMinuteTickDelay(listOf(now + 600_000L), now, fallbackMillis = 10_000L))
        assertEquals(10_000L, finalMinuteTickDelay(listOf(now - 5_000L), now, fallbackMillis = 10_000L))
        assertEquals(10_000L, finalMinuteTickDelay(emptyList(), now, fallbackMillis = 10_000L))
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
