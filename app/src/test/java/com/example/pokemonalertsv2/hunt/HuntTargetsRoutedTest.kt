package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Planning once real walked legs are available, and keeping a plan once the trainer is on it.
 *
 * The costs are hand-built rather than fetched: this is about what the planner does with an
 * answer, not about how the answer arrives. Alerts are named so the assertions read, and keyed
 * on [PokemonAlert.uniqueId] because that is what the planner actually looks legs up by.
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
    private fun costsOf(legs: Map<Pair<String?, String>, Double> = emptyMap()) = object : HuntLegCosts {
        override val calculatedAtMillis = now
        override fun walkedMetersOrNull(fromId: String?, toId: String): Double? = legs[fromId to toId]
        override fun walkSecondsFromOriginOrNull(toId: String, slackMeters: Double): Long? =
            legs[null to toId]?.let { huntWalkSeconds((it - slackMeters).coerceAtLeast(0.0).toInt()) }
        override fun forOrigin(latitude: Double, longitude: Double, nowMillis: Long): HuntLegCosts = this
    }

    /** Every leg between [alerts] at [meters], plus the given overrides. */
    private fun uniform(alerts: List<PokemonAlert>, meters: Double, overrides: Map<Pair<String?, String>, Double>) =
        buildMap<Pair<String?, String>, Double> {
            for (from in alerts) for (to in alerts) if (from !== to) put(from.uniqueId to to.uniqueId, meters)
            for (to in alerts) put((null as String?) to to.uniqueId, meters)
            putAll(overrides)
        }

    private fun List<PokemonAlert>.names() = map { it.name }

    private fun plan(
        alerts: List<PokemonAlert>,
        legs: Map<Pair<String?, String>, Double> = emptyMap(),
        previous: List<PokemonAlert> = emptyList(),
        pinned: PokemonAlert? = null
    ) = huntPlan(
        alerts, originLat, originLon, now, costsOf(legs),
        previousRouteIds = previous.map { it.uniqueId },
        pinnedId = pinned?.uniqueId
    )

    @Test
    fun `the target across the river is walked after the one on this side`() {
        // "b" is nearer as the crow flies, but the only bridge is a long way round.
        val a = alert("a", 49.87330, 8.65200)
        val b = alert("b", 49.87300, 8.65150)

        assertEquals("b", plan(listOf(a, b)).route.first().name)

        val routed = plan(listOf(a, b), mapOf(null to a.uniqueId to 120.0, null to b.uniqueId to 1400.0))

        assertEquals(listOf("a", "b"), routed.route.names())
    }

    @Test
    fun `the route follows routed legs between targets, not just from the trainer`() {
        val a = alert("a", 49.87330, 8.65200)
        val b = alert("b", 49.87500, 8.65600)
        val c = alert("c", 49.87360, 8.65240)

        // a is nearest the trainer; from a, c is a short hop while b is far.
        val routed = plan(
            listOf(a, b, c),
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

        assertEquals(listOf("a", "c", "b"), routed.route.names())
    }

    @Test
    fun `a missing leg falls back to a straight line without losing anyone`() {
        val a = alert("a", 49.87330, 8.65200)
        val b = alert("b", 49.87500, 8.65600)
        val c = alert("c", 49.87360, 8.65240)

        val routed = plan(
            listOf(a, b, c),
            mapOf(null to a.uniqueId to 100.0, null to b.uniqueId to 900.0, null to c.uniqueId to 250.0)
        )

        assertEquals(setOf("a", "b", "c"), routed.route.names().toSet())
        assertEquals("a", routed.route.first().name)
    }

    @Test
    fun `a routed walk that clearly overruns keeps the target off the route`() {
        val soon = alert("soon", 49.87300, 8.65150, endsInMinutes = 5)
        val later = alert("later", 49.87600, 8.65600, endsInMinutes = 90)

        // The estimate says "soon" is reachable, so it leads.
        assertEquals("soon", plan(listOf(soon, later)).route.first().name)

        // Routed, the walk is 3 km and the alert has 5 minutes -- clearly too late.
        val routed = plan(listOf(soon, later), mapOf(null to soon.uniqueId to 3_000.0))

        assertEquals(listOf("later"), routed.route.names())
        assertEquals(listOf("soon"), routed.others.names())
    }

    @Test
    fun `a stop reached just in time is on the route, and one reached just too late is not`() {
        val soon = alert("soon", 49.87300, 8.65150, endsInMinutes = 10)
        // ~590 s against 600 s left, then ~640 s: nothing is numbered that the walk misses.
        assertEquals(listOf("soon"), plan(listOf(soon), mapOf(null to soon.uniqueId to 840.0)).route.names())
        assertEquals(emptyList<String>(), plan(listOf(soon), mapOf(null to soon.uniqueId to 910.0)).route.names())
    }

    @Test
    fun `more targets than the planner covers still come back whole`() {
        val alerts = (0 until 40).map { alert("t$it", 49.8700 + it * 0.0004, 8.6500 + it * 0.0004) }

        val routed = plan(alerts)

        assertEquals(alerts.names().toSet(), routed.ordered.names().toSet())
        assertEquals(40, routed.ordered.size)
    }

    @Test
    fun `the route does not double back for a target it skipped`() {
        // The greedy trap: from a, d is the cheapest single hop (40 m), but everything
        // is expensive once you are at d. Walking a -> b -> c -> d is the short way.
        val a = alert("a", 49.87330, 8.65200)
        val b = alert("b", 49.87360, 8.65240)
        val c = alert("c", 49.87390, 8.65280)
        val d = alert("d", 49.87420, 8.65320)

        val legs = mapOf<Pair<String?, String>, Double>(
            (null as String?) to a.uniqueId to 100.0,
            (null as String?) to b.uniqueId to 200.0,
            (null as String?) to c.uniqueId to 300.0,
            (null as String?) to d.uniqueId to 400.0,
            a.uniqueId to b.uniqueId to 90.0,
            b.uniqueId to c.uniqueId to 90.0,
            c.uniqueId to d.uniqueId to 90.0,
            a.uniqueId to d.uniqueId to 80.0,
            a.uniqueId to c.uniqueId to 300.0,
            d.uniqueId to c.uniqueId to 500.0,
            d.uniqueId to b.uniqueId to 600.0,
            d.uniqueId to a.uniqueId to 500.0,
            c.uniqueId to b.uniqueId to 90.0,
            c.uniqueId to a.uniqueId to 300.0,
            b.uniqueId to d.uniqueId to 600.0,
            b.uniqueId to a.uniqueId to 90.0
        )

        assertEquals(listOf("a", "b", "c", "d"), plan(listOf(a, b, c, d), legs).route.names())
    }

    @Test
    fun `a reversal that is only cheaper forwards is rejected`() {
        // Walking the middle stretch backwards is far more expensive than walking it
        // forwards. Routed legs are directional, which is why whole routes are scored.
        val a = alert("a", 49.87330, 8.65200)
        val b = alert("b", 49.87360, 8.65240)
        val c = alert("c", 49.87390, 8.65280)
        val d = alert("d", 49.87420, 8.65320)

        val legs = mapOf<Pair<String?, String>, Double>(
            (null as String?) to a.uniqueId to 100.0,
            (null as String?) to b.uniqueId to 200.0,
            (null as String?) to c.uniqueId to 300.0,
            (null as String?) to d.uniqueId to 400.0,
            a.uniqueId to b.uniqueId to 90.0,
            b.uniqueId to c.uniqueId to 90.0,
            c.uniqueId to d.uniqueId to 90.0,
            c.uniqueId to b.uniqueId to 5_000.0,
            b.uniqueId to a.uniqueId to 5_000.0,
            d.uniqueId to c.uniqueId to 5_000.0,
            a.uniqueId to c.uniqueId to 5_000.0,
            b.uniqueId to d.uniqueId to 5_000.0,
            a.uniqueId to d.uniqueId to 5_000.0,
            d.uniqueId to a.uniqueId to 5_000.0,
            c.uniqueId to a.uniqueId to 5_000.0,
            d.uniqueId to b.uniqueId to 5_000.0
        )

        assertEquals(listOf("a", "b", "c", "d"), plan(listOf(a, b, c, d), legs).route.names())
    }

    // --- only stops the walk will make ----------------------------------------

    @Test
    fun `a stop that would run out later is walked first rather than dropped`() {
        // Each is ~7 minutes from the trainer. Walked a, b, c the last is reached after
        // ~21 minutes and it only has 12 -- so it goes first, and all three are caught.
        val a = alert("a", 49.87330, 8.65200, endsInMinutes = 90)
        val b = alert("b", 49.87360, 8.65240, endsInMinutes = 90)
        val c = alert("c", 49.87390, 8.65280, endsInMinutes = 12)
        val legs = uniform(listOf(a, b, c), 610.0, mapOf((null as String?) to a.uniqueId to 590.0))

        val routed = plan(listOf(a, b, c), legs)

        assertEquals("c", routed.route.first().name)
        assertEquals(setOf("a", "b", "c"), routed.route.names().toSet())
    }

    @Test
    fun `a stop nobody can reach in time is never numbered, and costs the rest nothing`() {
        // The zigzag screenshot: stops the plan had given up on used to be appended to the
        // numbered route anyway. b ends almost at once; a and c are comfortable.
        val a = alert("a", 49.87330, 8.65200, endsInMinutes = 90)
        val b = alert("b", 49.87360, 8.65240, endsInMinutes = 1)
        val c = alert("c", 49.87390, 8.65280, endsInMinutes = 90)
        val legs = uniform(listOf(a, b, c), 200.0, mapOf((null as String?) to a.uniqueId to 100.0))

        val routed = plan(listOf(a, b, c), legs)

        assertEquals(listOf("a", "c"), routed.route.names())
        assertEquals(listOf("b"), routed.others.names())
    }

    @Test
    fun `every stop on the route is reached before it ends`() {
        // A row of stops that all end together, most too far to make: the route holds
        // only what the walk reaches, and in walking order.
        val alerts = (0 until 12).map { alert("t$it", originLat + (it + 1) * 0.0018, originLon, endsInMinutes = 12) }

        val routed = plan(alerts)

        assertTrue(routed.route.isNotEmpty())
        assertTrue(routed.others.isNotEmpty())
        assertEquals(alerts.take(routed.route.size).names(), routed.route.names())
    }

    // --- keeping the route the trainer is on ----------------------------------

    @Test
    fun `the route being walked is kept when a fresh plan is only a little better`() {
        val x = alert("x", 49.87400, 8.65300)
        val y = alert("y", 49.87420, 8.65320)
        // From scratch y then x is 20 m quicker -- not worth turning the trainer round.
        val legs = mapOf<Pair<String?, String>, Double>(
            (null as String?) to x.uniqueId to 300.0,
            (null as String?) to y.uniqueId to 280.0,
            x.uniqueId to y.uniqueId to 100.0,
            y.uniqueId to x.uniqueId to 100.0
        )

        assertEquals(listOf("y", "x"), plan(listOf(x, y), legs).route.names())
        assertEquals(listOf("x", "y"), plan(listOf(x, y), legs, previous = listOf(x, y)).route.names())
    }

    @Test
    fun `a new alert right on the way is taken first when that is clearly better`() {
        // Screenshot 2: the target is 900 m east, and a new alert lands 100 m away on the
        // path to it. Walking past it and coming back later would be absurd.
        val target = alert("target", 49.87300, 8.66300)
        val onTheWay = alert("onTheWay", 49.87280, 8.65250)
        val legs = mapOf<Pair<String?, String>, Double>(
            (null as String?) to target.uniqueId to 900.0,
            (null as String?) to onTheWay.uniqueId to 100.0,
            onTheWay.uniqueId to target.uniqueId to 810.0,
            target.uniqueId to onTheWay.uniqueId to 810.0
        )

        val routed = plan(listOf(target, onTheWay), legs, previous = listOf(target))

        assertEquals(listOf("onTheWay", "target"), routed.route.names())
    }

    @Test
    fun `a new alert off to the side is not forced in front of the target`() {
        val target = alert("target", 49.87400, 8.65300)
        val next = alert("next", 49.87450, 8.65350)
        val newcomer = alert("newcomer", 49.87100, 8.64900)
        val legs = mapOf<Pair<String?, String>, Double>(
            (null as String?) to target.uniqueId to 200.0,
            (null as String?) to next.uniqueId to 270.0,
            (null as String?) to newcomer.uniqueId to 260.0,
            target.uniqueId to next.uniqueId to 70.0,
            next.uniqueId to target.uniqueId to 70.0,
            target.uniqueId to newcomer.uniqueId to 460.0,
            newcomer.uniqueId to target.uniqueId to 460.0,
            next.uniqueId to newcomer.uniqueId to 530.0,
            newcomer.uniqueId to next.uniqueId to 530.0
        )

        val routed = plan(listOf(target, next, newcomer), legs, previous = listOf(target, next))

        assertEquals(listOf("target", "next", "newcomer"), routed.route.names())
    }

    @Test
    fun `a kept target that can no longer be reached gives way to the next stop`() {
        val gone = alert("gone", 49.87400, 8.65300, endsInMinutes = 1)
        val next = alert("next", 49.87450, 8.65350)
        val legs = uniform(listOf(gone, next), 900.0, emptyMap())

        val routed = plan(listOf(gone, next), legs, previous = listOf(gone, next))

        assertEquals(listOf("next"), routed.route.names())
    }

    @Test
    fun `a previous route id that is no longer a match is ignored`() {
        val alerts = listOf(alert("a", 49.87330, 8.65200), alert("b", 49.87500, 8.65600))
        val gone = alert("gone", 49.87400, 8.65300)

        assertEquals(plan(alerts).route.names(), plan(alerts, previous = listOf(gone)).route.names())
    }

    // --- a target picked by hand ----------------------------------------------

    @Test
    fun `a hand-picked target leads, and the rest follow from it`() {
        val here = alert("here", 49.87280, 8.65120)
        val target = alert("target", 49.88000, 8.66000)
        val nearTarget = alert("nearTarget", 49.88010, 8.66010)
        val alerts = listOf(here, target, nearTarget)

        assertEquals("here", plan(alerts).route.first().name)

        val routed = plan(alerts, pinned = target)

        assertEquals(listOf("target", "nearTarget"), routed.route.names().take(2))
    }

    @Test
    fun `a hand-picked target leads even when it looks too late`() {
        val target = alert("target", 49.87400, 8.65300, endsInMinutes = 1)
        val other = alert("other", 49.87500, 8.65400, endsInMinutes = 90)
        val legs = uniform(listOf(target, other), 900.0, emptyMap())

        assertEquals("target", plan(listOf(target, other), legs, pinned = target).route.first().name)
    }

    @Test
    fun `the alert next to a far hand-picked target follows it`() {
        // Tapping a distant pin is the whole point of tap-to-retarget, and ranking
        // candidates only from the trainer left the alerts beside the target out of reach.
        val anchor = alert("anchor", originLat + 0.0054, originLon)
        val besideAnchor = alert("besideAnchor", originLat + 0.0055, originLon)
        val alsoBesideAnchor = alert("alsoBesideAnchor", originLat + 0.0056, originLon)
        val nearTrainer = (0 until 34).map {
            alert("near$it", originLat + it * 0.00008, originLon + it * 0.00008)
        }

        val routed = plan(nearTrainer + listOf(anchor, besideAnchor, alsoBesideAnchor), pinned = anchor)

        assertEquals("anchor", routed.route.first().name)
        assertTrue(
            "expected an alert beside the anchor at #2, got ${routed.route.names()[1]}",
            routed.route.names()[1] in setOf("besideAnchor", "alsoBesideAnchor")
        )
    }

    @Test
    fun `a hand-picked target that is not a match is ignored`() {
        val alerts = listOf(alert("a", 49.87330, 8.65200), alert("b", 49.87500, 8.65600))
        val gone = alert("gone", 49.87400, 8.65300)

        assertEquals(plan(alerts).route.names(), plan(alerts, pinned = gone).route.names())
    }
}
