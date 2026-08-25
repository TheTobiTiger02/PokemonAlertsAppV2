package com.example.pokemonalertsv2.data.counters

import org.junit.Assert.assertEquals
import org.junit.Test

private const val LEGENDARY = "POKEMON_RARITY_LEGENDARY"
private const val MYTHIC = "POKEMON_RARITY_MYTHIC"
private const val ULTRA_BEAST = "POKEMON_RARITY_ULTRA_BEAST"

class RaidTierFallbackTest {

    @Test
    fun `legendary and mythic bosses fall back to tier five`() {
        assertEquals("RAID_LEVEL_5", fallbackRaidLevel("SOLGALEO", LEGENDARY))
        assertEquals("RAID_LEVEL_5", fallbackRaidLevel("MEW", MYTHIC))
    }

    @Test
    fun `ultra beasts fall back to their own tier`() {
        assertEquals("RAID_LEVEL_ULTRA_BEAST", fallbackRaidLevel("PHEROMOSA", ULTRA_BEAST))
    }

    @Test
    fun `primal and mega legendary use the mega legendary tier`() {
        assertEquals("RAID_LEVEL_MEGA_5", fallbackRaidLevel("GROUDON_PRIMAL", LEGENDARY))
        assertEquals("RAID_LEVEL_MEGA_5", fallbackRaidLevel("MEWTWO_MEGA_Y", LEGENDARY))
    }

    @Test
    fun `an ordinary mega uses the mega tier`() {
        assertEquals("RAID_LEVEL_MEGA", fallbackRaidLevel("SWAMPERT_MEGA", null))
    }

    @Test
    fun `shadow bosses use the shadow tier where one exists`() {
        // The 22 Shadow Palkia alerts that used to fail outright.
        assertEquals("RAID_LEVEL_5_SHADOW", fallbackRaidLevel("PALKIA_SHADOW_FORM", LEGENDARY))
        assertEquals("RAID_LEVEL_3_SHADOW", fallbackRaidLevel("SNORLAX_SHADOW_FORM", null))
        assertEquals("RAID_LEVEL_1_SHADOW", fallbackRaidLevel("PIKACHU_SHADOW_FORM", null))
        // Ultra beasts have no shadow tier, so the base tier stands.
        assertEquals("RAID_LEVEL_ULTRA_BEAST", fallbackRaidLevel("PHEROMOSA_SHADOW_FORM", ULTRA_BEAST))
    }

    @Test
    fun `costume pikachu falls back to tier one`() {
        // Tier 1 vs tier 3 is a fourfold difference in boss HP, so this is worth splitting out.
        assertEquals("RAID_LEVEL_1", fallbackRaidLevel("PIKACHU_WCS_2025_FORM", null))
        assertEquals("RAID_LEVEL_1", fallbackRaidLevel("PIKACHU", null))
    }

    @Test
    fun `an unknown species falls back to tier three`() {
        assertEquals("RAID_LEVEL_3", fallbackRaidLevel("GENGAR", null))
        assertEquals("RAID_LEVEL_3", fallbackRaidLevel("POLTCHAGEIST_COUNTERFEIT_FORM", null))
    }

    @Test
    fun `never returns the unset bucket`() {
        listOf("DITTO", "SOLGALEO", "PIKACHU_WCS_2022_FORM", "GROUDON_PRIMAL").forEach {
            assert(fallbackRaidLevel(it, null) != "RAID_LEVEL_UNSET")
        }
    }
}
