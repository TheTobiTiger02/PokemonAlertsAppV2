package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MapClusteringPerformanceTest {

    /**
     * The reported bug: a 350 marker limit engaged with well under 350 markers on screen.
     *
     * Markers are prepared for a box padded beyond the viewport so a pan has something ready,
     * and the budget used to be counted over that padded set. Off-screen markers therefore
     * spent the on-screen budget, and on a phone the padded box held several times a screenful.
     */
    @Test
    fun theMarkerLimitCountsWhatIsOnScreenNotWhatIsPreparedAroundIt() {
        val centreLatitude = 49.87
        val centreLongitude = 8.65
        val zoom = 16.0
        val screen = mapViewportBounds(
            centreLatitude, centreLongitude, zoom,
            viewportWidthDp = 360f, viewportHeightDp = 800f, marginFactor = 0.0
        )!!
        val prepared = mapViewportBounds(
            centreLatitude, centreLongitude, zoom,
            viewportWidthDp = 360f, viewportHeightDp = 800f
        )!!
        val config = com.example.pokemonalertsv2.data.MapClusteringConfig(
            grouping = com.example.pokemonalertsv2.data.MapGrouping.CURRENT,
            zoomCutoff = 12,
            overviewLimit = 350,
            closeLimit = 350
        )

        // 200 on screen, comfortably under the limit, at distinct coordinates.
        val onScreen = List(200) { index ->
            PokemonAlert(
                id = index + 1, name = "on $index", type = listOf("Spawn"),
                latitude = centreLatitude + (index % 20 - 10) * (screen.north - screen.south) / 40.0,
                longitude = centreLongitude + (index / 20 - 5) * (screen.east - screen.west) / 40.0
            )
        }
        // 300 more in the prepared ring, outside the screen but inside what gets rendered.
        val offScreen = List(300) { index ->
            PokemonAlert(
                id = 10_000 + index, name = "off $index", type = listOf("Spawn"),
                latitude = screen.north + (prepared.north - screen.north) * (0.1 + 0.8 * (index % 30) / 30.0),
                longitude = centreLongitude + (index / 30 - 5) * (screen.east - screen.west) / 40.0
            )
        }
        val alerts = onScreen + offScreen
        assertTrue(onScreen.all { screen.contains(it.latitude!!, it.longitude!!) })
        assertFalse(offScreen.any { screen.contains(it.latitude!!, it.longitude!!) })

        val items = clusterMapAlerts(alerts, zoom, config = config, budgetBounds = screen)

        // 500 prepared against a 350 limit, but only 200 of them are visible: nothing clusters.
        assertTrue(items.none { it is MapMarkerItem.Cluster })
        assertFalse(items.any { it is MapMarkerItem.Cluster && it.markerLimitActive })

        // Counting the prepared set instead - the old behaviour - does force clustering.
        val countingEverything = clusterMapAlerts(alerts, zoom, config = config)
        assertTrue(countingEverything.any { it is MapMarkerItem.Cluster })
    }

    /** A cluster that stays put keeps its id, so the map swaps its icon instead of replacing it. */
    @Test
    fun clusterIdsSurviveAMembershipChange() {
        val alerts = List(40) { index ->
            PokemonAlert(
                id = index + 1, name = "alert $index", type = listOf("Spawn"),
                latitude = 49.87, longitude = 8.65
            )
        }
        val before = clusterMapAlerts(alerts, 16.0).filterIsInstance<MapMarkerItem.Cluster>()
        val after = clusterMapAlerts(alerts.dropLast(1), 16.0).filterIsInstance<MapMarkerItem.Cluster>()

        assertEquals(1, before.size)
        assertEquals(1, after.size)
        assertEquals(before.single().id, after.single().id)
        assertEquals(40, before.single().alerts.size)
        assertEquals(39, after.single().alerts.size)
    }

    /** Small pans reuse the prepared anchor; leaving the safe area re-anchors. */
    @Test
    fun theClusteringAnchorSurvivesSmallPansOnly() {
        val anchor = MapCameraSnapshot(49.87, 8.65, 16.0)
        val width = 360f
        val height = 800f
        fun retained(latitude: Double, longitude: Double, zoom: Double = 16.0) =
            retainedMapAnchor(anchor, latitude, longitude, zoom, width, height)

        assertEquals(anchor, retained(49.87, 8.65))
        assertEquals(anchor, retained(49.8702, 8.6502))
        // A quarter of a viewport is still within what was prepared; a whole one is not.
        assertNotEquals(anchor, retained(49.87 + 0.02, 8.65))
        // A zoom change always re-anchors: the whole projection moved.
        assertNotEquals(anchor, retained(49.87, 8.65, zoom = 16.5))
    }

    @Test
    fun denseGridsRespectBudgetAndPreserveEveryMemberAcrossZoomsAndRadii() {
        for (count in listOf(999, 3_000, 10_000)) {
            val alerts = List(count) { index ->
                PokemonAlert(
                    id = index + 1, name = "alert $index", type = listOf("Spawn"),
                    latitude = 49.85 + (index % 100) * 0.0003,
                    longitude = 8.60 + (index / 100) * 0.0003
                )
            }
            val protected = setOf(alerts.first().uniqueId)
            for (zoom in listOf(3.0, 12.0, 14.0, 20.0, 24.0)) {
                for (radius in listOf(null, 40.0, 80.0)) {
                    val items = clusterMapAlerts(alerts, zoom, spawnRadiusMeters = radius, protectedAlertIds = protected)
                    assertTrue("$count alerts at $zoom / $radius produced ${items.size} markers", items.size <= capFor(zoom))
                    val members = items.flatMap {
                        when (it) {
                            is MapMarkerItem.Alert -> listOf(it.alert)
                            is MapMarkerItem.Cluster -> it.alerts
                        }
                    }
                    assertEquals(count, members.size)
                    assertEquals(alerts.map { it.uniqueId }.toSet(), members.map { it.uniqueId }.toSet())
                    assertTrue(items.any { it is MapMarkerItem.Alert && it.alert.uniqueId in protected })
                }
            }
            assertEquals(clusterMapAlerts(alerts, 12.0), clusterMapAlerts(alerts.reversed(), 12.0))
        }
    }

    @Test
    fun coincidentDenseStackDoesLinearWorkAndRetainsTrackingPin() {
        val alerts = List(10_000) { PokemonAlert(id = it + 1, name = "stack $it", latitude = 49.87, longitude = 8.65) }
        var checkpoints = 0
        val items = clusterMapAlerts(alerts, 20.0, protectedAlertIds = setOf(alerts.first().uniqueId)) { checkpoints++ }
        assertEquals(2, items.size)
        assertEquals(9_999, (items.single { it is MapMarkerItem.Cluster } as MapMarkerItem.Cluster).alerts.size)
        assertTrue("Dense stack should never compare every pair", checkpoints < alerts.size * 10)
    }

    @Test
    fun obsoleteClusteringCanBeCancelledInsideDensePass() {
        val alerts = List(10_000) { PokemonAlert(id = it + 1, name = "a", latitude = 49.87, longitude = 8.65) }
        var checkpoints = 0
        try {
            clusterMapAlerts(alerts, 12.0, checkActive = {
                if (++checkpoints == 12_000) throw kotlinx.coroutines.CancellationException("obsolete camera")
            })
            org.junit.Assert.fail("Cancelled work must not return markers")
        } catch (_: kotlinx.coroutines.CancellationException) {
            assertEquals(12_000, checkpoints)
        }
    }

    @Test
    fun gridClusteringMatchesAnAllPairsReference() {
        val random = Random(1234)
        val points = List(600) {
            MapScreenPoint(
                x = random.nextDouble(0.0, 2_000.0),
                y = random.nextDouble(0.0, 2_000.0)
            )
        }
        val threshold = 48.0

        val expected = allPairsComponents(points, threshold)
        val actual = connectedMapScreenComponents(
            points = points,
            thresholdFor = { _, _ -> threshold },
            cellSize = threshold
        ).map { group -> group.map(points::get) }

        // Group order is not part of the contract (clusterMapAlerts re-sorts members), so
        // compare canonically: by each group's smallest original index.
        val expectedCanonical = expected.mapIndexed { index, _ -> index }.sortedBy { idx ->
            expected[idx].minOf { points.indexOf(it) }
        }
        val actualCanonical = actual.mapIndexed { index, _ -> index }.sortedBy { idx ->
            actual[idx].minOf { points.indexOf(it) }
        }
        assertEquals(
            expectedCanonical.map { expected[it].toSet() },
            actualCanonical.map { actual[it].toSet() }
        )
    }

    @Test
    fun thousandsOfAlertsNeverExceedTheRenderedMarkerCap() {
        // Worst case for the old exact-stack-only high-zoom branch: thousands of distinct
        // positions would render as thousands of individual markers.
        val random = Random(42)
        val alerts = List(3_000) { index ->
            PokemonAlert(
                name = "a$index",
                latitude = 49.70 + random.nextDouble(0.0, 0.2),
                longitude = 8.50 + random.nextDouble(0.0, 0.3),
                endTime = "2099-01-01T00:00:00Z",
                type = listOf("Spawn")
            )
        }

        val items = clusterMapAlerts(alerts = alerts, zoom = 20.0)

        assertTrue(items.size <= capFor(20.0))
    }

    @Test
    fun neighbourhoodZoomKeepsDetailInsteadOfCoarseningToTheZoomedOutBudget() {
        // A dense feed used to take the grid path at every zoom, doubling the cell size until only
        // MAX_RENDERED_MAP_MARKERS bubbles were left - a handful of bubbles across the screen even
        // when fully zoomed in. Zoomed in the grid must stay at its natural cell size instead.
        val random = Random(7)
        val alerts = List(1_200) { index ->
            PokemonAlert(
                id = index + 1, name = "a$index",
                latitude = 49.87 + random.nextDouble(-0.015, 0.015),
                longitude = 8.65 + random.nextDouble(-0.03, 0.03),
                type = listOf("Spawn")
            )
        }

        val items = clusterMapAlerts(alerts = alerts, zoom = 15.0)

        assertTrue(
            "Zoomed in, a dense view must keep more detail than the zoomed-out budget allows",
            items.size > com.example.pokemonalertsv2.data.MapClusteringPreset.CURRENT.config.overviewLimit
        )
        assertTrue(items.size <= capFor(15.0))
        assertEquals(
            alerts.map { it.uniqueId }.toSet(),
            items.flatMap {
                when (it) {
                    is MapMarkerItem.Alert -> listOf(it.alert)
                    is MapMarkerItem.Cluster -> it.alerts
                }
            }.map { it.uniqueId }.toSet()
        )
    }

    /** Coarsening only starts at the larger ceiling once individual markers are expected. */
    private fun capFor(zoom: Double): Int = with(com.example.pokemonalertsv2.data.MapClusteringPreset.CURRENT.config) {
        if (zoom >= zoomCutoff) closeLimit else overviewLimit
    }

    @Test
    fun viewportBoundsCoversTheScreenPlusMarginAndRejectsDegenerateSizes() {
        val bounds = mapViewportBounds(
            centreLatitude = 49.87,
            centreLongitude = 8.65,
            zoom = 13.0,
            viewportWidthDp = 400f,
            viewportHeightDp = 800f
        )
        assertNotNull(bounds)
        bounds!!
        assertTrue(bounds.contains(49.87, 8.65))

        // Padding is per axis, so a portrait viewport gets a portrait box. It used to be a
        // square built from the half-diagonal, which covered about eight times the visible
        // area - and since the marker limit was counted over whatever fell in this box, a
        // "350 marker" limit engaged at a few dozen markers actually on screen.
        val latitudeSpan = bounds.north - bounds.south
        val longitudeSpan = bounds.east - bounds.west
        assertTrue(latitudeSpan > longitudeSpan)

        // The screen corner plus the margin is covered; well beyond it is not.
        val screen = mapViewportBounds(
            centreLatitude = 49.87,
            centreLongitude = 8.65,
            zoom = 13.0,
            viewportWidthDp = 400f,
            viewportHeightDp = 800f,
            marginFactor = 0.0
        )!!
        assertTrue(bounds.contains(screen.north, screen.east))
        assertTrue(bounds.contains(screen.south, screen.west))
        assertFalse(bounds.contains(49.87 + latitudeSpan, 8.65))
        assertFalse(bounds.contains(49.87, 8.65 + longitudeSpan))

        assertNull(
            mapViewportBounds(49.87, 8.65, 13.0, viewportWidthDp = 0f, viewportHeightDp = 800f)
        )
    }

    /** Straightforward reference: every pair under the threshold is unioned. */
    private fun allPairsComponents(
        points: List<MapScreenPoint>,
        threshold: Double
    ): List<List<MapScreenPoint>> {
        val parent = IntArray(points.size) { it }
        fun find(index: Int): Int {
            var current = index
            while (parent[current] != current) {
                parent[current] = parent[parent[current]]
                current = parent[current]
            }
            return current
        }
        points.indices.forEach { first ->
            for (second in first + 1 until points.size) {
                val dx = points[first].x - points[second].x
                val dy = points[first].y - points[second].y
                if (dx * dx + dy * dy <= threshold * threshold) {
                    val firstRoot = find(first)
                    val secondRoot = find(second)
                    if (firstRoot != secondRoot) parent[secondRoot] = firstRoot
                }
            }
        }
        return points.indices
            .groupBy { find(it) }
            .toSortedMap()
            .values
            .map { group -> group.map(points::get) }
    }
}
