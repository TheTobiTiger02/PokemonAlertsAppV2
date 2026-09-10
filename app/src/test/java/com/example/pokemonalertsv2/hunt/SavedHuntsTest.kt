package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.FilterSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedHuntsTest {

    private val now = 1_700_000_000_000L

    private fun hunt(vararg grunts: String) = FilterDefinition(
        alertTypes = FilterSelection.only(listOf(FilterAlertType.ROCKET.name)),
        rocketTypes = if (grunts.isEmpty()) FilterSelection.All else FilterSelection.only(grunts.toList())
    )

    @Test
    fun `starting the same hunt twice leaves one row`() {
        val (first, _) = recordStartedHunt(emptyList(), "Dragon grunts", hunt("Dragon"), now, id = "a")
        val (second, row) = recordStartedHunt(first, "Dragon grunts", hunt("Dragon"), now + 1_000L, id = "b")

        assertEquals(1, second.size)
        // Touched, not replaced: the original id is what a running session points at.
        assertEquals("a", row.id)
        assertEquals(now + 1_000L, row.lastUsedAtMillis)
        assertEquals(now, row.savedAtMillis)
    }

    @Test
    fun `a different hunt gets its own row`() {
        val (first, _) = recordStartedHunt(emptyList(), "Dragon grunts", hunt("Dragon"), now, id = "a")
        val (second, row) = recordStartedHunt(first, "Water grunts", hunt("Water"), now + 1_000L, id = "b")

        assertEquals(2, second.size)
        assertEquals("b", row.id)
    }

    @Test
    fun `the list reads most recently used first`() {
        var list = recordStartedHunt(emptyList(), "Dragon", hunt("Dragon"), now, id = "a").first
        list = recordStartedHunt(list, "Water", hunt("Water"), now + 1_000L, id = "b").first
        list = recordStartedHunt(list, "Dragon", hunt("Dragon"), now + 2_000L, id = "a2").first

        assertEquals(listOf("a", "b"), list.map { it.id })
    }

    @Test
    fun `the oldest hunt falls off the end`() {
        var list = emptyList<SavedHunt>()
        repeat(MAX_SAVED_HUNTS + 1) { index ->
            list = recordStartedHunt(
                existing = list,
                name = "Hunt $index",
                definition = hunt("Grunt$index"),
                nowMillis = now + index * 1_000L,
                id = "id$index"
            ).first
        }

        assertEquals(MAX_SAVED_HUNTS, list.size)
        assertTrue(list.none { it.id == "id0" })
        assertTrue(list.any { it.id == "id${MAX_SAVED_HUNTS}" })
    }

    @Test
    fun `an edited hunt overwrites the row it was opened from`() {
        val (first, original) = recordStartedHunt(emptyList(), "Dragon grunts", hunt("Dragon"), now, id = "a")
        val (second, row) = recordStartedHunt(
            existing = first,
            name = "Dragon and Water grunts",
            definition = hunt("Dragon", "Water"),
            nowMillis = now + 1_000L,
            id = "b",
            replacingId = original.id
        )

        assertEquals(1, second.size)
        assertEquals("a", row.id)
        assertEquals("Dragon and Water grunts", row.name)
        assertNotEquals(original.definition, row.definition)
    }

    @Test
    fun `renaming leaves the definition alone and avoids a collision`() {
        var list = recordStartedHunt(emptyList(), "Dragon grunts", hunt("Dragon"), now, id = "a").first
        list = recordStartedHunt(list, "Water grunts", hunt("Water"), now + 1_000L, id = "b").first

        val renamed = renameSavedHunt(list, "b", "Dragon grunts")

        assertEquals("Dragon grunts (2)", renamed.first { it.id == "b" }.name)
        assertEquals(hunt("Water"), renamed.first { it.id == "b" }.definition)
    }

    @Test
    fun `a unique name never grows past the profile name cap`() {
        val long = "x".repeat(60)
        val existing = listOf(SavedHunt("a", long.take(40), hunt("Dragon"), now))

        val unique = uniqueHuntName(long, existing)

        assertTrue(unique.length <= 40)
        assertNotEquals(existing.first().name, unique)
    }

    @Test
    fun `rename and delete no-op on an id that is not there`() {
        val list = recordStartedHunt(emptyList(), "Dragon grunts", hunt("Dragon"), now, id = "a").first

        assertEquals(list, renameSavedHunt(list, "missing", "Anything"))
        assertEquals(list, removeSavedHunt(list, "missing"))
    }

    @Test
    fun `deleting removes only that hunt`() {
        var list = recordStartedHunt(emptyList(), "Dragon", hunt("Dragon"), now, id = "a").first
        list = recordStartedHunt(list, "Water", hunt("Water"), now + 1_000L, id = "b").first

        assertEquals(listOf("b"), removeSavedHunt(list, "a").map { it.id })
    }
}
