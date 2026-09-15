package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * Ordering by how far there is left to *walk*, not by how far away the pin is.
 *
 * Pokemon GO lets you spin a stop from 80 m and catch a spawn from 40 m, so two alerts
 * the same distance away are not the same distance away. These are the cases where
 * that changes the answer.
 */
class HuntInteractionRadiusTest {

    private val now = 1_700_000_000_000L
    private val originLat = 49.87275
    private val originLon = 8.65112

    private var nextId = 1

    private fun spawn(name: String, latitude: Double, longitude: Double) = PokemonAlert(
        id = nextId++,
        name = name,
        pokemon = name,
        type = listOf("Spawn"),
        latitude = latitude,
        longitude = longitude,
        endTime = Instant.ofEpochMilli(now + 60 * 60_000L).toString()
    )

    private fun stop(name: String, latitude: Double, longitude: Double) = PokemonAlert(
        id = nextId++,
        name = name,
        type = listOf("Quest"),
        pokestop = name,
        questTask = "Catch 5 Pokemon",
        latitude = latitude,
        longitude = longitude,
        endTime = Instant.ofEpochMilli(now + 60 * 60_000L).toString()
    )

    private fun costsOf(legs: Map<Pair<String?, String>, Double>) = object : HuntLegCosts {
        override val calculatedAtMillis = now
        override fun walkedMetersOrNull(fromId: String?, toId: String): Double? = legs[fromId to toId]
        override fun walkSecondsFromOriginOrNull(toId: String, slackMeters: Double): Long? =
            legs[null to toId]?.let { huntWalkSeconds((it - slackMeters).coerceAtLeast(0.0).toInt()) }
        override fun forOrigin(latitude: Double, longitude: Double, nowMillis: Long): HuntLegCosts = this
    }

    private fun List<PokemonAlert>.names() = map { it.name }

    @Test
    fun `a stop you pass at eighty metres beats a spawn twenty metres nearer`() {
        // The screenshot: the stop's pin is further away, but you can spin it from
        // where the spawn still needs another twenty metres of walking.
        val theStop = stop("stop", 49.87355, 8.65112) // ~89 m
        val theSpawn = spawn("spawn", 49.87337, 8.65112) // ~69 m
        val alerts = listOf(theStop, theSpawn)

        assertEquals(listOf("stop", "spawn"), huntPlan(alerts, originLat, originLon, now).route.names())
    }

    @Test
    fun `an alert you are already standing inside leads the route`() {
        val underfoot = stop("underfoot", 49.87280, 8.65112) // ~6 m: already spinnable
        val nearby = spawn("nearby", 49.87320, 8.65112) // ~50 m: ten still to walk
        val alerts = listOf(nearby, underfoot)

        assertEquals(
            listOf("underfoot", "nearby"),
            huntPlan(alerts, originLat, originLon, now).route.names()
        )
    }

    @Test
    fun `routed legs are priced to the edge of the radius, not to the pin`() {
        val a = spawn("a", 49.87400, 8.65200)
        val b = stop("b", 49.87500, 8.65300)
        val alerts = listOf(a, b)

        // To the pin, a is the shorter walk. To the point where each is tappable,
        // b is: 130 - 80 beats 100 - 40.
        val routed = huntPlan(
            alerts, originLat, originLon, now,
            costsOf(
                mapOf(
                    null to a.uniqueId to 100.0,
                    null to b.uniqueId to 130.0,
                    // Far apart, and the same walk either way once each radius is taken
                    // off, so only the first leg decides.
                    a.uniqueId to b.uniqueId to 5_040.0,
                    b.uniqueId to a.uniqueId to 5_000.0
                )
            )
        ).route

        assertEquals("b", routed.first().name)
    }

    @Test
    fun `a walk of spawns sweeps outward from the trainer`() {
        // Every alert a spawn, so the radius is one constant and only the geometry
        // decides: the nearest first, then onward without crossing back.
        val alerts = listOf(
            spawn("far", 49.8800, 8.6600),
            spawn("near", 49.8730, 8.6515),
            spawn("mid", 49.8760, 8.6540),
            spawn("other", 49.8745, 8.6490)
        )

        val ordered = huntPlan(alerts, originLat, originLon, now).route

        assertEquals(listOf("near", "other", "mid", "far"), ordered.names())
    }

    @Test
    fun `reachability counts the walk to the radius, not to the pin`() {
        val ending = stop("ending", 49.87400, 8.65112).copy(
            endTime = Instant.ofEpochMilli(now + 70_000L).toString()
        )
        // 175 m to the pin is over two minutes; 95 m to the edge is about seventy
        // seconds, which just fits inside what is left.
        val costs = costsOf(mapOf(null to ending.uniqueId to 175.0))

        assertEquals(listOf("ending"), huntPlan(listOf(ending), originLat, originLon, now, costs).route.names())
    }
}
