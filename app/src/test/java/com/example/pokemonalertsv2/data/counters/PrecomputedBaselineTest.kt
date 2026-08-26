package com.example.pokemonalertsv2.data.counters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pokébattler serves precomputed rankings only. For a boss outside the current raid
 * rotation exactly one parameter combination exists, and asking for anything else answers
 * HTTP 429 ("ranking capacity busy") or 504 — which is why most raids showed no counters
 * at all under a level-50 / Forever Friend setup.
 *
 * These pin the combination that a single-variable live sweep found to be the survivor.
 */
class PrecomputedBaselineTest {

    private val userSetup = RaidCounterOptions(
        attackerLevel = 50,
        weather = PokebattlerWeather.CLEAR,
        friendship = PokebattlerFriendship.FOREVER,
        sort = PokebattlerSort.TDO,
        attackStrategy = PokebattlerAttackStrategy.DODGE_WEAVE,
        includeMegas = false,
        includeShadow = false,
        includeLegendary = false
    )

    @Test
    fun `baseline is the combination Pokebattler always serves`() {
        val baseline = userSetup.precomputedBaseline()
        assertEquals(40, baseline.attackerLevel)
        assertEquals(PokebattlerWeather.NONE, baseline.weather)
        assertEquals(PokebattlerFriendship.NONE, baseline.friendship)
        assertEquals(PokebattlerAttackStrategy.CINEMATIC, baseline.attackStrategy)
        assertTrue(baseline.includeMegas)
        assertTrue(baseline.includeLegendary)
    }

    @Test
    fun `sort and shadow filtering are free, so they survive the downgrade`() {
        val baseline = userSetup.precomputedBaseline()
        assertEquals(PokebattlerSort.TDO, baseline.sort)
        assertFalse(baseline.includeShadow)
    }

    @Test
    fun `the baseline is a fixed point`() {
        val baseline = userSetup.precomputedBaseline()
        assertTrue(baseline.isPrecomputedBaseline)
        assertEquals(baseline, baseline.precomputedBaseline())
        // Nothing to retry when the request already is the baseline.
        assertTrue(baseline.downgradesFromBaseline().isEmpty())
    }

    @Test
    fun `app defaults are already servable, so a default request never needs a retry`() {
        assertTrue(RaidCounterOptions().isPrecomputedBaseline)
    }

    @Test
    fun `a setup that cannot be served is not mistaken for the baseline`() {
        assertFalse(userSetup.isPrecomputedBaseline)
        assertFalse(RaidCounterOptions(attackerLevel = 50).isPrecomputedBaseline)
        assertFalse(
            RaidCounterOptions(friendship = PokebattlerFriendship.FOREVER).isPrecomputedBaseline
        )
        assertFalse(RaidCounterOptions(weather = PokebattlerWeather.RAINY).isPrecomputedBaseline)
        assertFalse(RaidCounterOptions(includeMegas = false).isPrecomputedBaseline)
        assertFalse(RaidCounterOptions(includeLegendary = false).isPrecomputedBaseline)
        // Shadow filtering and sort are served either way.
        assertTrue(RaidCounterOptions(includeShadow = false).isPrecomputedBaseline)
        assertTrue(RaidCounterOptions(sort = PokebattlerSort.POWER).isPrecomputedBaseline)
    }

    @Test
    fun `downgrades name every setting the user will lose`() {
        val labels = userSetup.downgradesFromBaseline()
        assertEquals(
            listOf(
                "level 50",
                PokebattlerWeather.CLEAR.label,
                PokebattlerFriendship.FOREVER.label,
                PokebattlerAttackStrategy.DODGE_WEAVE.label,
                "megas excluded",
                "legendaries excluded"
            ),
            labels
        )
    }

    @Test
    fun `the reported downgrade covers only what actually changed`() {
        val onlyFriendship = RaidCounterOptions(friendship = PokebattlerFriendship.FOREVER)
        assertEquals(listOf("Forever Friend"), onlyFriendship.downgradesFromBaseline())

        val levelAndFriend = RaidCounterOptions(
            attackerLevel = 50,
            friendship = PokebattlerFriendship.FOREVER
        )
        assertEquals(listOf("level 50", "Forever Friend"), levelAndFriend.downgradesFromBaseline())
    }

    @Test
    fun `dodging is absent from the cache key because Pokebattler ignores it`() {
        // Verified live: DODGE_0, DODGE_REACTION_TIME and DODGE_100 return byte-identical
        // bodies. Splitting the cache on it would store identical copies.
        val base = RaidCounterOptions()
        fun key(o: RaidCounterOptions) =
            raidCounterCacheKey("ZEKROM", "RAID_LEVEL_5", AttackerSpec.Level(40), o)
        assertEquals(key(base), key(base.copy(dodge = PokebattlerDodge.PERFECT)))
        // The request still has to carry a valid token.
        assertEquals("DODGE_0", PokebattlerUrls.queryParams(base)["dodgeStrategy"])
        assertEquals(
            "DODGE_0",
            PokebattlerUrls.queryParams(base.copy(dodge = PokebattlerDodge.PERFECT))["dodgeStrategy"]
        )
    }
}
