package com.example.pokemonalertsv2.data.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CpmTableTest {

    @Test
    fun `matches the published anchor values`() {
        assertEquals(0.094, CpmTable.forLevel(1.0), 1e-9)
        assertEquals(0.5974, CpmTable.forLevel(20.0), 1e-4)
        assertEquals(0.7317, CpmTable.forLevel(30.0), 1e-4)
        assertEquals(0.7903, CpmTable.forLevel(40.0), 1e-4)
        assertEquals(0.84029999, CpmTable.forLevel(50.0), 1e-8)
        assertEquals(0.84529999, CpmTable.forLevel(51.0), 1e-8)
    }

    @Test
    fun `snaps to half levels and clamps`() {
        assertEquals(20.5, CpmTable.snap(20.4), 1e-9)
        assertEquals(20.5, CpmTable.snap(20.6), 1e-9)
        assertEquals(1.0, CpmTable.snap(-5.0), 1e-9)
        assertEquals(51.0, CpmTable.snap(99.0), 1e-9)
        assertEquals(CpmTable.forLevel(51.0), CpmTable.forLevel(80.0), 1e-9)
    }

    @Test
    fun `is monotonic across the whole range`() {
        var previous = 0.0
        var level = 1.0
        while (level <= 51.0) {
            val cpm = CpmTable.forLevel(level)
            assertTrue("cpm must increase at level $level", cpm > previous)
            previous = cpm
            level += 0.5
        }
    }
}

class TypeChartTest {

    @Test
    fun `uses the GO multipliers`() {
        assertEquals(1.6, TypeChart.singleEffectiveness(PokemonType.WATER, PokemonType.FIRE), 1e-9)
        assertEquals(0.625, TypeChart.singleEffectiveness(PokemonType.FIRE, PokemonType.WATER), 1e-9)
        // GO has no immunities; a main-series immunity is a double resistance.
        assertEquals(0.390625, TypeChart.singleEffectiveness(PokemonType.NORMAL, PokemonType.GHOST), 1e-9)
        assertEquals(0.390625, TypeChart.singleEffectiveness(PokemonType.ELECTRIC, PokemonType.GROUND), 1e-9)
        assertEquals(1.0, TypeChart.singleEffectiveness(PokemonType.NORMAL, PokemonType.WATER), 1e-9)
    }

    @Test
    fun `stacks both defender types`() {
        // Rock hits a Flying/Bug defender twice as hard.
        assertEquals(
            1.6 * 1.6,
            TypeChart.effectiveness(PokemonType.ROCK, listOf(PokemonType.FLYING, PokemonType.BUG)),
            1e-9
        )
        // Fighting into Rock/Fairy cancels out.
        assertEquals(
            1.0,
            TypeChart.effectiveness(PokemonType.FIGHTING, listOf(PokemonType.ROCK, PokemonType.FAIRY)),
            1e-9
        )
    }

    @Test
    fun `parses api type ids`() {
        assertEquals(PokemonType.DARK, PokemonType.fromApiValue("POKEMON_TYPE_DARK"))
        assertEquals(null, PokemonType.fromApiValue("POKEMON_TYPE_LIGHT"))
        assertEquals(null, PokemonType.fromApiValue(null))
    }
}

class RaidSimulatorTest {

    private val tyranitar = SimSpecies(
        pokemonId = "TYRANITAR",
        baseAttack = 251,
        baseDefense = 207,
        baseStamina = 225,
        types = listOf(PokemonType.ROCK, PokemonType.DARK)
    )
    private val lunala = SimSpecies(
        pokemonId = "LUNALA",
        baseAttack = 264,
        baseDefense = 190,
        baseStamina = 293,
        types = listOf(PokemonType.PSYCHIC, PokemonType.GHOST)
    )

    private val smackDown = SimMove("SMACK_DOWN_FAST", PokemonType.ROCK, 13.0, 1.0, 7)
    private val stoneEdge = SimMove("STONE_EDGE", PokemonType.ROCK, 105.0, 2.5, -100)
    private val bite = SimMove("BITE_FAST", PokemonType.DARK, 6.0, 0.5, 4)
    private val crunch = SimMove("CRUNCH", PokemonType.DARK, 70.0, 3.2, -33)

    private fun boss(fast: SimMove? = null, charged: SimMove? = null) = SimBoss(
        species = lunala,
        cpm = 0.79,
        hp = 15000,
        fastMove = fast,
        chargedMove = charged,
        combatTimeSeconds = 300.0
    )

    private fun attacker(
        level: Double = 40.0,
        atk: Int = 15,
        def: Int = 15,
        sta: Int = 15,
        fast: SimMove = bite,
        charged: SimMove = crunch,
        shadow: Boolean = false
    ) = SimAttacker(tyranitar, level, atk, def, sta, fast, charged, shadow)

    @Test
    fun `stats follow the base plus iv times cpm formula`() {
        // Level 40 hundo Tyranitar: (251+15) * 0.7903 = 210.2
        assertEquals(210.2, RaidSimulator.attackStat(tyranitar, 40.0, 15, shadow = false), 0.1)
        assertEquals(175.4, RaidSimulator.defenseStat(tyranitar, 40.0, 15, shadow = false), 0.1)
        // Stamina floors.
        assertEquals(189, RaidSimulator.staminaStat(tyranitar, 40.0, 15))
    }

    @Test
    fun `shadow trades defence for attack`() {
        val normalAtk = RaidSimulator.attackStat(tyranitar, 40.0, 15, shadow = false)
        val shadowAtk = RaidSimulator.attackStat(tyranitar, 40.0, 15, shadow = true)
        assertEquals(normalAtk * 1.2, shadowAtk, 1e-6)

        val normalDef = RaidSimulator.defenseStat(tyranitar, 40.0, 15, shadow = false)
        val shadowDef = RaidSimulator.defenseStat(tyranitar, 40.0, 15, shadow = true)
        assertTrue(shadowDef < normalDef)
    }

    @Test
    fun `damage applies stab and type effectiveness`() {
        val atk = 200.0
        val def = 150.0
        // Dark into Psychic/Ghost is doubly super effective, and Dark is STAB for Tyranitar.
        val superEffective = RaidSimulator.damagePerHit(
            crunch, atk, def, tyranitar.types, lunala.types
        )
        // Rock into Psychic/Ghost is neutral, and also STAB.
        val neutral = RaidSimulator.damagePerHit(
            stoneEdge, atk, def, tyranitar.types, lunala.types
        )
        assertTrue("dark should out-damage rock here", superEffective > neutral)

        // Damage is never zero.
        val tiny = SimMove("TINY", PokemonType.NORMAL, 0.0, 1.0, 5)
        assertEquals(1, RaidSimulator.damagePerHit(tiny, 1.0, 10_000.0, emptyList(), emptyList()))
    }

    @Test
    fun `cycle dps counts the fast moves needed to pay for the charged move`() {
        // 100 energy at 7 per fast move needs 15 fast moves.
        val dps = RaidSimulator.cycleDps(
            fastDamage = 10, fastDuration = 1.0, fastEnergy = 7,
            chargedDamage = 150, chargedDuration = 2.5, chargedCost = 100
        )
        val expected = (15 * 10 + 150) / (15 * 1.0 + 2.5)
        assertEquals(expected, dps, 1e-9)
    }

    @Test
    fun `a charged move that can never be afforded degrades to fast only`() {
        val dps = RaidSimulator.cycleDps(
            fastDamage = 10, fastDuration = 1.0, fastEnergy = 0,
            chargedDamage = 150, chargedDuration = 2.5, chargedCost = 100
        )
        assertEquals(10.0, dps, 1e-9)
    }

    @Test
    fun `higher level beats lower level for the same pokemon`() {
        val low = RaidSimulator.simulate(attacker(level = 20.0), boss())
        val high = RaidSimulator.simulate(attacker(level = 50.0), boss())
        assertTrue("level 50 must outrank level 20", high.rating > low.rating)
        assertTrue(high.dps > low.dps)
    }

    @Test
    fun `better ivs beat worse ivs`() {
        val hundo = RaidSimulator.simulate(attacker(atk = 15, def = 15, sta = 15), boss())
        val nundo = RaidSimulator.simulate(attacker(atk = 0, def = 0, sta = 0), boss())
        assertTrue(hundo.rating > nundo.rating)
    }

    @Test
    fun `the right moveset beats the wrong one`() {
        // Dark is doubly super effective on Lunala; Rock is neutral.
        val dark = RaidSimulator.simulate(attacker(fast = bite, charged = crunch), boss())
        val rock = RaidSimulator.simulate(attacker(fast = smackDown, charged = stoneEdge), boss())
        assertTrue(
            "dark moveset ${dark.dps} should beat rock moveset ${rock.dps}",
            dark.dps > rock.dps
        )
    }

    @Test
    fun `weather boost raises damage`() {
        val plain = RaidSimulator.simulate(attacker(fast = bite, charged = crunch), boss())
        val foggy = RaidSimulator.simulate(attacker(fast = bite, charged = crunch), boss(), WeatherBoost.FOG)
        assertTrue("fog boosts dark moves", foggy.dps > plain.dps)
    }

    @Test
    fun `dodging extends survival`() {
        val bossWithMoves = boss(
            fast = SimMove("CONFUSION_FAST", PokemonType.PSYCHIC, 20.0, 1.6, 15),
            charged = SimMove("SHADOW_BALL", PokemonType.GHOST, 100.0, 3.0, -50)
        )
        val noDodge = RaidSimulator.simulate(attacker(), bossWithMoves, dodgeFraction = 0.0)
        val dodging = RaidSimulator.simulate(attacker(), bossWithMoves, dodgeFraction = 0.75)
        assertTrue(dodging.survivalSeconds > noDodge.survivalSeconds)
        assertTrue(dodging.tdo > noDodge.tdo)
    }

    @Test
    fun `survival never exceeds the raid timer`() {
        val result = RaidSimulator.simulate(attacker(), boss())
        assertTrue(result.survivalSeconds <= 300.0)
    }

    @Test
    fun `estimated attackers scales with boss hp`() {
        val result = RaidSimulator.simulate(attacker(), boss())
        assertEquals(15000 / result.tdo, result.estimatedAttackers, 1e-6)
    }
}

class TeamBuilderTest {

    private val tyranitar = SimSpecies("TYRANITAR", 251, 207, 225, listOf(PokemonType.ROCK, PokemonType.DARK))
    private val magikarp = SimSpecies("MAGIKARP", 29, 85, 85, listOf(PokemonType.WATER))
    private val bite = SimMove("BITE_FAST", PokemonType.DARK, 6.0, 0.5, 4)
    private val crunch = SimMove("CRUNCH", PokemonType.DARK, 70.0, 3.2, -33)
    private val splash = SimMove("SPLASH_FAST", PokemonType.WATER, 0.0, 1.23, 0)

    private val boss = SimBoss(
        species = SimSpecies("LUNALA", 264, 190, 293, listOf(PokemonType.PSYCHIC, PokemonType.GHOST)),
        cpm = 0.79, hp = 15000, fastMove = null, chargedMove = null, combatTimeSeconds = 300.0
    )

    private fun owned(id: String, species: SimSpecies, level: Double, shadow: Boolean = false) =
        id to SimAttacker(species, level, 15, 15, 15, if (species == magikarp) splash else bite,
            if (species == magikarp) splash else crunch, shadow)

    @Test
    fun `ranks the strongest first`() {
        val ranked = TeamBuilder.rank(
            listOf(owned("karp", magikarp, 40.0), owned("ttar", tyranitar, 40.0)),
            boss
        )
        assertEquals("ttar", ranked.first().owned)
    }

    @Test
    fun `a team is six and may repeat the same species`() {
        // Eight copies of the same strong Pokemon: the team should be six of them.
        val candidates = (1..8).map { owned("ttar$it", tyranitar, 40.0) }
        val ranked = TeamBuilder.rank(candidates, boss)
        val team = TeamBuilder.buildTeam(ranked, boss)
        assertEquals(6, team.members.size)
        assertEquals(6, team.members.map { it.owned }.distinct().size)
        assertTrue(team.members.all { it.attacker.species.pokemonId == "TYRANITAR" })
    }

    @Test
    fun `a team may contain at most one mega`() {
        // Only one Mega Evolution can be active at a time, so six megas is not a real team.
        val mega = SimSpecies("TYRANITAR_MEGA", 309, 276, 225, listOf(PokemonType.ROCK, PokemonType.DARK))
        val candidates = (1..6).map { owned("mega$it", mega, 40.0) } +
            (1..6).map { owned("ttar$it", tyranitar, 40.0) }
        val team = TeamBuilder.buildTeam(TeamBuilder.rank(candidates, boss), boss)
        assertEquals(6, team.members.size)
        assertEquals(
            1,
            team.members.count { it.attacker.species.pokemonId.contains("_MEGA") }
        )
    }

    @Test
    fun `primals occupy the same slot as megas`() {
        val megaTtar = SimSpecies("TYRANITAR_MEGA", 309, 276, 225, listOf(PokemonType.ROCK, PokemonType.DARK))
        val primal = SimSpecies("GROUDON_PRIMAL", 353, 268, 218, listOf(PokemonType.ROCK, PokemonType.DARK))
        val candidates = listOf(owned("mega", megaTtar, 40.0), owned("primal", primal, 40.0)) +
            (1..6).map { owned("ttar$it", tyranitar, 40.0) }
        val team = TeamBuilder.buildTeam(TeamBuilder.rank(candidates, boss), boss)
        assertEquals(
            1,
            team.members.count {
                val id = it.attacker.species.pokemonId
                id.contains("_MEGA") || id.contains("_PRIMAL")
            }
        )
    }

    @Test
    fun `never suggests the same copy twice`() {
        val ranked = TeamBuilder.rank(listOf(owned("only", tyranitar, 40.0)), boss)
        val team = TeamBuilder.buildTeam(ranked, boss)
        assertEquals(1, team.members.size)
    }

    @Test
    fun `groups identical copies with a count`() {
        val candidates = (1..5).map { owned("ttar$it", tyranitar, 40.0) } +
            listOf(owned("karp", magikarp, 40.0))
        val ranked = TeamBuilder.rank(candidates, boss)
        val groups = TeamBuilder.groupTeam(TeamBuilder.buildTeam(ranked, boss).members)
        assertEquals(2, groups.size)
        assertEquals("TYRANITAR", groups.first().representative.attacker.species.pokemonId)
        assertEquals(5, groups.first().count)
        assertEquals(1, groups.last().count)
    }

    @Test
    fun `higher level copies are preferred over lower ones`() {
        val candidates = listOf(
            owned("low", tyranitar, 20.0),
            owned("mid", tyranitar, 35.0),
            owned("high", tyranitar, 50.0)
        )
        val ranked = TeamBuilder.rank(candidates, boss)
        assertEquals(listOf("high", "mid", "low"), ranked.map { it.owned })
    }

    @Test
    fun `reports how much of the boss the team removes`() {
        val ranked = TeamBuilder.rank((1..6).map { owned("ttar$it", tyranitar, 40.0) }, boss)
        val team = TeamBuilder.buildTeam(ranked, boss)
        assertEquals(team.members.sumOf { it.result.tdo }, team.combinedTdo, 1e-9)
        assertTrue(team.bossFraction > 0.0 && team.bossFraction <= 1.0)
    }
}
