package com.example.pokemonalertsv2.data.counters

import com.example.pokemonalertsv2.data.pokegenie.OwnedPokemon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The personal tab used to fire one ~1 MB request per distinct roster level. Tobias's
 * 2404-row export spans 54 of them, which is ~54 MB and well past the point where
 * Pokebattler starts returning 429 — so the tab never finished loading. These tests pin
 * the bucketing that replaced it.
 */
class PersonalLevelPlanTest {

    private fun mon(level: Double?) = OwnedPokemon(
        displayName = "Machamp",
        form = null,
        level = level,
        atkIv = 15,
        defIv = 15,
        staIv = 15,
        cp = 3000,
        quickMove = "Counter",
        chargeMove = "Dynamic Punch",
        shadow = false,
        lucky = false,
        matchKeys = listOf("MACHAMP")
    )

    @Test
    fun `levels below forty are not worth a request`() {
        assertNull(personalLevelBucket(1.0))
        assertNull(personalLevelBucket(30.0))
        assertNull(personalLevelBucket(39.5))
        assertNull(personalLevelBucket(Double.NaN))
    }

    @Test
    fun `forty to fifty snaps to the nearer endpoint and ties round down`() {
        assertEquals(40.0, personalLevelBucket(40.0)!!, 1e-9)
        assertEquals(40.0, personalLevelBucket(44.5)!!, 1e-9)
        // Exactly halfway: never flatter the Pokemon.
        assertEquals(40.0, personalLevelBucket(45.0)!!, 1e-9)
        assertEquals(50.0, personalLevelBucket(45.5)!!, 1e-9)
        assertEquals(50.0, personalLevelBucket(50.0)!!, 1e-9)
    }

    @Test
    fun `best buddies fall back to fifty because Pokebattler will not rank fifty-one`() {
        // levels/51 hangs and the gateway 504s after 30s, while 40 and 50 answer in ~300ms.
        assertEquals(50.0, personalLevelBucket(50.5)!!, 1e-9)
        assertEquals(50.0, personalLevelBucket(51.0)!!, 1e-9)
        assertEquals(50.0, personalLevelBucket(52.0)!!, 1e-9)
        assertEquals(50.0, personalLevelBucket(55.0)!!, 1e-9)
    }

    @Test
    fun `a full sized roster costs a handful of requests, not one per level`() {
        // Mirrors the real export: every half-level from 1 to 50, plus best buddies.
        val roster = buildList {
            var level = 1.0
            while (level <= 50.0) {
                repeat(20) { add(mon(level)) }
                level += 0.5
            }
            repeat(3) { add(mon(51.0)) }
            repeat(2) { add(mon(52.0)) }
            add(mon(null))
        }
        val plan = planPersonalLevels(roster)

        assertEquals(listOf(40.0, 50.0), plan.levels)
        assertTrue(plan.levels.size <= 2 + MAX_ABOVE_MAX_LEVEL_REQUESTS)
        assertEquals(1, plan.skippedWithoutLevel)
        // Everything under 40 is dropped: 78 half-levels x 20 copies.
        assertEquals(78 * 20, plan.skippedBelowMinimum)
        // 40..45 inclusive -> 40; 45.5..50 -> 50; 51/52 -> 51.
        assertEquals(11 * 20, plan.byLevel.getValue(40.0).size)
        // 45.5..50 -> 50, plus the five best buddies folded in.
        assertEquals(10 * 20 + 5, plan.byLevel.getValue(50.0).size)
    }

    @Test
    fun `a roster with nothing above level forty plans no requests`() {
        val plan = planPersonalLevels(listOf(mon(20.0), mon(39.5), mon(null)))
        assertTrue(plan.levels.isEmpty())
        assertEquals(2, plan.skippedBelowMinimum)
        assertEquals(1, plan.skippedWithoutLevel)
    }

    @Test
    fun `above-fifty levels never add a request`() {
        val roster = (0..20).map { mon(50.5 + it * 0.5) }
        val plan = planPersonalLevels(roster)
        assertEquals(listOf(50.0), plan.levels)
        assertEquals(roster.size, plan.byLevel.getValue(50.0).size)
    }

    @Test
    fun `every planned level is one Pokebattler actually answers`() {
        assertEquals("levels/40", AttackerSpec.ExactLevel(40.0).pathSegment)
        assertEquals("levels/50", AttackerSpec.ExactLevel(50.0).pathSegment)
    }
}
