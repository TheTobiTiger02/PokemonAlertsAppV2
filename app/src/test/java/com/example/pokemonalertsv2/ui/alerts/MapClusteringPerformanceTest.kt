package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MapClusteringPerformanceTest {

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
            items.size > MAX_RENDERED_MAP_MARKERS
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
    private fun capFor(zoom: Double): Int =
        if (zoom >= MAP_CLUSTER_MAX_ZOOM) MAX_RENDERED_MAP_MARKERS_ZOOMED_IN else MAX_RENDERED_MAP_MARKERS

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
        assertTrue(bounds.contains(49.87 + 0.05, 8.65))
        assertTrue(bounds.contains(49.87, 8.65 + 0.08))
        assertFalse(bounds.contains(49.87 + 0.6, 8.65))
        assertFalse(bounds.contains(49.87, 8.65 + 0.9))

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
