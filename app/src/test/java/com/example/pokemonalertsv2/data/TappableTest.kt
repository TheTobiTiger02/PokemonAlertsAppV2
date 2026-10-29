package com.example.pokemonalertsv2.data

import com.example.pokemonalertsv2.fcm.PushTopicPlanner
import com.example.pokemonalertsv2.ui.alerts.MapSelectorTarget
import com.example.pokemonalertsv2.ui.alerts.SPECIES_TARGETS
import com.example.pokemonalertsv2.ui.alerts.candidates
import com.example.pokemonalertsv2.ui.alerts.mappanel.activeFilterChips
import com.example.pokemonalertsv2.ui.alerts.mappanel.tappableRewardIcon
import com.example.pokemonalertsv2.ui.alerts.mappanel.tappableRewardSections
import com.example.pokemonalertsv2.ui.alerts.AlertCategory
import com.example.pokemonalertsv2.ui.alerts.alertCategories
import com.example.pokemonalertsv2.ui.alerts.resolveAlertVisualStyle
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TappableTest {

    /** A Pokémon a Tappable spawned, exactly as the backend serves it (a live sighting). */
    private val gengar = PokemonAlert(
        id = 1_100_000_001,
        name = "Gengar",
        description = "From a Tappable",
        pokemon = "Gengar",
        pokedexId = 94,
        type = listOf("Tappable"),
        live = true,
        area = "Alsbach",
        latitude = 49.8,
        longitude = 8.6,
        endTime = "2099-10-29 20:00:00"
    )

    /** A loot Tappable. */
    private val rareCandy = PokemonAlert(
        id = 1_200_000_000,
        name = "🎁 Rare Candy ×3",
        description = "Tappable (Box): Rare Candy ×3",
        type = listOf("Tappable"),
        questReward = "Rare Candy ×3",
        area = "Alsbach",
        latitude = 49.81,
        longitude = 8.61,
        endTime = "2099-10-29 20:00:00"
    )

    private val withTappables = FilterDefinition(
        alertTypes = FilterSelection.only(DEFAULT_FILTER_ALERT_TYPES.normalizedValues + "tappable")
    )

    @Test
    fun aTappableIsOnlyATappable() {
        assertEquals(setOf(FilterAlertType.TAPPABLE), gengar.filterAlertTypes())
        assertEquals(setOf(FilterAlertType.TAPPABLE), rareCandy.filterAlertTypes())
        assertFalse("not a wild spawn", gengar.isSpawnAlert)
        assertEquals(setOf(AlertCategory.TAPPABLE), gengar.alertCategories())
        assertEquals(AlertCategory.TAPPABLE, resolveAlertVisualStyle(rareCandy).category)
        assertEquals("Tappable", resolveAlertVisualStyle(gengar).label)
        assertEquals("Gengar", gengar.tappableToken())
        assertEquals("Rare Candy", rareCandy.tappableToken())
    }

    @Test
    fun tappablesAreOptInAndCanBeSwitchedOffAgain() {
        assertFalse("a fresh or reset filter leaves them off", AlertFilterMatcher.matches(gengar, FilterDefinition()))
        assertFalse(AlertFilterMatcher.matches(rareCandy, FilterDefinition()))
        assertTrue(AlertFilterMatcher.matches(gengar, withTappables))
        assertTrue(AlertFilterMatcher.matches(rareCandy, withTappables))

        val off = withTappables.copy(
            alertTypes = FilterSelection.only(withTappables.alertTypes.normalizedValues - "tappable" + "common")
        )
        assertFalse("Common on does not bring Tappables back", AlertFilterMatcher.matches(gengar, off))
        assertFalse(AlertFilterMatcher.matches(rareCandy, off))
        assertFalse("Other no longer covers them", AlertFilterMatcher.matches(rareCandy, off.copy(alertTypes = FilterSelection.only(listOf("Other")))))
    }

    @Test
    fun oneListPicksSpeciesAndLoot() {
        val onlyGengar = withTappables.copy(tappableSpecies = FilterSelection.only(listOf("Gengar")))
        assertTrue(AlertFilterMatcher.matches(gengar, onlyGengar))
        assertFalse(AlertFilterMatcher.matches(gengar.copy(pokemon = "Haunter", pokedexId = 93), onlyGengar))
        assertFalse(AlertFilterMatcher.matches(rareCandy, onlyGengar))

        val onlyCandy = withTappables.copy(tappableSpecies = FilterSelection.only(listOf("Rare Candy")))
        assertTrue(AlertFilterMatcher.matches(rareCandy, onlyCandy))
        assertTrue(AlertFilterMatcher.matches(rareCandy.copy(questReward = "Rare Candy ×1"), onlyCandy))
        assertFalse(AlertFilterMatcher.matches(gengar, onlyCandy))
        assertEquals(1, onlyCandy.advancedRuleCount)
    }

    @Test
    fun tappablePokemonPushOnTheirOwnTopicsAndOnlyWhenAskedFor() {
        val catalog = PushTopicCatalog(schemaVersion = 1, baseTopic = "alerts", legacyTopic = "alerts", fanoutEnabled = true)
        val dex = mapOf("gengar" to 94, "dratini" to 147)
        val tappablesOn = FilterDefinition(alertTypes = FilterSelection.only(listOf("Tappable", "Hundo")))

        assertEquals(setOf("alerts-tp-all"), PushTopicPlanner.liveSightingTopics(catalog, tappablesOn, dex))
        assertEquals(
            "loot adds no topic",
            setOf("alerts-tp-94"),
            PushTopicPlanner.liveSightingTopics(
                catalog,
                tappablesOn.copy(tappableSpecies = FilterSelection.only(listOf("Gengar", "Rare Candy"))),
                dex
            )
        )
        val both = FilterDefinition(
            alertTypes = FilterSelection.only(listOf("Tappable", "Common")),
            commonSpecies = FilterSelection.only(listOf("Dratini")),
            tappableSpecies = FilterSelection.only(listOf("Gengar"))
        )
        val topics = PushTopicPlanner.liveSightingTopics(catalog, both, dex)
        assertEquals(setOf("alerts-l-147", "alerts-tp-94"), topics)
        assertEquals(
            LiveSightingRegistration(species = listOf(147), tappableSpecies = listOf(94)),
            PushTopicPlanner.liveSightingRegistration(topics)
        )
        assertEquals(
            LiveSightingRegistration(all = true, tappableAll = true),
            PushTopicPlanner.liveSightingRegistration(setOf("alerts-l-all", "alerts-tp-all"))
        )
    }

    @Test
    fun schemaFiveShowsTappablesWhereOtherWasShownButKeepsNotificationsOff() {
        val v4 = """{"schemaVersion":4,"profiles":[{"id":"p","name":"Mine","definition":{"schemaVersion":4,
            "alertTypes":{"mode":"ONLY","values":["hundo","other"]}}}],
            "feed":{"mode":"LOCAL","definition":{"schemaVersion":4,"alertTypes":{"mode":"ONLY","values":["raid","other"]}}},
            "map":{"mode":"LOCAL","definition":{"schemaVersion":4,"alertTypes":{"mode":"ONLY","values":["raid"]}}},
            "notifications":{"mode":"LOCAL","definition":{"schemaVersion":4,"alertTypes":{"mode":"ONLY","values":["hundo","other"]},
            "commonSpecies":{"mode":"ONLY","values":["gible"]}}}}"""
        val document = requireNotNull(FilterStateCodec.decode(v4))

        assertEquals(5, document.schemaVersion)
        assertEquals(setOf("hundo", "other", "tappable"), document.profiles.single().definition.alertTypes.normalizedValues)
        assertEquals(setOf("raid", "other", "tappable"), document.feed.resolve(document).alertTypes.normalizedValues)
        assertEquals("no Other, no Tappable", setOf("raid"), document.map.resolve(document).alertTypes.normalizedValues)
        val notifications = document.notifications.resolve(document)
        assertEquals("notifications are opt-in", setOf("hundo", "other"), notifications.alertTypes.normalizedValues)
        assertEquals("schema 4 rules are not re-applied", setOf("gible"), notifications.commonSpecies.normalizedValues)
        assertEquals(FilterSelection.All, notifications.tappableSpecies)
        assertEquals(document, FilterStateCodec.decode(FilterStateCodec.encode(document)))
    }

    @Test
    fun aResetNotificationsFilterSubscribesToNoTappableTopic() {
        assertFalse(DEFAULT_FILTER_ALERT_TYPES.contains(FilterAlertType.TAPPABLE.name))
        assertFalse(DEFAULT_FILTER_ALERT_TYPES.contains(FilterAlertType.COMMON.name))
        val catalog = PushTopicCatalog(schemaVersion = 1, baseTopic = "alerts", legacyTopic = "alerts", fanoutEnabled = true)
        assertEquals(emptySet<String>(), PushTopicPlanner.liveSightingTopics(catalog, FilterDefinition(), mapOf("gengar" to 94)))
    }

    @Test
    fun backendRowsParse() {
        val json = Json { ignoreUnknownKeys = true }
        val loot = json.decodeFromString<PokemonAlert>(
            """{"id":1297611986,"name":"🎁 Rare Candy ×1","description":"Tappable (Box): Rare Candy ×1","latitude":49.86,
            "longitude":8.65,"endTime":"2099-10-29 19:58:05","type":["Tappable"],"pokemon":null,"questReward":"Rare Candy ×1",
            "expiryVerified":true,"pokemonRewards":[],"area":"Darmstadt-North"}"""
        )
        assertEquals(setOf(FilterAlertType.TAPPABLE), loot.filterAlertTypes())
        assertEquals("Rare Candy", loot.tappableToken())
        val catalog = json.decodeFromString<FilterCatalog>("""{"schemaVersion":1,"tappableRewards":["Rare Candy"]}""")
        assertEquals(listOf("Rare Candy"), catalog.tappableRewards)
    }

    @Test
    fun theTappablesTabOffersOnlyWhatTappablesGive() {
        val catalog = FilterCatalog(
            spawnSpecies = listOf("Bulbasaur", "Gengar", "Drifblim", "Pidgey"),
            tappableRewards = listOf("Rare Candy", "Gengar", "Drifblim"),
            tappableRewardIcons = mapOf("Rare Candy" to "https://icons.example/item/1301.png")
        )
        assertEquals(listOf("Rare Candy", "Gengar", "Drifblim"), MapSelectorTarget.TAPPABLE.candidates(catalog))
        assertFalse("not in the Pokémon tab", MapSelectorTarget.TAPPABLE in SPECIES_TARGETS)

        val sections = tappableRewardSections(catalog, FilterSelection.All, artwork = emptyMap())
        assertEquals(listOf("Rare Candy"), sections.loot)
        assertEquals(listOf("Gengar", "Drifblim"), sections.pokemon)
        // A pick that has since dropped out of the catalogue stays listed, so it can be undone.
        val stale = tappableRewardSections(catalog, FilterSelection.only(listOf("Pidgey", "Gengar")), emptyMap())
        assertEquals(listOf("Gengar", "Drifblim", "Pidgey"), stale.pokemon)

        assertEquals("https://icons.example/item/1301.png", tappableRewardIcon("rare candy", catalog, emptyMap()))
        assertEquals(
            "https://sprites.example/94.png",
            tappableRewardIcon("Gengar", catalog, mapOf("gengar" to "https://sprites.example/94.png"))
        )
    }

    @Test
    fun aNarrowedTappableRuleShowsOneChip() {
        val chips = activeFilterChips(FilterDefinition(tappableSpecies = FilterSelection.only(listOf("Gengar"))))
        assertEquals(listOf("Tappables: 1"), chips.filter { "Tappable" in it.label }.map { it.label })
    }

    @Test
    fun tappablesOnScreenJoinTheCatalogueWithTheirIcons() {
        val cached = FilterCatalog(tappableRewards = listOf("Rare Candy", "Gengar"))
        val merged = mergeFilterCatalog(
            cached,
            localSpecies = emptyList(),
            currentAlerts = listOf(
                gengar.copy(pokemon = "Noibat", pokedexId = 714, thumbnailUrl = "https://icons.example/pokemon/714.png"),
                rareCandy.copy(questReward = "Poké Ball ×5", thumbnailUrl = "https://icons.example/item/1.png")
            )
        )
        assertEquals(listOf("Poké Ball", "Rare Candy", "Gengar", "Noibat"), merged.tappableRewards)
        assertEquals("https://icons.example/pokemon/714.png", merged.tappableRewardIcons["Noibat"])
        assertEquals("https://icons.example/item/1.png", merged.tappableRewardIcons["Poké Ball"])
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<FilterCatalog>(
            """{"tappableRewards":["Rare Candy","Gengar"],"tappableRewardIcons":{"Rare Candy":"https://icons.example/item/1301.png"}}"""
        )
        assertEquals(mapOf("Rare Candy" to "https://icons.example/item/1301.png"), parsed.tappableRewardIcons)
    }
}
