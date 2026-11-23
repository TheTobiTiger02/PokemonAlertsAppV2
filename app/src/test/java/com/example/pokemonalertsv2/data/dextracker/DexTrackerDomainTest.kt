package com.example.pokemonalertsv2.data.dextracker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Mirrors Dex Tracker's own rules (PokemonGoChecklist src/domain.ts): actionPatch for
 * `{ type: "add", pool: "main", delta: 1 }`, credited() and describeChange() for that change.
 */
class DexTrackerDomainTest {
    private val bulbasaurLine = DexTrackerEntry(
        id = "e1", pokemonId = 1, name = "Bulbasaur", target = 3,
        owned = mapOf("1" to 1), unassigned = 0, order = 0
    )

    @Test
    fun plusOneAddsAnUnassignedCopyAndCountsItTowardsTheTarget() {
        val change = DexTrackerDomain.plusOne(bulbasaurLine, at = 1_000, eventId = "ev", day = "2026-11-23")
        assertEquals(mapOf("unassigned" to 1L, "revision" to 1L), change.increments)
        assertEquals(1, change.gained)
        assertEquals(
            mapOf(
                "id" to "ev", "at" to 1_000L, "entryId" to "e1", "entryName" to "Bulbasaur",
                "pokemonId" to 1, "kind" to "caught", "count" to 1, "change" to 1
            ),
            change.event
        )
    }

    @Test
    fun aCopyBeyondTheTargetGainsNothing() {
        val full = bulbasaurLine.copy(owned = mapOf("1" to 1, "2" to 1, "3" to 1))
        val change = DexTrackerDomain.plusOne(full, at = 1, eventId = "ev")
        assertEquals(0, change.gained)
        assertEquals(0, change.event["change"])
    }

    @Test
    fun progressCountsCreditedCopiesAgainstTargets() {
        val entries = listOf(bulbasaurLine, bulbasaurLine.copy(id = "e2", target = 1, owned = emptyMap(), unassigned = 4))
        assertEquals(2 to 4, DexTrackerDomain.progress(entries))
        assertEquals("2026-03-07", DexTrackerDomain.dayKey(LocalDate.of(2026, 3, 7)))
    }
}
