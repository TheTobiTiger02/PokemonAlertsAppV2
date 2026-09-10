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
}
