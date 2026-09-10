package com.example.pokemonalertsv2.hunt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HuntLegCostsTest {

    private val now = 1_700_000_000_000L
    private val originLat = 49.87275
    private val originLon = 8.65112
    private val aLat = 49.87410
    private val aLon = 8.64890
    private val bLat = 49.87058
    private val bLon = 8.65401

    private fun costs(
        legs: Map<HuntLegKey, HuntLeg> = defaultLegs(),
        calculatedAt: Long = now
    ) = ResolvedHuntLegCosts(
        legs = legs,
        nodes = mapOf("a" to huntLegNode(aLat, aLon), "b" to huntLegNode(bLat, bLon)),
        originNode = huntLegNode(originLat, originLon),
        originLatitude = originLat,
        originLongitude = originLon,
        calculatedAtMillis = calculatedAt,
        expiresAtMillis = calculatedAt + HUNT_LEG_TTL_MILLIS
    )

    private fun defaultLegs(): Map<HuntLegKey, HuntLeg> {
        val o = huntLegNode(originLat, originLon)
        val a = huntLegNode(aLat, aLon)
        val b = huntLegNode(bLat, bLon)
        return mapOf(
            HuntLegKey(o, a) to HuntLeg(317, 236),
            HuntLegKey(o, b) to HuntLeg(519, 411),
            HuntLegKey(a, b) to HuntLeg(756, 599),
            HuntLegKey(b, a) to HuntLeg(760, 563)
        )
    }

    @Test
    fun `a routed leg reads back in metres`() {
        val c = costs()

        assertEquals(317.0, c.walkedMetersOrNull(null, "a")!!, 0.001)
        assertEquals(756.0, c.walkedMetersOrNull("a", "b")!!, 0.001)
    }

    @Test
    fun `legs are directional`() {
        val c = costs()

        assertEquals(756.0, c.walkedMetersOrNull("a", "b")!!, 0.001)
        assertEquals(760.0, c.walkedMetersOrNull("b", "a")!!, 0.001)
    }

    @Test
    fun `an unknown id or an unrouted pair reads null`() {
        val c = costs()

        assertNull(c.walkedMetersOrNull(null, "nope"))
        assertNull(c.walkedMetersOrNull("nope", "a"))
        // a -> a is never a leg worth having.
        assertNull(c.walkedMetersOrNull("a", "a"))
    }

    @Test
    fun `walking away drops the trainer's legs but keeps the legs between targets`() {
        // 250 m north of the origin, well past HUNT_ORIGIN_DRIFT_METERS.
        val moved = costs().forOrigin(originLat + 0.00225, originLon, now)

        assertNull(moved.walkedMetersOrNull(null, "a"))
        assertNull(moved.walkSecondsFromOriginOrNull("a"))
        // The walk from a to b is the same walk wherever the trainer is standing.
        assertEquals(756.0, moved.walkedMetersOrNull("a", "b")!!, 0.001)
    }

    @Test
    fun `a small step keeps the trainer's legs`() {
        val nudged = costs().forOrigin(originLat + 0.00018, originLon, now)

        assertNotNull(nudged.walkedMetersOrNull(null, "a"))
    }

    @Test
    fun `everything expires with the snapshot`() {
        val c = costs()

        val justLive = c.forOrigin(originLat, originLon, now + HUNT_LEG_TTL_MILLIS - 1)
        val dead = c.forOrigin(originLat, originLon, now + HUNT_LEG_TTL_MILLIS)

        assertNotNull(justLive.walkedMetersOrNull("a", "b"))
        assertNull(dead.walkedMetersOrNull("a", "b"))
        assertNull(dead.walkedMetersOrNull(null, "a"))
    }

    @Test
    fun `seconds come from the routed distance on the app's own speed`() {
        val c = costs()

        // 317 m at 1.36 m/s, rounded up.
        assertEquals(huntWalkSeconds(317), c.walkSecondsFromOriginOrNull("a"))
        // Deliberately not the provider's 236 s: see HUNT_USE_PROVIDER_DURATIONS.
        assertEquals(false, c.walkSecondsFromOriginOrNull("a") == 236L)
    }

    @Test
    fun `two alerts within a few metres share a leg, further apart they do not`() {
        // 4 dp is about 11 m, so a 5 m step lands on the same node.
        assertEquals(huntLegNode(aLat, aLon), huntLegNode(aLat + 0.00004, aLon))
        assertEquals(false, huntLegNode(aLat, aLon) == huntLegNode(aLat + 0.0018, aLon))
    }

    @Test
    fun `None answers nothing`() {
        assertNull(HuntLegCosts.None.walkedMetersOrNull(null, "a"))
        assertNull(HuntLegCosts.None.walkedMetersOrNull("a", "b"))
        assertNull(HuntLegCosts.None.walkSecondsFromOriginOrNull("a"))
        assertEquals(0L, HuntLegCosts.None.calculatedAtMillis)
    }
}
