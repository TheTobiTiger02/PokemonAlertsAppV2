package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `a hunt window trades the follow toggle for Got it`() {
        val specs = huntPipActionSpecs(canStep = true, hasTarget = true, maxActions = 3)

        assertEquals(
            listOf(MapPipCommand.PREVIOUS, MapPipCommand.GOT_IT, MapPipCommand.NEXT),
            specs.map { it.command }
        )
        assertTrue(specs.all { it.enabled })
        assertTrue(specs.none { it.command == MapPipCommand.TOGGLE_MODE })
    }

    @Test
    fun `Got it is disabled with nothing targeted, stepping with nothing to step to`() {
        val noTarget = huntPipActionSpecs(canStep = true, hasTarget = false, maxActions = 3)
        assertFalse(noTarget.single { it.command == MapPipCommand.GOT_IT }.enabled)

        val nothingLeft = huntPipActionSpecs(canStep = false, hasTarget = false, maxActions = 3)
        assertEquals(3, nothingLeft.size)
        assertTrue(nothingLeft.none { it.enabled })
    }

    @Test
    fun `a one-slot system keeps stepping rather than the destructive action`() {
        val specs = huntPipActionSpecs(canStep = true, hasTarget = true, maxActions = 1)

        assertEquals(listOf(MapPipCommand.PREVIOUS), specs.map { it.command })
    }

    @Test
    fun `hunting picks the hunt slots and otherwise leaves the map PiP alone`() {
        val hunting = buildMapPipActionSpecs(
            mode = MapPipMode.BROWSE,
            canStep = true,
            maxActions = 3,
            hunting = true,
            hasTarget = true
        )
        assertTrue(hunting.any { it.command == MapPipCommand.GOT_IT })

        val normal = buildMapPipActionSpecs(
            mode = MapPipMode.BROWSE,
            canStep = true,
            maxActions = 3,
            hunting = false,
            hasTarget = true
        )
        assertEquals(
            mapPipActionSpecs(MapPipMode.BROWSE, canStep = true, maxActions = 3),
            normal
        )
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

    // --- Browsing an alert takes it on as the arrival destination ------------------------

    @Test
    fun `browsing an eligible alert makes it the destination`() {
        val browsed = alert("Larvitar", 49.75, 8.62)

        val intent = resolveMapPipTrackingIntent(
            compactPictureInPicture = true,
            pipMode = MapPipMode.BROWSE,
            browsedAlert = browsed,
            activeDestinationId = null,
            lastPipStartedId = null,
            nowMillis = 0L
        )

        assertEquals(MapPipTrackingIntent.Start(browsed), intent)
    }

    @Test
    fun `the alert already being tracked is left alone`() {
        // Rewriting it would restart the service and its notification for no reason.
        val browsed = alert("Larvitar", 49.75, 8.62)

        assertEquals(
            MapPipTrackingIntent.None,
            resolveMapPipTrackingIntent(
                compactPictureInPicture = true,
                pipMode = MapPipMode.BROWSE,
                browsedAlert = browsed,
                activeDestinationId = browsed.uniqueId,
                lastPipStartedId = null,
                nowMillis = 0L
            )
        )
    }

    @Test
    fun `alerts that cannot be walked to are not tracked`() {
        val unmappable = alert("No fix", null, null)
        val expired = PokemonAlert(
            name = "Gone",
            latitude = 49.75,
            longitude = 8.62,
            endTime = "1970-01-01T00:00:01Z"
        )

        for (candidate in listOf(unmappable, expired)) {
            assertEquals(
                candidate.name + " should not become a destination",
                MapPipTrackingIntent.None,
                resolveMapPipTrackingIntent(
                    compactPictureInPicture = true,
                    pipMode = MapPipMode.BROWSE,
                    browsedAlert = candidate,
                    activeDestinationId = null,
                    lastPipStartedId = null,
                    nowMillis = 2_000L
                )
            )
        }
    }

    @Test
    fun `arriving does not re-arm the alert just arrived at`() {
        // Arrival clears the destination while the cursor is still parked on that alert. Re-arming
        // would loop, and for a raid it would raise the arrival service back over the Raid Watch
        // handoff that just replaced it.
        val browsed = alert("Larvitar", 49.75, 8.62)

        assertEquals(
            MapPipTrackingIntent.None,
            resolveMapPipTrackingIntent(
                compactPictureInPicture = true,
                pipMode = MapPipMode.BROWSE,
                browsedAlert = browsed,
                activeDestinationId = null,
                lastPipStartedId = browsed.uniqueId,
                nowMillis = 0L
            )
        )
    }

    @Test
    fun `following the user again ends the journey the window started`() {
        val browsed = alert("Larvitar", 49.75, 8.62)

        assertEquals(
            MapPipTrackingIntent.Stop,
            resolveMapPipTrackingIntent(
                compactPictureInPicture = true,
                pipMode = MapPipMode.FOLLOW,
                browsedAlert = null,
                activeDestinationId = browsed.uniqueId,
                lastPipStartedId = browsed.uniqueId,
                nowMillis = 0L
            )
        )
    }

    @Test
    fun `following the user leaves a journey started elsewhere running`() {
        // Set from a card or the detail screen: the window never started it, so it may not end it.
        val elsewhere = alert("Larvitar", 49.75, 8.62)

        assertEquals(
            MapPipTrackingIntent.None,
            resolveMapPipTrackingIntent(
                compactPictureInPicture = true,
                pipMode = MapPipMode.FOLLOW,
                browsedAlert = null,
                activeDestinationId = elsewhere.uniqueId,
                lastPipStartedId = null,
                nowMillis = 0L
            )
        )
    }

    @Test
    fun `leaving the window keeps the journey running`() {
        val browsed = alert("Larvitar", 49.75, 8.62)

        assertEquals(
            MapPipTrackingIntent.None,
            resolveMapPipTrackingIntent(
                compactPictureInPicture = false,
                pipMode = MapPipMode.FOLLOW,
                browsedAlert = browsed,
                activeDestinationId = browsed.uniqueId,
                lastPipStartedId = browsed.uniqueId,
                nowMillis = 0L
            )
        )
    }

    @Test
    fun `a refreshed copy of the same alert is not a new journey`() {
        // The feed rebuilds alerts on every poll, restamping the fields that move.
        val browsed = alert("Larvitar", 49.75, 8.62)
        val refreshed = browsed.copy(currentWeather = "Partly cloudy")

        assertTrue(
            MapPipTrackingIntent.Start(browsed)
                .sameTargetAs(MapPipTrackingIntent.Start(refreshed))
        )
        assertFalse(
            MapPipTrackingIntent.Start(browsed)
                .sameTargetAs(MapPipTrackingIntent.Start(alert("Other", 49.76, 8.63)))
        )
        assertFalse(MapPipTrackingIntent.Stop.sameTargetAs(MapPipTrackingIntent.None))
    }

    // --- The label chip yields to the notification ----------------------------------------

    private fun alert(name: String, latitude: Double?, longitude: Double?) = PokemonAlert(
        name = name,
        latitude = latitude,
        longitude = longitude
    )
}
