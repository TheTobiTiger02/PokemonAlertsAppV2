package com.example.pokemonalertsv2.ui.alerts

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteLineStyleTest {
    @Test
    fun classicKeepsTheShippedLine() {
        val classic = RouteLineStyle.CLASSIC
        assertEquals(RouteStroke(RouteLineStyle.COLOR, 6f, casingWidth = 10f), classic.next)
        assertEquals(RouteStroke(RouteLineStyle.COLOR, 4f, opacity = 0.45f, casingWidth = 7f, casingOpacity = 0.6f), classic.later)
        assertEquals(RouteStroke(RouteLineStyle.WALKED_COLOR, 3.5f, opacity = 0.7f), classic.walked)
        assertTrue(classic.chevrons)
    }

    @Test
    fun walkingAndFloatingHuntLinesStandApart() {
        assertNotEquals(RouteLineStyle.CLASSIC, RouteLineStyle.NAVIGATION)
        assertEquals(RoutePattern.DOTTED, RouteLineStyle.FOOTSTEPS.next.pattern)
        assertEquals(RoutePattern.DASHED, RouteLineStyle.NAVIGATION.later.pattern)
    }

    @Test
    fun casingDotsSitUnderTheLineDots() {
        assertNull(routeDashArray(RoutePattern.SOLID, 6f))
        val line = routeDashArray(RoutePattern.DOTTED, 7f)!!
        val casing = routeDashArray(RoutePattern.DOTTED, 10f, lineWidth = 7f)!!
        // Dash arrays are in each layer's own width, so the gaps match on screen.
        assertEquals(line[1] * 7f, casing[1] * 10f, 1e-4f)
        assertArrayEquals(arrayOf(0f, RouteLineStyle.DOT_GAP), line)
    }
}
