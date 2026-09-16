package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.catchroutes.CatchPathGeometry
import com.example.pokemonalertsv2.catchroutes.CatchPathLeg
import com.example.pokemonalertsv2.catchroutes.CatchPathResponse
import com.example.pokemonalertsv2.catchroutes.CatchPoint
import com.example.pokemonalertsv2.catchroutes.CatchSnappedPoint
import com.example.pokemonalertsv2.data.RouteMatrixPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuntPathGeometryTest {
    private val origin = HuntRoutePoint(HuntRouteMatrixCache.HUNT_ORIGIN_ID, 49.8700, 8.6500)
    private val a = HuntRoutePoint("a", 49.8720, 8.6510)
    private val b = HuntRoutePoint("b", 49.8740, 8.6530)
    private val c = HuntRoutePoint("c", 49.8760, 8.6490)

    private fun key(from: HuntRoutePoint, to: HuntRoutePoint) =
        HuntLegKey(huntLegNode(from.latitude, from.longitude), huntLegNode(to.latitude, to.longitude))

    private fun line(from: HuntRoutePoint, to: HuntRoutePoint) = listOf(
        CatchPoint(from.latitude, from.longitude),
        CatchPoint((from.latitude + to.latitude) / 2, from.longitude),
        CatchPoint(to.latitude, to.longitude)
    )

    @Test fun `the chain leads with the trainer, skips unusable and repeated stops, and is capped`() {
        val many = (1..20).map { HuntRoutePoint("s$it", 49.87 + it * 0.001, 8.65) }
        val chain = huntPathChain(origin, listOf(a, a.copy(id = "same-place"), HuntRoutePoint("zero", 0.0, 0.0),
            HuntRoutePoint("nan", Double.NaN, 8.6)) + many)
        assertEquals(origin, chain.first())
        assertEquals(listOf("a", "s1", "s2"), chain.drop(1).take(3).map { it.id })
        assertEquals(HUNT_PATH_MAX_LEGS + 1, chain.size)
        assertTrue(huntPathChain(origin, emptyList()).isEmpty())
    }

    @Test fun `only the stretch with missing legs is asked for`() {
        val chain = listOf(origin, a, b, c)
        val all = setOf(key(origin, a), key(a, b), key(b, c))
        assertNull(huntPathRequestSpan(chain, all))
        assertEquals(0..1, huntPathRequestSpan(chain, all - key(origin, a)))
        assertEquals(1..2, huntPathRequestSpan(chain, all - key(a, b)))
        assertEquals(0..3, huntPathRequestSpan(chain, setOf(key(a, b))))
    }

    @Test fun `stitching splits off the next leg and does not repeat joints`() {
        val chain = listOf(origin, a, b, c)
        val legs = mapOf(key(origin, a) to line(origin, a), key(a, b) to line(a, b), key(b, c) to line(b, c))
        val path = huntPathFrom(chain, legs, 42L) as HuntPath.Resolved
        assertEquals(line(origin, a), path.next)
        assertEquals(5, path.rest.size)
        assertEquals(CatchPoint(a.latitude, a.longitude), path.rest.first())
        assertEquals(CatchPoint(c.latitude, c.longitude), path.rest.last())
        assertEquals(42L, path.calculatedAtMillis)
        // A missing leg draws nothing rather than half a route.
        assertEquals(HuntPath.None, huntPathFrom(chain, legs - key(a, b), 42L))
    }

    private fun response(points: List<RouteMatrixPoint>, coordinates: (RouteMatrixPoint, RouteMatrixPoint) -> List<List<Double>>) =
        CatchPathResponse(
            status = "ok", provider = "test", calculatedAt = "2026-09-16T10:00:00Z",
            snappedPoints = points.map { CatchSnappedPoint(it.id, it.latitude, it.longitude) },
            legs = points.zipWithNext { from, to ->
                CatchPathLeg(from.id, to.id, 100.0, 70.0, CatchPathGeometry("LineString", coordinates(from, to)))
            }
        )

    @Test fun `GeoJSON longitude-first coordinates become latitude-first points`() {
        val points = listOf(RouteMatrixPoint("h0", a.latitude, a.longitude), RouteMatrixPoint("h1", b.latitude, b.longitude))
        val parsed = parseHuntPath(response(points) { from, to ->
            listOf(listOf(from.longitude, from.latitude), listOf(to.longitude, to.latitude))
        }, points)!!
        assertEquals(listOf(CatchPoint(a.latitude, a.longitude), CatchPoint(b.latitude, b.longitude)), parsed[key(a, b)])
    }

    @Test fun `a response that does not describe the request is rejected`() {
        val points = listOf(RouteMatrixPoint("h0", a.latitude, a.longitude), RouteMatrixPoint("h1", b.latitude, b.longitude))
        val good = response(points) { from, to -> listOf(listOf(from.longitude, from.latitude), listOf(to.longitude, to.latitude)) }
        assertNull(parseHuntPath(good.copy(status = "degraded"), points))
        assertNull(parseHuntPath(good.copy(legs = emptyList()), points))
        assertNull(parseHuntPath(good.copy(snappedPoints = good.snappedPoints.reversed()), points))
        assertNull(parseHuntPath(response(points) { from, _ -> listOf(listOf(from.longitude, from.latitude)) }, points))
        assertNull(parseHuntPath(response(points) { _, _ -> listOf(listOf(8.65, 49.87, 1.0), listOf(8.65, 49.88)) }, points))
        assertNull(parseHuntPath(response(points) { _, _ -> listOf(listOf(200.0, 49.87), listOf(8.65, 49.88)) }, points))
    }
}
