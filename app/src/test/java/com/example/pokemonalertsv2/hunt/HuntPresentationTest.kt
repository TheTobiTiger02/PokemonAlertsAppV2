package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.ui.alerts.AlertMapCoordinates
import org.junit.Assert.*
import org.junit.Test

class HuntPresentationTest {
    @Test fun focusCyclesAndExplicitSelectionResetsIt() {
        var focus = HuntMapFocus.READY
        focus = focus.pressed()
        assertEquals(HuntMapFocus.TARGET, focus)
        focus = focus.pressed()
        assertEquals(HuntMapFocus.ROUTE, focus)
        assertEquals(HuntMapFocus.TARGET, focus.pressed())
        assertEquals(HuntMapFocus.TARGET, HuntMapFocus.READY.pressed())
    }

    @Test fun routeFitsOnlyNumberedHeadAndCurrentLocationWithoutReranking() {
        val targets = (1..12).map { PokemonAlert(name = "Target $it", id = it, latitude = 49.0 + it / 100.0, longitude = 8.0) }
        val points = huntRouteFocusCoordinates(targets, 50.0, 9.0)
        assertEquals(10, points.size)
        assertEquals(AlertMapCoordinates(49.01, 8.0), points.first())
        assertEquals(AlertMapCoordinates(50.0, 9.0), points.last())
        assertFalse(points.contains(AlertMapCoordinates(49.12, 8.0)))
        assertEquals(9, huntRouteFocusCoordinates(targets, null, null).size)
    }

    @Test fun emptySingleAndCoincidentPointsAreSafe() {
        assertTrue(huntRouteFocusCoordinates(emptyList(), null, null).isEmpty())
        assertEquals(1, huntRouteFocusCoordinates(emptyList(), 49.0, 8.0).size)
        val target = PokemonAlert(name = "Target", latitude = 49.0, longitude = 8.0)
        assertEquals(1, huntRouteFocusCoordinates(listOf(target, target), 49.0, 8.0).size)
        assertEquals(1, huntRouteFocusCoordinates(listOf(target), Double.NaN, 8.0).size)
    }

    @Test fun upsideDownRequiresStableTiltAndRestoresWithoutFlicker() {
        val state = HuntUpsideDownState()
        assertFalse(state.update(9.8f, 0))
        assertFalse(state.update(-9.8f, 100))
        assertFalse(state.update(-9.8f, 599))
        assertTrue(state.update(-9.8f, 600))
        assertTrue(state.update(-5f, 700))
        assertFalse(state.update(0f, 800))
        assertFalse(state.update(-9.8f, 900))
        state.reset()
        assertFalse(state.update(-9.8f, 1500))
        assertFalse(state.update(Float.NaN, 2000))
    }

    @Test fun briefTiltAndFaceDownDoNotBlackOut() {
        val state = HuntUpsideDownState()
        assertFalse(state.update(-9.8f, 0))
        assertFalse(state.update(0f, 300))
        assertFalse(state.update(-9.8f, 600))
        assertFalse(state.update(-6f, 800))
        assertFalse(state.update(-9.8f, 1200))
        assertFalse(state.update(-9.8f, 1600))
        assertFalse(state.update(0f, 1700))
    }
}
