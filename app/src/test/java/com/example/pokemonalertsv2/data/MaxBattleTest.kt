package com.example.pokemonalertsv2.data

import com.example.pokemonalertsv2.data.database.toDomain
import com.example.pokemonalertsv2.data.database.toEntity
import com.example.pokemonalertsv2.fcm.PushTopicPlanner
import com.example.pokemonalertsv2.notifications.AlertNotifier
import com.example.pokemonalertsv2.ui.alerts.AlertCategory
import com.example.pokemonalertsv2.ui.alerts.MapSelectorTarget
import com.example.pokemonalertsv2.ui.alerts.alertCategories
import com.example.pokemonalertsv2.ui.alerts.candidates
import com.example.pokemonalertsv2.ui.alerts.mappanel.activeFilterChips
import com.example.pokemonalertsv2.ui.alerts.resolveAlertVisualStyle
import com.example.pokemonalertsv2.ui.alerts.resolveRaidTier
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaxBattleTest {

    /** A Gigantamax Max Battle exactly as the backend's scanner maps it. */
    private val gmaxCharizard = PokemonAlert(
        id = 1_300_000_001,
        name = "Gigantamax Charizard Max Battle at Brunnen",
        description = "Max Battle ★6 at Brunnen",
        pokemon = "Charizard",
        pokedexId = 6,
        type = listOf("MaxBattle"),
        gym = "Brunnen",
        raidLevel = 6,
        breadMode = 2,
        area = "Alsbach",
        latitude = 49.8,
        longitude = 8.6,
        endTime = "2099-11-19 20:00:00"
    )

    private val dynamaxWooloo = gmaxCharizard.copy(
        id = 1_300_000_002,
        name = "Dynamax Wooloo Max Battle at Brunnen",
        pokemon = "Wooloo",
        pokedexId = 831,
        raidLevel = 1,
        breadMode = 1
    )

    @Test
    fun aMaxBattleIsOnlyAMaxBattle() {
        assertEquals(setOf(FilterAlertType.MAX_BATTLE), gmaxCharizard.filterAlertTypes())
        assertEquals(setOf(AlertCategory.MAX_BATTLE), gmaxCharizard.alertCategories())
        assertFalse("names a Pokémon but is not a spawn", gmaxCharizard.isSpawnAlert)
        assertTrue("fought at a Power Spot, so the wider range applies", gmaxCharizard.isStopOrGymAlert)
        assertFalse("never a raid, so no Raid Watch or counters", RaidTierParser.isRaid(gmaxCharizard))
        assertFalse("even at a Power Spot named after raids", RaidTierParser.isRaid(gmaxCharizard.copy(gym = "Raid Corner", name = "Max Battle at Raid Corner")))
        assertEquals(AlertCategory.MAX_BATTLE, resolveAlertVisualStyle(gmaxCharizard).category)
        assertTrue(gmaxCharizard.isGigantamax)
        assertFalse(dynamaxWooloo.isGigantamax)
        assertEquals("6", gmaxCharizard.maxBattleTierToken())
        assertEquals(AlertNotifier.CHANNEL_MAX_BATTLES, AlertNotifier.notificationChannelFor(gmaxCharizard))
    }

    @Test
    fun aFreshFilterShowsMaxBattles() {
        assertTrue(DEFAULT_FILTER_ALERT_TYPES.contains(FilterAlertType.MAX_BATTLE.name))
        assertTrue(AlertFilterMatcher.matches(gmaxCharizard, FilterDefinition()))
        val off = FilterDefinition(alertTypes = FilterSelection.only(DEFAULT_FILTER_ALERT_TYPES.normalizedValues - "max battle"))
        assertFalse(AlertFilterMatcher.matches(gmaxCharizard, off))
    }

    @Test
    fun bossesAndLevelsNarrowMaxBattles() {
        val onlyCharizard = FilterDefinition(maxBattleSpecies = FilterSelection.only(listOf("Charizard")))
        assertTrue(AlertFilterMatcher.matches(gmaxCharizard, onlyCharizard))
        assertFalse(AlertFilterMatcher.matches(dynamaxWooloo, onlyCharizard))

        val onlyLevelOne = FilterDefinition(maxBattleTiers = FilterSelection.only(listOf("1")))
        assertTrue(AlertFilterMatcher.matches(dynamaxWooloo, onlyLevelOne))
        assertFalse(AlertFilterMatcher.matches(gmaxCharizard, onlyLevelOne))
        assertEquals(2, onlyCharizard.copy(maxBattleTiers = FilterSelection.only(listOf("6"))).advancedRuleCount)

        val raidRule = FilterDefinition(raidSpecies = FilterSelection.only(listOf("Mewtwo")))
        assertTrue("raid rules leave Max Battles alone", AlertFilterMatcher.matches(gmaxCharizard, raidRule))
    }

    @Test
    fun unionKeepsEitherSidesMaxBattleRules() {
        val merged = unionOf(listOf(
            FilterDefinition(maxBattleSpecies = FilterSelection.only(listOf("Charizard"))),
            FilterDefinition(maxBattleSpecies = FilterSelection.only(listOf("Wooloo")))
        ))
        assertEquals(setOf("charizard", "wooloo"), merged.maxBattleSpecies.normalizedValues)
    }

    @Test
    fun schemaSixAddsMaxBattlesWhereverRaidsWereShown() {
        val v5 = """{"schemaVersion":5,"profiles":[{"id":"p","name":"Mine","definition":{"schemaVersion":5,
            "alertTypes":{"mode":"ONLY","values":["hundo"]}}}],
            "feed":{"mode":"LOCAL","definition":{"schemaVersion":5,"alertTypes":{"mode":"ONLY","values":["raid","quest"]}}},
            "map":{"mode":"LOCAL","definition":{"schemaVersion":5,"alertTypes":{"mode":"ALL","values":[]}}},
            "notifications":{"mode":"LOCAL","definition":{"schemaVersion":5,"alertTypes":{"mode":"ONLY","values":["RAID"]},
            "tappableSpecies":{"mode":"ONLY","values":["gengar"]}}}}"""
        val document = requireNotNull(FilterStateCodec.decode(v5))

        assertEquals(CURRENT_FILTER_SCHEMA_VERSION, document.schemaVersion)
        assertEquals("no raids, no Max Battles", setOf("hundo"), document.profiles.single().definition.alertTypes.normalizedValues)
        assertEquals(setOf("raid", "quest", "max battle"), document.feed.resolve(document).alertTypes.normalizedValues)
        assertEquals(FilterSelection.All, document.map.resolve(document).alertTypes)
        val notifications = document.notifications.resolve(document)
        assertEquals("they push like raids", setOf("raid", "max battle"), notifications.alertTypes.normalizedValues)
        assertEquals("earlier rules are kept", setOf("gengar"), notifications.tappableSpecies.normalizedValues)
        assertEquals(FilterSelection.All, notifications.maxBattleSpecies)
        assertEquals(FilterSelection.All, notifications.maxBattleTiers)
        assertEquals(document, FilterStateCodec.decode(FilterStateCodec.encode(document)))
    }

    @Test
    fun theirOwnTypeTopicNarrowsNotifications() {
        val json = Json { ignoreUnknownKeys = true }
        val catalog = json.decodeFromString<PushTopicCatalog>(
            requireNotNull(javaClass.classLoader).getResource("push-topics.json")!!.readText()
        )
        val types = PushTopicPlanner.selectedCatalogTypes(
            catalog,
            FilterDefinition(alertTypes = FilterSelection.only(listOf("MAX_BATTLE", "RAID")))
        )
        assertEquals(setOf("MaxBattle", "Raid"), types?.map { it.value }?.toSet())
    }

    @Test
    fun backendRowsParseAndSurviveTheDatabase() {
        val json = Json { ignoreUnknownKeys = true }
        val alert = json.decodeFromString<PokemonAlert>(
            """{"id":1300000003,"name":"Gigantamax Charizard Max Battle at Brunnen","type":["MaxBattle"],
            "pokemon":"Charizard","pokedexId":6,"gym":"Brunnen","raidLevel":6,"breadMode":2,
            "moves":{"fast":"Fire Spin","charged":"Blast Burn"},"moveIds":{"fast":269,"charged":287},
            "endTime":"2099-11-19 20:00:00","area":"Alsbach"}"""
        )
        assertEquals(2, alert.breadMode)
        val restored = alert.toEntity().toDomain()
        assertEquals(2, restored.breadMode)
        assertEquals(6, restored.raidLevel)
        assertEquals(setOf(FilterAlertType.MAX_BATTLE), restored.filterAlertTypes())

        val catalog = json.decodeFromString<FilterCatalog>("""{"maxBattleSpecies":["Charizard"],"maxBattleTiers":["1","6"]}""")
        assertEquals(listOf("Charizard"), MapSelectorTarget.MAX_BATTLE_SPECIES.candidates(catalog))
        assertEquals(listOf("1", "6"), MapSelectorTarget.MAX_BATTLE_TIERS.candidates(catalog))
    }

    @Test
    fun maxBattlesOnScreenJoinTheirOwnCatalogueNotTheSpawnList() {
        val merged = mergeFilterCatalog(
            FilterCatalog(maxBattleTiers = listOf("1", "2")),
            localSpecies = emptyList(),
            currentAlerts = listOf(gmaxCharizard, dynamaxWooloo.copy(raidLevel = 7))
        )
        assertEquals(listOf("Charizard", "Wooloo"), merged.maxBattleSpecies)
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7"), merged.maxBattleTiers)
        assertFalse("Charizard" in merged.spawnSpecies)
    }

    @Test
    fun narrowedRulesShowAsChips() {
        val chips = activeFilterChips(FilterDefinition(
            maxBattleSpecies = FilterSelection.only(listOf("Charizard")),
            maxBattleTiers = FilterSelection.only(listOf("6"))
        )).map { it.id }
        assertTrue("max_battle_species" in chips)
        assertTrue("max_battle_tiers" in chips)
    }

    @Test
    fun theMapMarkerCarriesTheLevel() {
        assertEquals("G", resolveRaidTier(gmaxCharizard, AlertCategory.MAX_BATTLE))
        assertEquals("1★", resolveRaidTier(dynamaxWooloo, AlertCategory.MAX_BATTLE))
        assertNull(resolveRaidTier(dynamaxWooloo, AlertCategory.SPAWN))
    }
}
