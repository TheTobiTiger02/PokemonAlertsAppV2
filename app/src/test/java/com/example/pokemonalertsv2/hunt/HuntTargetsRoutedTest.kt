package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Ordering once real walked legs are available.
 *
 * The costs are hand-built rather than fetched: this is about what the ordering does
 * with an answer, not about how the answer arrives. Alerts are named so the
 * assertions read, and keyed on [PokemonAlert.uniqueId] because that is what the
 * ordering actually looks legs up by.
 */
class HuntTargetsRoutedTest {

    private val now = 1_700_000_000_000L
    private val originLat = 49.87275
    private val originLon = 8.65112

    private var nextId = 1

    private fun alert(
        name: String,
        latitude: Double,
        longitude: Double,
        endsInMinutes: Long = 60
    ) = PokemonAlert(
        id = nextId++,
        name = name,
        pokemon = name,
        type = listOf("Spawn"),
        latitude = latitude,
        longitude = longitude,
        endTime = Instant.ofEpochMilli(now + endsInMinutes * 60_000L).toString()
    )

    /** Costs that answer for exactly the legs handed to them, and nothing else. */
    private fun costsOf(
        legs: Map<Pair<String?, String>, Double> = emptyMap(),
        seconds: Map<String, Long> = emptyMap()
    ) = object : HuntLegCosts {
        override val calculatedAtMillis = now
        override fun walkedMetersOrNull(fromId: String?, toId: String): Double? = legs[fromId to toId]
        override fun walkSecondsFromOriginOrNull(toId: String): Long? = seconds[toId]
        override fun forOrigin(latitude: Double, longitude: Double, nowMillis: Long): HuntLegCosts = this
    }

    private fun List<PokemonAlert>.names() = map { it.name }

    @Test
    fun `with nothing routed the order is exactly what it was before`() {
        // The regression guard for the DETOUR_FACTOR scaling in chainNearest: one
        // positive factor on every fallback must not reorder anything.
        val alerts = listOf(
            alert("far", 49.8800, 8.6600),
            alert("near", 49.8730, 8.6515),
            alert("mid", 49.8760, 8.6540),
            alert("other", 49.8745, 8.6490)
        )

        val withNone = huntWalkOrder(alerts, originLat, originLon, now, HuntLegCosts.None)
        val withAllNull = huntWalkOrder(alerts, originLat, originLon, now, costsOf())
        val withDefault = huntWalkOrder(alerts, originLat, originLon, now)

        assertEquals(withNone.names(), withAllNull.names())
        assertEquals(withNone.names(), withDefault.names())
    }

    @Test
    fun `the target across the river sorts behind the one on this side`() {
        // "b" is nearer as the crow flies, but the only bridge is a long way round.
        val a = alert("a", 49.87330, 8.65200)
        val b = alert("b", 49.87300, 8.65150)
        val alerts = listOf(a, b)

        assertEquals(listOf("b", "a"), huntWalkOrder(alerts, originLat, originLon, now).names())

        val routed = huntWalkOrder(
            alerts, originLat, originLon, now,
            costsOf(mapOf(null to a.uniqueId to 120.0, null to b.uniqueId to 1400.0))
        )

        assertEquals(listOf("a", "b"), routed.names())
    }

    @Test
    fun `the chain follows routed legs between targets, not just from the trainer`() {
        val a = alert("a", 49.87330, 8.65200)
        val b = alert("b", 49.87500, 8.65600)
        val c = alert("c", 49.87360, 8.65240)
        val alerts = listOf(a, b, c)

        // a is nearest the trainer; from a, c is a short hop while b is far.
        val routed = huntWalkOrder(
            alerts, originLat, originLon, now,
            costsOf(
                mapOf(
                    null to a.uniqueId to 100.0,
                    null to b.uniqueId to 900.0,
                    null to c.uniqueId to 250.0,
                    a.uniqueId to b.uniqueId to 1500.0,
                    a.uniqueId to c.uniqueId to 90.0,
                    c.uniqueId to b.uniqueId to 1400.0,
                    c.uniqueId to a.uniqueId to 95.0,
                    b.uniqueId to a.uniqueId to 1500.0,
                    b.uniqueId to c.uniqueId to 1400.0
                )
            )
        )

        assertEquals(listOf("a", "c", "b"), routed.names())
    }

    @Test
    fun `a missing leg mid-chain falls back without losing anyone`() {
        val a = alert("a", 49.87330, 8.65200)
        val b = alert("b", 49.87500, 8.65600)
        val c = alert("c", 49.87360, 8.65240)
        val alerts = listOf(a, b, c)

        // Only the trainer's legs are known; nothing between targets is.
        val routed = huntWalkOrder(
            alerts, originLat, originLon, now,
            costsOf(
                mapOf(
                    null to a.uniqueId to 100.0,
                    null to b.uniqueId to 900.0,
                    null to c.uniqueId to 250.0
                )
            )
        )

        assertEquals(3, routed.size)
        assertEquals(setOf("a", "b", "c"), routed.names().toSet())
        assertEquals("a", routed.first().name)
    }

    @Test
    fun `a routed walk that clearly overruns drops the target to the back`() {
        val soon = alert("soon", 49.87300, 8.65150, endsInMinutes = 5)
        val later = alert("later", 49.87600, 8.65600, endsInMinutes = 90)
        val alerts = listOf(soon, later)

        // The estimate says "soon" is reachable, so it leads.
        assertEquals(listOf("soon", "later"), huntWalkOrder(alerts, originLat, originLon, now).names())

        // Routed, the walk is 40 minutes and the alert has 5 -- clearly too late.
        val routed = huntWalkOrder(
            alerts, originLat, originLon, now,
            costsOf(seconds = mapOf(soon.uniqueId to 2_400L))
        )

        assertEquals(listOf("later", "soon"), routed.names())
    }

    @Test
    fun `a borderline overrun keeps the target, thanks to the margin`() {
        val soon = alert("soon", 49.87300, 8.65150, endsInMinutes = 10)
        // 640 s routed against 600 s left: over, but only just, so the margin keeps it.
        val costs = costsOf(seconds = mapOf(soon.uniqueId to 640L))

        assertTrue(canArriveBeforeItEnds(soon, originLat, originLon, now, costs))
        assertEquals(listOf("soon"), huntWalkOrder(listOf(soon), originLat, originLon, now, costs).names())
    }

    @Test
    fun `more targets than the matrix covers still come back whole and ordered`() {
        val alerts = (0 until 40).map { alert("t$it", 49.8700 + it * 0.0004, 8.6500 + it * 0.0004) }
        // Only the first handful are routed, as the 29-target cap would give.
        val legs = alerts.take(5).mapIndexed { index, a ->
            ((null as String?) to a.uniqueId) to (100.0 + index)
        }.toMap()

        val routed = huntWalkOrder(alerts, originLat, originLon, now, costsOf(legs))

        assertEquals(40, routed.size)
        assertEquals(alerts.names().toSet(), routed.names().toSet())
    }

    // --- 2-opt -------------------------------------------------------------

    @Test
    fun `the chain stops doubling back on a target it skipped`() {
        // The greedy trap: from a, d is the cheapest single hop (40 m), but everything
        // is expensive once you are at d, so the walk costs 690 m. Reversing the
        // stretch after a gives a -> b -> c -> d at 250 m -- one 2-opt move.
        val a = alert("a", 49.87330, 8.65200)
        val b = alert("b", 49.87360, 8.65240)
        val c = alert("c", 49.87390, 8.65280)
        val d = alert("d", 49.87420, 8.65320)
        val alerts = listOf(a, b, c, d)

        val legs = mapOf<Pair<String?, String>, Double>(
            (null as String?) to a.uniqueId to 100.0,
            (null as String?) to b.uniqueId to 200.0,
            (null as String?) to c.uniqueId to 300.0,
            (null as String?) to d.uniqueId to 400.0,
            a.uniqueId to b.uniqueId to 50.0,
            b.uniqueId to c.uniqueId to 50.0,
            c.uniqueId to d.uniqueId to 50.0,
            a.uniqueId to d.uniqueId to 40.0,
            a.uniqueId to c.uniqueId to 300.0,
            d.uniqueId to c.uniqueId to 500.0,
            d.uniqueId to b.uniqueId to 600.0,
            d.uniqueId to a.uniqueId to 500.0,
            c.uniqueId to b.uniqueId to 50.0,
            c.uniqueId to a.uniqueId to 300.0,
            b.uniqueId to d.uniqueId to 600.0,
            b.uniqueId to a.uniqueId to 50.0
        )

        val routed = huntWalkOrder(alerts, originLat, originLon, now, costsOf(legs))

        assertEquals(listOf("a", "b", "c", "d"), routed.names())
    }

    @Test
    fun `a reversal that only looks good on its cut edges is rejected`() {
        // The two-edge shortcut would take this move; scoring the whole tour will not,
        // because walking the reversed stretch backwards is far more expensive than
        // walking it forwards. Directional legs are the reason we score the tour.
        val a = alert("a", 49.87330, 8.65200)
        val b = alert("b", 49.87360, 8.65240)
        val c = alert("c", 49.87390, 8.65280)
        val d = alert("d", 49.87420, 8.65320)
        val alerts = listOf(a, b, c, d)

        val legs = mapOf<Pair<String?, String>, Double>(
            (null as String?) to a.uniqueId to 100.0,
            (null as String?) to b.uniqueId to 200.0,
            (null as String?) to c.uniqueId to 300.0,
            (null as String?) to d.uniqueId to 400.0,
            a.uniqueId to b.uniqueId to 50.0,
            b.uniqueId to c.uniqueId to 50.0,
            c.uniqueId to d.uniqueId to 50.0,
            // Backwards through the middle is brutal.
            c.uniqueId to b.uniqueId to 5_000.0,
            b.uniqueId to a.uniqueId to 5_000.0,
            d.uniqueId to c.uniqueId to 5_000.0,
            a.uniqueId to c.uniqueId to 60.0,
            b.uniqueId to d.uniqueId to 60.0,
            a.uniqueId to d.uniqueId to 90.0,
            d.uniqueId to a.uniqueId to 90.0,
            c.uniqueId to a.uniqueId to 60.0,
            d.uniqueId to b.uniqueId to 60.0
        )

        val routed = huntWalkOrder(alerts, originLat, originLon, now, costsOf(legs))

        assertEquals(listOf("a", "b", "c", "d"), routed.names())
    }

    @Test
    fun `an unrouted chain is never rearranged`() {
        val alerts = (0 until 6).map { alert("t$it", 49.8730 + it * 0.0008, 8.6515 + it * 0.0008) }

        assertEquals(
            huntWalkOrder(alerts, originLat, originLon, now).names(),
            huntWalkOrder(alerts, originLat, originLon, now, costsOf()).names()
        )
    }

    // --- chain-aware reachability ------------------------------------------

    @Test
    fun `a target reachable on its own is demoted once the walk ahead of it counts`() {
        // Each is ~7 minutes from the trainer, so alone they all survive. Walked in
        // sequence the third is reached after ~21 minutes, and it only has 12.
        val a = alert("a", 49.87330, 8.65200, endsInMinutes = 90)
        val b = alert("b", 49.87360, 8.65240, endsInMinutes = 90)
        val c = alert("c", 49.87390, 8.65280, endsInMinutes = 12)
        val alerts = listOf(a, b, c)

        val seconds = mapOf(a.uniqueId to 420L, b.uniqueId to 420L, c.uniqueId to 420L)
        val legs = mapOf<Pair<String?, String>, Double>(
            (null as String?) to a.uniqueId to 570.0,
            (null as String?) to b.uniqueId to 580.0,
            (null as String?) to c.uniqueId to 590.0,
            a.uniqueId to b.uniqueId to 570.0,
            b.uniqueId to c.uniqueId to 570.0,
            a.uniqueId to c.uniqueId to 580.0,
            c.uniqueId to b.uniqueId to 570.0,
            b.uniqueId to a.uniqueId to 570.0,
            c.uniqueId to a.uniqueId to 580.0
        )

        // Judged alone, c is fine.
        assertTrue(canArriveBeforeItEnds(c, originLat, originLon, now, costsOf(legs, seconds)))

        val routed = huntWalkOrder(alerts, originLat, originLon, now, costsOf(legs, seconds))

        // ...but it goes to the back once the walk ahead of it is counted.
        assertEquals("c", routed.names().last())
    }

    @Test
    fun `one unreachable target does not condemn the ones behind it`() {
        // b expires almost immediately; a and c are both comfortable. Skipping b must
        // not add its walk to the running total, so c survives.
        val a = alert("a", 49.87330, 8.65200, endsInMinutes = 90)
        val b = alert("b", 49.87360, 8.65240, endsInMinutes = 1)
        val c = alert("c", 49.87390, 8.65280, endsInMinutes = 90)
        val alerts = listOf(a, b, c)

        val legs = mapOf<Pair<String?, String>, Double>(
            (null as String?) to a.uniqueId to 100.0,
            (null as String?) to b.uniqueId to 200.0,
            (null as String?) to c.uniqueId to 300.0,
            a.uniqueId to b.uniqueId to 100.0,
            b.uniqueId to c.uniqueId to 100.0,
            a.uniqueId to c.uniqueId to 150.0,
            c.uniqueId to b.uniqueId to 100.0,
            b.uniqueId to a.uniqueId to 100.0,
            c.uniqueId to a.uniqueId to 150.0
        )

        val routed = huntWalkOrder(alerts, originLat, originLon, now, costsOf(legs))

        assertEquals(3, routed.size)
        assertEquals("b", routed.names().last())
        assertTrue(routed.names().indexOf("c") < routed.names().indexOf("b"))
    }

    @Test
    fun `an alert with no end time is carried, never demoted`() {
        val a = alert("a", 49.87330, 8.65200, endsInMinutes = 90)
        val forever = PokemonAlert(
            id = 9_999,
            name = "forever",
            pokemon = "forever",
            type = listOf("Spawn"),
            latitude = 49.87360,
            longitude = 8.65240
        )
        val legs = mapOf<Pair<String?, String>, Double>(
            (null as String?) to a.uniqueId to 100.0,
            (null as String?) to forever.uniqueId to 200.0,
            a.uniqueId to forever.uniqueId to 100.0,
            forever.uniqueId to a.uniqueId to 100.0
        )

        val routed = huntWalkOrder(listOf(a, forever), originLat, originLon, now, costsOf(legs))

        assertEquals(setOf("a", "forever"), routed.names().toSet())
    }

    // --- the committed target anchors the route ----------------------------

    @Test
    fun `the target being walked to leads, and the rest follow from it`() {
        val here = alert("here", 49.87280, 8.65120)     // right next to the trainer
        val target = alert("target", 49.88000, 8.66000)  // the one being walked to
        val nearTarget = alert("nearTarget", 49.88010, 8.66010)
        val alerts = listOf(here, target, nearTarget)

        // Unanchored, the one under your feet leads.
        assertEquals("here", huntWalkOrder(alerts, originLat, originLon, now).names().first())

        val routed = huntWalkOrder(
            alerts, originLat, originLon, now, costsOf(), anchorId = target.uniqueId
        )

        // Anchored, the list is the route: target, then what is near the target, and
        // the alert beside the trainer sorts last because it is a detour from there.
        assertEquals(listOf("target", "nearTarget", "here"), routed.names())
    }

    @Test
    fun `the rest are chained on legs from the anchor, not from the trainer`() {
        val target = alert("target", 49.87400, 8.65300)
        val a = alert("a", 49.87500, 8.65400)
        val b = alert("b", 49.87600, 8.65500)
        val alerts = listOf(target, a, b)

        // From the trainer a looks closer; from the anchor b is the short hop.
        val legs = mapOf<Pair<String?, String>, Double>(
            (null as String?) to target.uniqueId to 200.0,
            (null as String?) to a.uniqueId to 210.0,
            (null as String?) to b.uniqueId to 900.0,
            target.uniqueId to b.uniqueId to 40.0,
            target.uniqueId to a.uniqueId to 800.0,
            b.uniqueId to a.uniqueId to 60.0,
            a.uniqueId to b.uniqueId to 60.0
        )

        val routed = huntWalkOrder(
            alerts, originLat, originLon, now, costsOf(legs), anchorId = target.uniqueId
        )

        assertEquals(listOf("target", "b", "a"), routed.names())
    }

    @Test
    fun `an anchor that is not on the list is ignored`() {
        val alerts = listOf(
            alert("a", 49.87330, 8.65200),
            alert("b", 49.87500, 8.65600),
            alert("c", 49.87360, 8.65240)
        )

        assertEquals(
            huntWalkOrder(alerts, originLat, originLon, now).names(),
            huntWalkOrder(alerts, originLat, originLon, now, costsOf(), anchorId = "server-gone").names()
        )
    }

    @Test
    fun `a null anchor changes nothing`() {
        val alerts = listOf(
            alert("a", 49.87330, 8.65200),
            alert("b", 49.87500, 8.65600),
            alert("c", 49.87360, 8.65240)
        )

        assertEquals(
            huntWalkOrder(alerts, originLat, originLon, now).names(),
            huntWalkOrder(alerts, originLat, originLon, now, costsOf(), anchorId = null).names()
        )
    }

    @Test
    fun `the anchor is never demoted, even when its own arrival looks late`() {
        // You are already walking to it; dropping it to the back of its own route
        // would be perverse. It still puts its leg on the clock for the ones behind.
        val target = alert("target", 49.87400, 8.65300, endsInMinutes = 1)
        val other = alert("other", 49.87500, 8.65400, endsInMinutes = 90)
        val alerts = listOf(target, other)

        val legs = mapOf<Pair<String?, String>, Double>(
            (null as String?) to target.uniqueId to 900.0,
            (null as String?) to other.uniqueId to 950.0,
            target.uniqueId to other.uniqueId to 100.0,
            other.uniqueId to target.uniqueId to 100.0
        )

        val routed = huntWalkOrder(
            alerts, originLat, originLon, now, costsOf(legs), anchorId = target.uniqueId
        )

        assertEquals("target", routed.names().first())
    }

    @Test
    fun `2-opt cannot displace the anchor from the front`() {
        val target = alert("target", 49.87400, 8.65300)
        val a = alert("a", 49.87420, 8.65320)
        val b = alert("b", 49.87440, 8.65340)
        val c = alert("c", 49.87460, 8.65360)
        val alerts = listOf(target, a, b, c)

        // Legs that make the anchor look like a terrible place to start.
        val legs = mapOf<Pair<String?, String>, Double>(
            (null as String?) to target.uniqueId to 100.0,
            (null as String?) to a.uniqueId to 100.0,
            (null as String?) to b.uniqueId to 100.0,
            (null as String?) to c.uniqueId to 100.0,
            target.uniqueId to a.uniqueId to 5_000.0,
            target.uniqueId to b.uniqueId to 5_000.0,
            target.uniqueId to c.uniqueId to 5_000.0,
            a.uniqueId to b.uniqueId to 10.0,
            b.uniqueId to c.uniqueId to 10.0,
            a.uniqueId to c.uniqueId to 10.0,
            c.uniqueId to b.uniqueId to 10.0,
            b.uniqueId to a.uniqueId to 10.0,
            c.uniqueId to a.uniqueId to 10.0
        )

        val routed = huntWalkOrder(
            alerts, originLat, originLon, now, costsOf(legs), anchorId = target.uniqueId
        )

        assertEquals("target", routed.names().first())
        assertEquals(4, routed.size)
    }
}
