package com.example.pokemonalertsv2.data

import com.example.pokemonalertsv2.data.database.toDomain
import com.example.pokemonalertsv2.data.database.toEntity
import com.example.pokemonalertsv2.fcm.PushTopicPlanner
import com.example.pokemonalertsv2.notifications.AlertNotifier
import com.example.pokemonalertsv2.tracking.liveSightingReplacement
import com.example.pokemonalertsv2.ui.alerts.AlertCategory
import com.example.pokemonalertsv2.ui.alerts.resolveAlertVisualStyle
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSightingsTest {

    private val dratini = PokemonAlert(
        id = 1_100_000_000,
        name = "Dratini",
        pokemon = "Dratini",
        pokedexId = 147,
        type = listOf("Common"),
        live = true,
        area = "Alsbach",
        latitude = 49.8,
        longitude = 8.6,
        endTime = "2099-09-16 14:20:00",
        expiryVerified = false
    )

    @Test
    fun backendLiveRowParsesAndSurvivesTheCache() {
        val json = """{"id":1100000000,"name":"Dratini","description":"","latitude":49.8,"longitude":8.6,
            "endTime":"2099-09-16 14:20:00","expiryVerified":false,"type":["Common"],"live":true,"pokemon":"Dratini",
            "pokemonForm":null,"pokedexId":147,"isShiny":false,"pvpRankings":[],"area":"Alsbach"}"""
        val alert = Json { ignoreUnknownKeys = true }.decodeFromString<PokemonAlert>(json)

        assertTrue(alert.isLiveSighting)
        assertNull(alert.iv)
        val cached = alert.toEntity().toDomain()
        assertEquals(false, cached.expiryVerified)
        assertTrue(cached.isLiveSighting)
        val upgraded = alert.copy(id = 612, type = listOf("Hundo"), live = null, replacesAlertId = 1_100_000_000)
        assertEquals(1_100_000_000, upgraded.toEntity().toDomain().replacesAlertId)
        assertFalse(upgraded.isLiveSighting)
    }

    @Test
    fun commonIsItsOwnTypeAndIvSpawnsNoLongerIncludeIt() {
        assertEquals(setOf(FilterAlertType.COMMON), dratini.filterAlertTypes())
        assertEquals(AlertCategory.COMMON, resolveAlertVisualStyle(dratini).category)
        assertEquals("Common", resolveAlertVisualStyle(dratini).label)
        val discordCommon = dratini.copy(live = null)
        assertEquals(setOf(FilterAlertType.COMMON), discordCommon.filterAlertTypes())
        // A cached alert from before the rename still counts as Common.
        assertEquals(setOf(FilterAlertType.COMMON), dratini.copy(type = listOf("Rare")).filterAlertTypes())
        assertEquals(
            setOf(FilterAlertType.HUNDO, FilterAlertType.SPAWN),
            dratini.copy(type = listOf("Hundo"), live = null, ivAttack = 15, ivDefense = 15, ivStamina = 15).filterAlertTypes()
        )
    }

    @Test
    fun commonIsOffByDefaultAndStartsWithEverySpeciesOnceTurnedOn() {
        val default = FilterDefinition()
        assertFalse(AlertFilterMatcher.matches(dratini, default))
        assertTrue(default.alertTypes.contains(FilterAlertType.HUNDO.name))

        val turnedOn = default.copy(alertTypes = FilterSelection.only(default.alertTypes.normalizedValues + "common"))
        assertTrue(AlertFilterMatcher.matches(dratini, turnedOn))
        assertTrue(AlertFilterMatcher.matches(dratini.copy(pokemon = "Pidgey", pokedexId = 16), turnedOn))

        val onlyDratini = turnedOn.copy(commonSpecies = FilterSelection.only(listOf("Dratini")))
        assertTrue(AlertFilterMatcher.matches(dratini, onlyDratini))
        assertFalse(AlertFilterMatcher.matches(dratini.copy(pokemon = "Pidgey", pokedexId = 16), onlyDratini))
    }

    @Test
    fun liveSightingsNotifyThroughTheNotificationsFilter() {
        val commonDratini = FilterDefinition(
            alertTypes = FilterSelection.only(listOf("Common")),
            commonSpecies = FilterSelection.only(listOf("Dratini")),
            areas = FilterSelection.only(listOf("Alsbach")),
            maxDistanceMeters = 500
        )
        val settings = settings().copy(filterDefinition = commonDratini)
        assertTrue(settings.shouldNotify(dratini, matchContext = FilterMatchContext(effectiveDistanceMeters = 400f)))
        assertFalse(settings.shouldNotify(dratini, matchContext = FilterMatchContext(effectiveDistanceMeters = 600f)))
        assertFalse(settings.shouldNotify(dratini.copy(pokemon = "Dragonair", pokedexId = 148)))
        assertFalse(settings.shouldNotify(dratini.copy(area = "Darmstadt-North")))
        assertFalse("silence still wins", settings.copy(silenceUntil = Long.MAX_VALUE).shouldNotify(dratini))
        assertFalse("Common off by default", settings().copy(filterDefinition = FilterDefinition()).shouldNotify(dratini))
    }

    @Test
    fun liveTopicsFollowTheNotificationsCommonSelection() {
        val catalog = PushTopicCatalog(schemaVersion = 1, baseTopic = "alerts", legacyTopic = "alerts", fanoutEnabled = true)
        val dex = mapOf("dratini" to 147, "charmander" to 4, "eevee starter" to 10159)
        val commonOn = FilterDefinition(alertTypes = FilterSelection.only(listOf("Common", "Hundo")))

        assertEquals(emptySet<String>(), PushTopicPlanner.liveSightingTopics(catalog, FilterDefinition(), dex))
        assertEquals(setOf("alerts-l-all"), PushTopicPlanner.liveSightingTopics(catalog, commonOn, dex))
        assertEquals(
            setOf("alerts-l-4", "alerts-l-147"),
            PushTopicPlanner.liveSightingTopics(
                null,
                commonOn.copy(commonSpecies = FilterSelection.only(listOf("Dratini", "Charmander", "Eevee Starter", "Unknownmon"))),
                dex
            )
        )
        assertEquals(emptySet<String>(), PushTopicPlanner.liveSightingTopics(catalog, commonOn.copy(commonSpecies = FilterSelection.None), dex))
        assertEquals(LiveSightingRegistration(all = true), PushTopicPlanner.liveSightingRegistration(setOf("alerts-l-all")))
        assertEquals(LiveSightingRegistration(species = listOf(4, 147)), PushTopicPlanner.liveSightingRegistration(setOf("alerts-l-147", "alerts-l-4")))
    }

    @Test
    fun schemaFourTurnsRareIntoAnOffCommonType() {
        val v3 = """{"schemaVersion":3,"profiles":[{"id":"p","name":"Spawns","definition":{"schemaVersion":3,
            "alertTypes":{"mode":"ONLY","values":["rare","hundo"]},"rareSpecies":{"mode":"ONLY","values":["gible"]},
            "distanceOverrides":{"perType":{"RARE":300},"perSpecies":{}}}}],
            "feed":{"mode":"LOCAL","definition":{"schemaVersion":3,"alertTypes":{"mode":"ALL","values":[]}}},
            "map":{"mode":"LOCAL","definition":{"schemaVersion":3,"alertTypes":{"mode":"NONE","values":[]}}},
            "notifications":{"mode":"LINKED","profileId":"p"}}"""
        val document = requireNotNull(FilterStateCodec.decode(v3))

        assertEquals(CURRENT_FILTER_SCHEMA_VERSION, document.schemaVersion)
        val profile = document.profiles.single().definition
        assertEquals(setOf("hundo"), profile.alertTypes.normalizedValues)
        assertEquals(FilterSelection.All, profile.commonSpecies)
        assertEquals(mapOf("COMMON" to 300), profile.distanceOverrides.perType)
        val feed = document.feed.resolve(document)
        assertEquals(DEFAULT_FILTER_ALERT_TYPES.normalizedValues, feed.alertTypes.normalizedValues)
        assertEquals(FilterSelectionMode.NONE, document.map.resolve(document).alertTypes.mode)
        // Re-reading the migrated document changes nothing.
        assertEquals(document, FilterStateCodec.decode(FilterStateCodec.encode(document)))
    }

    @Test
    fun aHuntFollowsTheSightingIntoItsFullAlert() {
        val hundo = dratini.copy(id = 612, type = listOf("Hundo"), live = null, replacesAlertId = dratini.id)
        val other = dratini.copy(id = 613, live = null, replacesAlertId = 99)

        assertEquals(hundo, liveSightingReplacement(dratini, listOf(other, hundo), emptySet()))
        assertNull(liveSightingReplacement(dratini, listOf(hundo), setOf(hundo.uniqueId)))
        assertNull(liveSightingReplacement(dratini, listOf(other), emptySet()))
    }

    private fun settings() = AlertNotifier.NotificationSettings(
        notificationsEnabled = true,
        raidsEnabled = true,
        spawnsEnabled = true,
        questsEnabled = true,
        hundosEnabled = true,
        pvpEnabled = true,
        nundosEnabled = true,
        kecleonEnabled = true,
        rocketEnabled = true,
        vibrateEnabled = true,
        silenceUntil = 0L,
        maxWalkingMinutes = 0,
        quietHoursEnabled = false,
        quietHoursStartMinute = DEFAULT_QUIET_HOURS_START,
        quietHoursEndMinute = DEFAULT_QUIET_HOURS_END,
        selectedArea = "All",
        maxDistance = 0,
        excludedHundoTypes = emptySet(),
        excludedNundoTypes = emptySet(),
        excludedPvpTypes = emptySet(),
        excludedSpawnTypes = emptySet(),
        excludedRocketTypes = emptySet(),
        excludedRaidTiers = emptySet(),
        allowedHundoSpecies = emptySet(),
        allowedNundoSpecies = emptySet(),
        allowedPvpSpecies = emptySet(),
        allowedSpawnSpecies = emptySet()
    )
}
