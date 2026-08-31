package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPipActionsTest {

    @Test
    fun `follow mode offers browse and zoom, browse mode offers follow and stepping`() {
        val follow = mapPipActionSpecs(MapPipMode.FOLLOW, canStep = true, maxActions = 3)
        assertEquals(
            listOf(MapPipCommand.TOGGLE_MODE, MapPipCommand.PREVIOUS, MapPipCommand.NEXT),
            follow.map { it.command }
        )
        assertEquals(R.string.map_pip_action_browse, follow[0].titleRes)
        assertEquals(R.string.map_pip_action_zoom_out, follow[1].titleRes)
        assertEquals(R.string.map_pip_action_zoom_in, follow[2].titleRes)

        val browse = mapPipActionSpecs(MapPipMode.BROWSE, canStep = true, maxActions = 3)
        assertEquals(R.string.map_pip_action_follow, browse[0].titleRes)
        assertEquals(R.string.map_pip_action_previous_alert, browse[1].titleRes)
        assertEquals(R.string.map_pip_action_next_alert, browse[2].titleRes)
    }

    @Test
    fun `stepping buttons are disabled but kept when there is nothing to browse`() {
        val browse = mapPipActionSpecs(MapPipMode.BROWSE, canStep = false, maxActions = 3)

        assertEquals(3, browse.size)
        assertTrue(browse[0].enabled)
        assertTrue(browse.drop(1).none { it.enabled })
    }

    @Test
    fun `the mode toggle survives a system that only offers one slot`() {
        val specs = mapPipActionSpecs(MapPipMode.FOLLOW, canStep = true, maxActions = 1)

        assertEquals(listOf(MapPipCommand.TOGGLE_MODE), specs.map { it.command })
        assertTrue(mapPipActionSpecs(MapPipMode.FOLLOW, canStep = true, maxActions = 0).isEmpty())
    }

    @Test
    fun `browse order is nearest first and skips alerts without coordinates`() {
        val near = alert("near", 49.7501, 8.6001)
        val far = alert("far", 49.8000, 8.7000)
        val unmappable = alert("unmappable", null, null)

        val ordered = mapPipBrowseOrder(listOf(far, unmappable, near), 49.7500, 8.6000)

        assertEquals(listOf(near.uniqueId, far.uniqueId), ordered.map { it.uniqueId })
    }

    @Test
    fun `stepping wraps at both ends and recovers from a stale selection`() {
        val ids = listOf("a", "b", "c")

        assertEquals("b", stepMapPipSelection(ids, "a", forward = true))
        assertEquals("a", stepMapPipSelection(ids, "c", forward = true))
        assertEquals("c", stepMapPipSelection(ids, "a", forward = false))
        assertEquals("a", stepMapPipSelection(ids, currentId = null, forward = true))
        assertEquals("a", stepMapPipSelection(ids, currentId = "expired", forward = false))
        assertNull(stepMapPipSelection(emptyList(), "a", forward = true))
    }

    @Test
    fun `the browse chip names the alert and how long is left`() {
        val label = mapPipBrowseLabel(
            alert("Larvitar", 49.75, 8.6).copy(pokemon = "Larvitar", cp = 1234),
            nowMillis = 0L
        )

        assertTrue(label, label.startsWith("Larvitar"))
        assertTrue(label, label.contains("CP 1234"))
    }

    @Test
    fun `browse framing spans the user and the alert`() {
        val focus = resolveMapPipFocus(
            userLatitude = 49.7500,
            userLongitude = 8.6200,
            alertLatitude = 49.7381,
            alertLongitude = 8.6031
        )

        val fit = focus as MapPipFocus.Fit
        assertEquals(49.7381, fit.south, 1e-9)
        assertEquals(8.6031, fit.west, 1e-9)
        assertEquals(49.7500, fit.north, 1e-9)
        assertEquals(8.6200, fit.east, 1e-9)
    }

    @Test
    fun `standing on the alert centres instead of fitting bounds`() {
        val focus = resolveMapPipFocus(
            userLatitude = 49.7500,
            userLongitude = 8.6200,
            // Roughly 11 m north: bounds this tight would slam the camera to max zoom.
            alertLatitude = 49.7501,
            alertLongitude = 8.6200
        )

        val centre = focus as MapPipFocus.Centre
        assertEquals(49.75005, centre.latitude, 1e-6)
        assertEquals(8.6200, centre.longitude, 1e-6)
        assertEquals(MAP_PIP_CLOSE_ZOOM, centre.zoom, 0.0)
    }

    private fun alert(name: String, latitude: Double?, longitude: Double?) = PokemonAlert(
        name = name,
        latitude = latitude,
        longitude = longitude
    )
}
