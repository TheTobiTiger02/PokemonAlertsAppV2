package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HuntRoutePrefetchTest {

    private val now = 1_700_000_000_000L
    private val originLat = 49.87275
    private val originLon = 8.65112

    private val spawns = FilterDefinition(
        alertTypes = FilterSelection.only(listOf(FilterAlertType.SPAWN.name))
    )

    private var nextId = 1

    private fun alert(
        name: String,
        latitude: Double = 49.8730,
        longitude: Double = 8.6515,
        endsInMinutes: Long = 60,
        type: String = "Spawn"
    ) = PokemonAlert(
        id = nextId++,
        name = name,
        pokemon = name,
        type = listOf(type),
        latitude = latitude,
        longitude = longitude,
        endTime = Instant.ofEpochMilli(now + endsInMinutes * 60_000L).toString()
    )

    @Test
    fun `candidates are the nearest matches, capped at what the endpoint takes`() {
        val alerts = (0 until 50).map { alert("t$it", 49.8700 + it * 0.0004, 8.6500) }

        val candidates = huntRoutingCandidates(alerts, spawns, emptySet(), originLat, originLon, now)

        assertEquals(HUNT_MATRIX_MAX_TARGETS, candidates.size)
        // Plus the trainer, that is 30 points -- the endpoint's ceiling exactly.
        assertTrue(candidates.size + 1 <= 30)
    }

    @Test
    fun `dismissed, expired and unmatched alerts are not routed`() {
        val kept = alert("kept")
        val dismissed = alert("dismissed")
        val expired = alert("expired", endsInMinutes = -5)
        val wrongType = alert("raid", type = "Raid")
        val alerts = listOf(kept, dismissed, expired, wrongType)

        val candidates = huntRoutingCandidates(
            alerts, spawns, setOf(dismissed.uniqueId), originLat, originLon, now
        )

        assertEquals(listOf(kept.uniqueId), candidates.map { it.id })
    }

    @Test
    fun `an alert with no coordinates is skipped rather than sent as zero`() {
        val placed = alert("placed")
        val unplaced = PokemonAlert(
            id = nextId++,
            name = "unplaced",
            pokemon = "unplaced",
            type = listOf("Spawn"),
            endTime = Instant.ofEpochMilli(now + 60 * 60_000L).toString()
        )

        val candidates = huntRoutingCandidates(
            listOf(placed, unplaced), spawns, emptySet(), originLat, originLon, now
        )

        assertEquals(listOf(placed.uniqueId), candidates.map { it.id })
    }

    @Test
    fun `the candidate list does not depend on any routed cost`() {
        // The loop-breaker, asserted. If this list were ranked by published costs, a
        // snapshot would reorder the targets, which would change the list, which would
        // trigger another prefetch -- a loop with a network call in it. The signature
        // takes no HuntLegCosts at all, and this pins that the answer is stable.
        val alerts = (0 until 12).map { alert("t$it", 49.8700 + it * 0.0006, 8.6500 + it * 0.0006) }

        val first = huntRoutingCandidates(alerts, spawns, emptySet(), originLat, originLon, now)
        val again = huntRoutingCandidates(alerts.reversed(), spawns, emptySet(), originLat, originLon, now)

        assertEquals(first.map { it.id }, again.map { it.id })
    }

    @Test
    fun `moving past the drift threshold is what re-asks the trainer's row`() {
        // ~11 m north: still the same row.
        assertFalse(huntOriginMoved(originLat, originLon, originLat + 0.0001, originLon))
        // ~220 m north: a new row.
        assertTrue(huntOriginMoved(originLat, originLon, originLat + 0.0020, originLon))
    }
}
