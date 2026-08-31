package com.example.pokemonalertsv2.data.counters

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentRaidBossFilterTest {

    @Test
    fun `picker keeps only current queryable raid tiers`() {
        assertTrue(isCurrentRaidTier("RAID_LEVEL_5"))
        assertTrue(isCurrentRaidTier("RAID_LEVEL_MEGA"))
        assertTrue(isCurrentRaidTier("RAID_LEVEL_5_SHADOW"))
        assertFalse(isCurrentRaidTier("RAID_LEVEL_5_FUTURE"))
        assertFalse(isCurrentRaidTier("RAID_LEVEL_5_LEGACY"))
        assertFalse(isCurrentRaidTier("RAID_LEVEL_1_MAX"))
        assertFalse(isCurrentRaidTier("RAID_LEVEL_UNSET"))
    }
}
