package com.example.pokemonalertsv2.data.counters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PokebattlerUrlsTest {

    @Test
    fun `reproduces the verified live counters url`() {
        val path = PokebattlerUrls.countersPath(
            bossPokemonId = "MEWTWO",
            raidLevel = "RAID_LEVEL_5",
            attacker = AttackerSpec.Level(40),
            attackStrategy = PokebattlerAttackStrategy.CINEMATIC.apiValue
        )
        assertEquals(
            "raids/defenders/MEWTWO/levels/RAID_LEVEL_5/attackers/levels/40" +
                "/strategies/CINEMATIC_ATTACK_WHEN_POSSIBLE/DEFENSE_RANDOM_MC",
            path
        )

        val query = PokebattlerUrls.queryParams(
            RaidCounterOptions(
                attackerLevel = 40,
                weather = PokebattlerWeather.NONE,
                friendship = PokebattlerFriendship.NONE,
                dodge = PokebattlerDodge.REALISTIC,
                sort = PokebattlerSort.OVERALL
            )
        )
        assertEquals("OVERALL", query["sort"])
        assertEquals("NO_WEATHER", query["weatherCondition"])
        assertEquals("DODGE_REACTION_TIME", query["dodgeStrategy"])
        assertEquals("AVERAGE", query["aggregation"])
        assertEquals("-1", query["randomAssistants"])
        assertEquals("FRIENDSHIP_LEVEL_0", query["friendLevel"])
    }

    @Test
    fun `attacker spec swaps only the attacker segment`() {
        val level = PokebattlerUrls.countersPath(
            "MEWTWO", "RAID_LEVEL_5", AttackerSpec.Level(30), "CINEMATIC_ATTACK_WHEN_POSSIBLE"
        )
        val user = PokebattlerUrls.countersPath(
            "MEWTWO", "RAID_LEVEL_5", AttackerSpec.PokebattlerUser("abc123"), "CINEMATIC_ATTACK_WHEN_POSSIBLE"
        )
        assertEquals("levels/30", AttackerSpec.Level(30).pathSegment)
        assertEquals("users/abc123", AttackerSpec.PokebattlerUser("abc123").pathSegment)
        assertEquals(level.replace("attackers/levels/30", "attackers/users/abc123"), user)
    }

    @Test
    fun `filter flags are serialized as booleans`() {
        val q = PokebattlerUrls.queryParams(
            RaidCounterOptions(includeMegas = false, includeShadow = false, includeLegendary = true)
        )
        assertEquals("false", q["includeMegas"])
        assertEquals("false", q["includeShadow"])
        assertEquals("true", q["includeLegendary"])
    }

    @Test
    fun `every option field participates in the cache key`() {
        val base = RaidCounterOptions()
        fun key(o: RaidCounterOptions) =
            raidCounterCacheKey("MEWTWO", "RAID_LEVEL_5", AttackerSpec.Level(o.attackerLevel), o)

        val baseKey = key(base)
        assertEquals(baseKey, key(base.copy()))

        val mutations = listOf(
            base.copy(attackerLevel = 30),
            base.copy(weather = PokebattlerWeather.RAINY),
            base.copy(friendship = PokebattlerFriendship.FOREVER),
            base.copy(dodge = PokebattlerDodge.PERFECT),
            base.copy(sort = PokebattlerSort.ESTIMATOR),
            base.copy(attackStrategy = PokebattlerAttackStrategy.DODGE_WEAVE),
            base.copy(includeMegas = false),
            base.copy(includeShadow = false),
            base.copy(includeLegendary = false)
        )
        mutations.forEach { assertNotEquals("option change must change the key", baseKey, key(it)) }
    }

    @Test
    fun `pokebox attacker gets a distinct cache key`() {
        val o = RaidCounterOptions()
        assertNotEquals(
            raidCounterCacheKey("MEWTWO", "RAID_LEVEL_5", AttackerSpec.Level(40), o),
            raidCounterCacheKey("MEWTWO", "RAID_LEVEL_5", AttackerSpec.PokebattlerUser("u1"), o)
        )
    }
}
