package com.example.pokemonalertsv2.raidwatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json

class RaidTeamSnapshotTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a snapshot survives the round trip through the store`() {
        val snapshot = RaidTeamSnapshot(
            alertUniqueId = "Mewtwo Raid|2026-08-30T12:30:00Z",
            members = listOf(
                RaidTeamMember("Shadow Mamoswine", 2, "Powder Snow", "Avalanche"),
                RaidTeamMember("Mega Rayquaza", 2, "Dragon Tail", "Breaking Swipe")
            ),
            goQuery = "473&CP3243,384&CP4499",
            speciesQuery = "473,384",
            computedAtMillis = 1_700_000_000_000L
        )

        val restored = json.decodeFromString(
            RaidTeamSnapshot.serializer(),
            json.encodeToString(RaidTeamSnapshot.serializer(), snapshot)
        )

        assertEquals(snapshot, restored)
    }

    @Test
    fun `an older snapshot without the newer fields still decodes`() {
        // The store keeps whatever was written before an upgrade; a decode failure there
        // would silently drop the team rather than refresh it.
        val restored = json.decodeFromString(
            RaidTeamSnapshot.serializer(),
            """{"alertUniqueId":"id"}"""
        )

        assertEquals("id", restored.alertUniqueId)
        assertFalse(restored.hasTeam)
    }

    @Test
    fun `a snapshot with members but no query is not copyable`() {
        val snapshot = RaidTeamSnapshot(
            alertUniqueId = "id",
            members = listOf(RaidTeamMember("Mamoswine", 2, "Powder Snow", "Avalanche"))
        )

        // The Copy action has nothing to write, so the notification must not offer it.
        assertFalse(snapshot.hasTeam)
    }

    @Test
    fun `a complete snapshot is copyable`() {
        val snapshot = RaidTeamSnapshot(
            alertUniqueId = "id",
            members = listOf(RaidTeamMember("Mamoswine", 2, "Powder Snow", "Avalanche")),
            goQuery = "473&CP3243"
        )

        assertTrue(snapshot.hasTeam)
    }

    @Test
    fun `an exact query short enough to paste is what gets copied`() {
        val snapshot = RaidTeamSnapshot(
            alertUniqueId = "id",
            members = listOf(RaidTeamMember("Mamoswine", 2, "Powder Snow", "Avalanche")),
            goQuery = "473&CP3243",
            speciesQuery = "473"
        )

        assertFalse(snapshot.exactQueryTooLong)
        assertEquals("473&CP3243", snapshot.clipboardQuery)
    }

    @Test
    fun `a combinatorially exploded query falls back to the species list`() {
        // teamQuery distributes OR-of-AND terms because GO has no parentheses, so a full six
        // with species, shadow, CP and both moves runs to hundreds of kilobytes. Copying that
        // gives the trainer something they cannot paste.
        val snapshot = RaidTeamSnapshot(
            alertUniqueId = "id",
            members = listOf(RaidTeamMember("Tyranitar", 5, "Bite", "Brutal Swing")),
            goQuery = "x".repeat(RaidTeamSnapshot.MAX_EXACT_QUERY_CHARS + 1),
            speciesQuery = "248,800"
        )

        assertTrue(snapshot.exactQueryTooLong)
        assertEquals("248,800", snapshot.clipboardQuery)
        // Still copyable, just coarser.
        assertTrue(snapshot.hasTeam)
    }

    @Test
    fun `an oversized query with no species fallback is not copyable`() {
        val snapshot = RaidTeamSnapshot(
            alertUniqueId = "id",
            members = listOf(RaidTeamMember("Tyranitar", 5, "Bite", "Brutal Swing")),
            goQuery = "x".repeat(RaidTeamSnapshot.MAX_EXACT_QUERY_CHARS + 1)
        )

        // Nothing to fall back to, so the exact query stands rather than copying nothing.
        assertFalse(snapshot.exactQueryTooLong)
        assertTrue(snapshot.hasTeam)
    }

    @Test
    fun `a member renders both moves on one line`() {
        val member = RaidTeamMember("Mamoswine", 2, "Powder Snow", "Avalanche")

        assertEquals("Powder Snow / Avalanche", member.moveLine)
    }
}
