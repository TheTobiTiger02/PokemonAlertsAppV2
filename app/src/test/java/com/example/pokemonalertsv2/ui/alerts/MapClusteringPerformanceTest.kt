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

        assertTrue(items.size <= MAX_RENDERED_MAP_MARKERS)
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
