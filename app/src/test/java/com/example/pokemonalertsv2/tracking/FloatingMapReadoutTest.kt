package com.example.pokemonalertsv2.tracking

import com.example.pokemonalertsv2.data.FLOATING_MAP_MIN_OPACITY
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.clampFloatingMapOpacity
import com.example.pokemonalertsv2.raidwatch.RaidWatchController
import com.example.pokemonalertsv2.raidwatch.WatchedRaid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingMapReadoutTest {

    @Test
    fun `the bubble shows the distance while walking`() {
        assertEquals("120 m", floatingBubbleReadout(true, 120f, inRange = false, waiting = false, paused = false))
    }

    @Test
    fun `in range wins over the distance`() {
        assertEquals("In range", floatingBubbleReadout(true, 12f, inRange = true, waiting = false, paused = false))
    }

    @Test
    fun `no fix yet reads as locating rather than a stale number`() {
        assertEquals("Locating", floatingBubbleReadout(true, null, inRange = false, waiting = false, paused = false))
        assertEquals("Locating", floatingBubbleReadout(true, 300f, inRange = false, waiting = true, paused = false))
    }

    @Test
    fun `without a target the bubble says what the hunt is doing`() {
        assertEquals("Searching", floatingBubbleReadout(false, null, inRange = false, waiting = false, paused = false))
        assertEquals("Paused", floatingBubbleReadout(false, null, inRange = false, waiting = false, paused = true))
    }

    @Test
    fun `opacity never goes so low the window is lost`() {
        assertEquals(FLOATING_MAP_MIN_OPACITY, clampFloatingMapOpacity(0f), 0f)
        assertEquals(1f, clampFloatingMapOpacity(1.4f), 0f)
        assertEquals(0.6f, clampFloatingMapOpacity(0.6f), 0f)
        assertEquals(1f, clampFloatingMapOpacity(Float.NaN), 0f)
    }

    @Test
    fun `a catch only ends the watch of the raid that was caught`() {
        val mewtwo = PokemonAlert(name = "Mewtwo Raid", pokemon = "Mewtwo", type = listOf("Raid"), endTime = "2026-10-05T12:00:00Z")
        val watched = WatchedRaid(alert = mewtwo, startedAtMillis = 0L, endMillis = 1L)
        assertTrue(RaidWatchController.isWatching(watched, mewtwo.uniqueId))
        assertFalse(RaidWatchController.isWatching(watched, "someone-else"))
        assertFalse(RaidWatchController.isWatching(null, mewtwo.uniqueId))
    }
}
