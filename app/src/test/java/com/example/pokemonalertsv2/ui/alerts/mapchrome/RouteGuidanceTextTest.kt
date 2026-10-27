package com.example.pokemonalertsv2.ui.alerts.mapchrome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteGuidanceTextTest {
    @Test
    fun huntDetailSplitsOnBullets() {
        val detail = "⛅ Partly cloudy • ~171 m • ~3 min walk • 11h 30m left"
        assertEquals("~171 m", guidanceDistance(detail))
        assertEquals("⛅ Partly cloudy · ~3 min walk · 11h 30m left", guidanceRest(detail))
    }

    @Test
    fun catchDetailSplitsOnDots() {
        assertEquals("1.2 km", guidanceDistance("1.2 km · due 06:14 · open now"))
        assertEquals("due 06:14 · open now", guidanceRest("1.2 km · due 06:14 · open now"))
    }

    @Test
    fun noDistanceLeavesTheLineWhole() {
        assertNull(guidanceDistance("Waiting for a match"))
        assertEquals("Waiting for a match", guidanceRest("Waiting for a match"))
    }
}
