package com.example.pokemonalertsv2.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The projection is hand-rolled, so these pin the properties the map relies on rather than
 * exact cell ids: a point sits inside the cell it resolves to, cells are the size Pokémon GO
 * uses, and nearby points share one.
 */
class S2CellTest {

    private val darmstadt = 49.87275 to 8.65112
    private val alsbach = 49.74677 to 8.62492

    @Test
    fun cellContainsThePointItWasResolvedFrom() {
        listOf(
            darmstadt,
            alsbach,
            52.52 to 13.405,      // Berlin
            -33.8688 to 151.2093, // Sydney, southern hemisphere
            -22.9068 to -43.1729, // Rio, both signs
            64.1466 to -21.9426   // Reykjavik, high latitude
        ).forEach { (latitude, longitude) ->
            val boundary = s2CellAt(latitude, longitude).boundary()
            assertTrue(
                "cell for $latitude,$longitude should contain it",
                boundary.containsPoint(latitude, longitude)
            )
        }
    }

    @Test
    fun aLevel10CellIsRoughlyTenKilometresAcross() {
        val boundary = s2CellAt(darmstadt.first, darmstadt.second).boundary(pointsPerEdge = 1)
        // pointsPerEdge = 1 gives exactly the four corners plus the closing repeat.
        assertEquals(5, boundary.size)
        val diagonal = metersBetween(boundary[0], boundary[2])
        assertTrue("diagonal was ${diagonal / 1000} km", diagonal in 9_000.0..16_000.0)

        val side = metersBetween(boundary[0], boundary[1])
        assertTrue("side was ${side / 1000} km", side in 6_000.0..12_000.0)
    }

    @Test
    fun nearbyPointsShareACellAndDistantOnesDoNot() {
        val centre = s2CellAt(darmstadt.first, darmstadt.second)
        // ~200m north — comfortably inside a 10km cell unless it straddles an edge.
        val nudged = s2CellAt(darmstadt.first + 0.0018, darmstadt.second)
        assertEquals(centre, nudged)

        // Alsbach is ~14km south of Darmstadt: a different weather cell, which is the whole
        // reason the app can show the two areas different weather.
        assertNotEquals(centre, s2CellAt(alsbach.first, alsbach.second))
    }

    @Test
    fun boundaryVerticesResolveBackToTheSameOrAdjacentCell() {
        val cell = s2CellAt(darmstadt.first, darmstadt.second)
        val centre = cell.centre()
        assertEquals(cell, s2CellAt(centre.latitude, centre.longitude))
        assertTrue(cell.boundary().containsPoint(centre.latitude, centre.longitude))
    }

    @Test
    fun levelControlsCellSize() {
        val coarse = s2CellAt(darmstadt.first, darmstadt.second, level = 8)
            .boundary(pointsPerEdge = 1)
        val fine = s2CellAt(darmstadt.first, darmstadt.second, level = 12)
            .boundary(pointsPerEdge = 1)
        assertTrue(metersBetween(coarse[0], coarse[2]) > metersBetween(fine[0], fine[2]) * 8)
    }

    @Test
    fun subdividingEdgesAddsPointsWithoutMovingTheCorners() {
        val cell = s2CellAt(darmstadt.first, darmstadt.second)
        val corners = cell.boundary(pointsPerEdge = 1)
        val smooth = cell.boundary(pointsPerEdge = 8)
        assertEquals(33, smooth.size)
        assertEquals(corners.first(), smooth.first())
        assertEquals(corners.last(), smooth.last())
        // Every corner still appears, at its subdivided index.
        corners.dropLast(1).forEachIndexed { index, corner ->
            assertEquals(corner, smooth[index * 8])
        }
    }

    /** Ray casting in lat/lng. Fine at this scale: a 10km cell is nowhere near the antimeridian. */
    private fun List<S2LatLng>.containsPoint(latitude: Double, longitude: Double): Boolean {
        var inside = false
        for (index in 0 until size - 1) {
            val a = this[index]
            val b = this[index + 1]
            val straddles = (a.latitude > latitude) != (b.latitude > latitude)
            if (!straddles) continue
            val crossing = a.longitude +
                (latitude - a.latitude) / (b.latitude - a.latitude) * (b.longitude - a.longitude)
            if (longitude < crossing) inside = !inside
        }
        return inside
    }

    private fun metersBetween(a: S2LatLng, b: S2LatLng): Double {
        val earthRadius = 6_371_000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val deltaLat = lat2 - lat1
        val deltaLng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(lat1) * cos(lat2) * sin(deltaLng / 2) * sin(deltaLng / 2)
        return 2 * earthRadius * atan2(sqrt(h), sqrt(1 - h))
    }

    @Suppress("unused")
    private fun assertClose(expected: Double, actual: Double, tolerance: Double) {
        assertTrue("expected $expected got $actual", abs(expected - actual) <= tolerance)
    }
}
