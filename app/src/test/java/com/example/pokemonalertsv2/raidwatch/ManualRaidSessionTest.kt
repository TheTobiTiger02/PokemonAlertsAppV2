package com.example.pokemonalertsv2.raidwatch

import com.example.pokemonalertsv2.data.counters.AvailableRaidBoss
import com.example.pokemonalertsv2.data.sim.SimSpecies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualRaidSessionTest {

    @Test
    fun `perfect catch cp matches known Mewtwo values`() {
        val cp = perfectCatchCp(
            SimSpecies(
                pokemonId = "MEWTWO",
                baseAttack = 300,
                baseDefense = 182,
                baseStamina = 214,
                types = emptyList()
            )
        )

        assertEquals(2387, cp.level20)
        assertEquals(2984, cp.level25)
    }

    @Test
    fun `manual session carries boss tier hundo and a 45 minute deadline`() {
        val now = 1_800_000_000_000L
        val boss = ManualRaidBoss(
            catalogue = AvailableRaidBoss(
                pokemonId = "MEWTWO",
                displayName = "Mewtwo",
                raidLevel = "RAID_LEVEL_5",
                bossCp = 54148,
                shiny = true
            ),
            tierLabel = "5",
            hundoCP = perfectCatchCp(
                SimSpecies("MEWTWO", 300, 182, 214, emptyList())
            ),
            pokedexId = 150,
            spriteUrls = listOf(
                "https://example.test/mewtwo-primary.png",
                "https://example.test/mewtwo-fallback.png"
            )
        )

        val alert = createManualRaidAlert(boss, now)

        assertEquals(listOf("Raid", "5"), alert.type)
        assertEquals("Mewtwo", alert.pokemon)
        assertEquals(2387, alert.hundoCP?.level20)
        assertEquals((now + MANUAL_RAID_DURATION_MILLIS).toString(), alert.endTime)
        assertEquals("https://example.test/mewtwo-primary.png", alert.imageUrl)
        assertTrue(alert.name.startsWith("Manual raid"))
    }

    @Test
    fun `catch species id collapses mega and primal bosses onto the base form`() {
        assertEquals("CHARIZARD", catchSpeciesId("CHARIZARD_MEGA_X"))
        assertEquals("CHARIZARD", catchSpeciesId("CHARIZARD_MEGA_Y"))
        assertEquals("KYOGRE", catchSpeciesId("KYOGRE_PRIMAL"))
        assertEquals("MEWTWO", catchSpeciesId("MEWTWO"))
        assertEquals("GENGAR", catchSpeciesId("GENGAR_MEGA"))
    }

    @Test
    fun `primal catalogue ids get a primal label`() {
        val boss = AvailableRaidBoss(
            pokemonId = "GROUDON_PRIMAL",
            displayName = "Primal Groudon",
            raidLevel = "RAID_LEVEL_MEGA_5",
            bossCp = null,
            shiny = true
        )

        assertEquals("Primal", manualRaidTierLabel(boss))
    }
}
