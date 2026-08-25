package com.example.pokemonalertsv2.data.counters

import com.example.pokemonalertsv2.data.pokegenie.OwnedPokemon
import com.example.pokemonalertsv2.data.sim.PokemonType
import com.example.pokemonalertsv2.data.sim.SimBoss
import com.example.pokemonalertsv2.data.sim.SimMove
import com.example.pokemonalertsv2.data.sim.SimSpecies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalCounterEngineTest {

    private val tyranitar = SimSpecies("TYRANITAR", 251, 207, 225, listOf(PokemonType.ROCK, PokemonType.DARK))
    private val tyranitarShadow = SimSpecies("TYRANITAR_SHADOW_FORM", 251, 207, 225, listOf(PokemonType.ROCK, PokemonType.DARK))
    private val magikarp = SimSpecies("MAGIKARP", 29, 85, 85, listOf(PokemonType.WATER))

    private val bite = SimMove("BITE_FAST", PokemonType.DARK, 6.0, 0.5, 4)
    private val smackDown = SimMove("SMACK_DOWN_FAST", PokemonType.ROCK, 13.0, 1.0, 7)
    private val crunch = SimMove("CRUNCH", PokemonType.DARK, 70.0, 3.2, -33)
    private val stoneEdge = SimMove("STONE_EDGE", PokemonType.ROCK, 105.0, 2.5, -100)
    private val splash = SimMove("SPLASH_FAST", PokemonType.WATER, 0.0, 1.23, 4)
    private val struggle = SimMove("STRUGGLE", PokemonType.NORMAL, 35.0, 2.2, -33)

    private val speciesMap = listOf(tyranitar, tyranitarShadow, magikarp).associateBy { it.pokemonId }
    private val moveMap = listOf(bite, smackDown, crunch, stoneEdge, splash, struggle).associateBy { it.moveId }
    private val legalMoves = mapOf(
        "TYRANITAR" to (listOf("BITE_FAST", "SMACK_DOWN_FAST") to listOf("CRUNCH", "STONE_EDGE")),
        "TYRANITAR_SHADOW_FORM" to (listOf("BITE_FAST", "SMACK_DOWN_FAST") to listOf("CRUNCH", "STONE_EDGE")),
        "MAGIKARP" to (listOf("SPLASH_FAST") to listOf("STRUGGLE"))
    )

    /** Lunala is Psychic/Ghost, so Dark moves are doubly super effective. */
    private val boss = SimBoss(
        species = SimSpecies("LUNALA", 264, 190, 293, listOf(PokemonType.PSYCHIC, PokemonType.GHOST)),
        cpm = 0.79,
        hp = 15000,
        fastMove = SimMove("CONFUSION_FAST", PokemonType.PSYCHIC, 20.0, 1.6, 15),
        chargedMove = SimMove("SHADOW_BALL", PokemonType.GHOST, 100.0, 3.0, -50),
        combatTimeSeconds = 300.0
    )

    private fun owned(
        name: String,
        id: String,
        level: Double = 40.0,
        atk: Int? = 15,
        def: Int? = 15,
        sta: Int? = 15,
        quick: String? = null,
        charge: String? = null,
        shadow: Boolean = false,
        cp: Int? = 3000
    ) = OwnedPokemon(
        displayName = name,
        form = null,
        level = level,
        atkIv = atk,
        defIv = def,
        staIv = sta,
        cp = cp,
        quickMove = quick,
        chargeMove = charge,
        shadow = shadow,
        lucky = false,
        matchKeys = listOf(id)
    )

    private fun rank(vararg mons: OwnedPokemon) = PersonalCounterEngine.rank(
        owned = mons.toList(),
        boss = boss,
        species = speciesMap,
        moves = moveMap,
        legalMoves = legalMoves
    )

    @Test
    fun `uses the recorded moveset when the scan has one`() {
        val result = rank(owned("Tyranitar", "TYRANITAR", quick = "Bite", charge = "Crunch"))
        val counter = result.ranked.single()
        assertEquals("BITE_FAST", counter.fastMove.moveId)
        assertEquals("CRUNCH", counter.chargedMove.moveId)
        assertFalse(counter.movesetAssumed)
    }

    @Test
    fun `matches Poke Genie display names onto move ids`() {
        val result = rank(owned("Tyranitar", "TYRANITAR", quick = "Smack Down", charge = "Stone Edge"))
        val counter = result.ranked.single()
        assertEquals("SMACK_DOWN_FAST", counter.fastMove.moveId)
        assertEquals("STONE_EDGE", counter.chargedMove.moveId)
    }

    @Test
    fun `assumes the best legal moveset when the scan has none`() {
        val result = rank(owned("Tyranitar", "TYRANITAR"))
        val counter = result.ranked.single()
        assertTrue(counter.movesetAssumed)
        // Dark is doubly super effective on Lunala, so it should pick the dark pairing.
        assertEquals("BITE_FAST", counter.fastMove.moveId)
        assertEquals("CRUNCH", counter.chargedMove.moveId)
    }

    @Test
    fun `the recorded moveset is respected even when it is worse`() {
        val recorded = rank(owned("Tyranitar", "TYRANITAR", quick = "Smack Down", charge = "Stone Edge"))
            .ranked.single()
        val assumed = rank(owned("Tyranitar", "TYRANITAR")).ranked.single()
        assertTrue("the assumed best should beat the rock moveset", assumed.dps > recorded.dps)
        assertEquals("STONE_EDGE", recorded.chargedMove.moveId)
    }

    @Test
    fun `level and IVs change the ranking of the same species`() {
        val result = rank(
            owned("Tyranitar", "TYRANITAR", level = 20.0, quick = "Bite", charge = "Crunch"),
            owned("Tyranitar", "TYRANITAR", level = 50.0, quick = "Bite", charge = "Crunch"),
            owned("Tyranitar", "TYRANITAR", level = 50.0, atk = 0, def = 0, sta = 0, quick = "Bite", charge = "Crunch")
        )
        val levels = result.ranked.map { it.owned.level }
        assertEquals(50.0, levels.first()!!, 1e-9)
        assertEquals(15, result.ranked.first().owned.atkIv)
        assertEquals(20.0, levels.last()!!, 1e-9)
    }

    @Test
    fun `shadow beats its non shadow twin`() {
        val result = rank(
            owned("Tyranitar", "TYRANITAR", quick = "Bite", charge = "Crunch"),
            owned("Tyranitar", "TYRANITAR_SHADOW_FORM", quick = "Bite", charge = "Crunch", shadow = true)
        )
        assertTrue(result.ranked.first().owned.shadow)
    }

    @Test
    fun `suggests six and repeats a species the user owns several of`() {
        val mons = (1..8).map { owned("Tyranitar", "TYRANITAR", quick = "Bite", charge = "Crunch") }
        val result = PersonalCounterEngine.rank(mons, boss, speciesMap, moveMap, legalMoves)
        assertEquals(6, result.team.sumOf { it.count })
        // All six are the same Pokemon and moveset, so they collapse into one row of six.
        assertEquals(1, result.team.size)
        assertEquals(6, result.team.single().count)
        assertEquals("Tyranitar", result.team.single().counter.displayName)
    }

    @Test
    fun `a mixed team keeps the strongest first`() {
        val mons = (1..3).map { owned("Tyranitar", "TYRANITAR", quick = "Bite", charge = "Crunch") } +
            (1..4).map { owned("Magikarp", "MAGIKARP") }
        val result = PersonalCounterEngine.rank(mons, boss, speciesMap, moveMap, legalMoves)
        assertEquals(6, result.team.sumOf { it.count })
        assertEquals("Tyranitar", result.team.first().counter.displayName)
        assertEquals(3, result.team.first().count)
        assertEquals("Magikarp", result.team.last().counter.displayName)
        assertEquals(3, result.team.last().count)
    }

    @Test
    fun `reports team damage against boss hp`() {
        val mons = (1..6).map { owned("Tyranitar", "TYRANITAR", quick = "Bite", charge = "Crunch") }
        val result = PersonalCounterEngine.rank(mons, boss, speciesMap, moveMap, legalMoves)
        assertEquals(15000, result.bossHp)
        assertTrue(result.combinedTdo > 0)
        assertTrue(result.bossFraction > 0.0 && result.bossFraction <= 1.0)
    }

    @Test
    fun `skips pokemon the game master does not know`() {
        val unknown = owned("Missingno", "MISSINGNO")
        val result = rank(unknown, owned("Tyranitar", "TYRANITAR", quick = "Bite", charge = "Crunch"))
        assertEquals(1, result.ranked.size)
        assertEquals("Tyranitar", result.ranked.single().displayName)
    }

    @Test
    fun `skips scans with no level`() {
        val result = rank(owned("Tyranitar", "TYRANITAR", level = 0.0).copy(level = null))
        assertTrue(result.ranked.isEmpty())
    }

    @Test
    fun `unappraised IVs still produce a ranking`() {
        val result = rank(owned("Tyranitar", "TYRANITAR", atk = null, def = null, sta = null))
        assertEquals(1, result.ranked.size)
        assertTrue(result.ranked.single().dps > 0)
    }
}
