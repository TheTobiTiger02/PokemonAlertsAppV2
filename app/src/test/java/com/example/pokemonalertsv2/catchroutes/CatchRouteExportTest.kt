package com.example.pokemonalertsv2.catchroutes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CatchRouteExportTest {
    /** [x] metres east and [y] metres north of a point in Darmstadt. */
    private fun p(x: Double, y: Double = 0.0) = CatchPoint(49.87 + y / 111_195, 8.65 + x / (111_195 * Math.cos(Math.toRadians(49.87))))
    private fun path(vararg points: CatchPoint): List<CatchPathPosition> {
        var meters = 0.0
        return points.mapIndexed { i, point -> if (i > 0) meters += catchDistance(points[i - 1], point); CatchPathPosition(point, meters) }
    }
    /** A straight 1 km path sampled every 10 m. */
    private fun straight(length: Int = 1000) = path(*(0..length step 10).map { p(it.toDouble()) }.toTypedArray())

    @Test fun `every stop becomes a waypoint, in walking order, when they fit`() {
        val stops = listOf(200.0, 600.0, 900.0)
        val waypoints = catchRouteWaypoints(straight(), stops)
        assertTrue(waypoints.size in stops.size..MAX_WAYPOINTS)
        for (meters in stops) assertTrue(waypoints.any { catchDistance(it, catchPathPointAt(straight(), meters)) < 5 })
        // Sorted along the route.
        val distances = waypoints.map { catchDistance(straight().first().point, it) }
        assertEquals(distances.sorted(), distances)
    }

    @Test fun `more stops than Google takes are thinned evenly, keeping the first and last`() {
        val route = straight(3000)
        val stops = (1..15).map { it * 180.0 }
        val waypoints = catchRouteWaypoints(route, stops)
        assertEquals(MAX_WAYPOINTS, waypoints.size)
        assertTrue(catchDistance(waypoints.first(), catchPathPointAt(route, stops.first())) < 5)
        assertTrue(catchDistance(waypoints.last(), catchPathPointAt(route, stops.last())) < 5)
    }

    @Test fun `corners survive when there are few stops`() {
        // An L: 500 m east, then 500 m north. The corner must be one of the waypoints.
        val route = path(*((0..500 step 25).map { p(it.toDouble()) } + (25..500 step 25).map { p(500.0, it.toDouble()) }).toTypedArray())
        val waypoints = catchRouteWaypoints(route, listOf(250.0))
        assertTrue("corner kept", waypoints.any { catchDistance(it, p(500.0)) < 20 })
        assertTrue(waypoints.size <= MAX_WAYPOINTS)
    }

    @Test fun `the link is walking, coordinate-safe in any locale, and within Google's waypoint limit`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val route = straight(2000)
            val url = googleMapsWalkingUrl(route.first().point, route.last().point,
                catchRouteWaypoints(route, (1..12).map { it * 150.0 }))
            assertTrue(url.startsWith("https://www.google.com/maps/dir/?api=1"))
            assertTrue(url.contains("&travelmode=walking"))
            val waypoints = url.substringAfter("&waypoints=").split("%7C")
            assertEquals(MAX_WAYPOINTS, waypoints.size)
            for (pair in waypoints) assertEquals("two decimal coordinates: $pair", 2, pair.split(",").size)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun `a route without a path cannot be exported`() {
        val settings = CatchRouteSettings(start = p(0.0), startAtMillis = 1_000L)
        assertNull(catchRouteMapsUrl(CatchItinerary(settings, emptyList(), emptyList(), emptyList())))
        assertNull(catchRouteMapsUrl(CatchItinerary(settings, path(p(0.0)), emptyList(), emptyList())))
        val exported = catchRouteMapsUrl(CatchItinerary(settings, straight(), emptyList(), emptyList()))
        assertTrue(exported!!.contains("origin=49.87"))
    }
}
