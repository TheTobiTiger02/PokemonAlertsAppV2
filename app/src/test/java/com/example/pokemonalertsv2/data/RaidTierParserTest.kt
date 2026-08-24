package com.example.pokemonalertsv2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RaidTierParserTest {

    @Test
    fun `parses bare numeric tiers`() {
        assertEquals(RaidTier.TIER_1, RaidTierParser.parse(listOf("Raid", "1")))
        assertEquals(RaidTier.TIER_3, RaidTierParser.parse(listOf("Raid", "3")))
        assertEquals(RaidTier.TIER_5, RaidTierParser.parse(listOf("Raid", "5")))
    }

    @Test
    fun `parses mega`() {
        assertEquals(RaidTier.MEGA, RaidTierParser.parse(listOf("Raid", "Mega")))
    }

    @Test
    fun `mega plus five is mega legendary not tier five`() {
        assertEquals(RaidTier.MEGA_LEGENDARY, RaidTierParser.parse(listOf("Raid", "Mega", "5")))
        assertEquals(RaidTier.MEGA_LEGENDARY, RaidTierParser.parse(listOf("Raid", "Mega Legendary")))
    }

    @Test
    fun `parses elite and primal which the notifier regex misses`() {
        assertEquals(RaidTier.ELITE, RaidTierParser.parse(listOf("Raid", "Elite")))
        assertEquals(RaidTier.PRIMAL, RaidTierParser.parse(listOf("Raid", "Primal")))
    }

    @Test
    fun `parses ultra beast and shadow five`() {
        assertEquals(RaidTier.ULTRA_BEAST, RaidTierParser.parse(listOf("Raid", "Ultra Beast")))
        assertEquals(RaidTier.SHADOW_5, RaidTierParser.parse(listOf("Raid", "Shadow", "5")))
    }

    @Test
    fun `falls back to the alert name`() {
        assertEquals(RaidTier.TIER_5, RaidTierParser.parse(listOf("Raid"), "Tier 5 Raid"))
        assertEquals(RaidTier.TIER_5, RaidTierParser.parse(listOf("Raid"), "5-Star Raid"))
        assertEquals(RaidTier.TIER_3, RaidTierParser.parse(listOf("Raid"), "T3 Raid - Machamp"))
    }

    @Test
    fun `returns null when there is no tier`() {
        assertNull(RaidTierParser.parse(listOf("Raid")))
        assertNull(RaidTierParser.parse(listOf("Hundo")))
        assertNull(RaidTierParser.parse(null))
        assertNull(RaidTierParser.parse(emptyList()))
    }

    @Test
    fun `ignores case and punctuation`() {
        assertEquals(RaidTier.MEGA, RaidTierParser.parse(listOf("RAID", "  mega  ")))
        assertEquals(RaidTier.TIER_5, RaidTierParser.parse(listOf("Raid", "Tier-5")))
    }

    @Test
    fun `isRaid detects raids from type or name`() {
        assertTrue(RaidTierParser.isRaid(listOf("Raid", "5")))
        assertTrue(RaidTierParser.isRaid(listOf("raid")))
        assertTrue(RaidTierParser.isRaid(emptyList(), "Terrakion Raid"))
        assertFalse(RaidTierParser.isRaid(listOf("Hundo", "PvP")))
        assertFalse(RaidTierParser.isRaid(null, "100% Togetic"))
    }

    @Test
    fun `maps catalogue raid levels back to tiers ignoring future and legacy suffixes`() {
        assertEquals(RaidTier.TIER_5, RaidTier.fromPokebattlerRaidLevel("RAID_LEVEL_5"))
        assertEquals(RaidTier.TIER_5, RaidTier.fromPokebattlerRaidLevel("RAID_LEVEL_5_FUTURE"))
        assertEquals(RaidTier.TIER_3, RaidTier.fromPokebattlerRaidLevel("RAID_LEVEL_3_LEGACY"))
        assertEquals(RaidTier.MEGA, RaidTier.fromPokebattlerRaidLevel("RAID_LEVEL_MEGA_LEGACY"))
        assertNull(RaidTier.fromPokebattlerRaidLevel("RAID_LEVEL_UNSET"))
        assertNull(RaidTier.fromPokebattlerRaidLevel(null))
    }
}
