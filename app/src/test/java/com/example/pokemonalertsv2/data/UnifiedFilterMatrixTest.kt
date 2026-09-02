package com.example.pokemonalertsv2.data

import androidx.datastore.preferences.core.*
import com.example.pokemonalertsv2.notifications.AlertNotifier
import com.example.pokemonalertsv2.ui.alerts.visibleMapAlerts
import com.example.pokemonalertsv2.widget.WidgetAlertFilter
import org.junit.Assert.*
import org.junit.Test

class UnifiedFilterMatrixTest {
    private val fixtures = listOf(
        alert(1, listOf("Spawn"), "Pikachu"),
        alert(2, listOf("Spawn", "Hundo"), "Bulbasaur").copy(ivAttack = 15, ivDefense = 15, ivStamina = 15),
        alert(3, listOf("Rare"), "Gible"),
        alert(4, listOf("Nundo"), "Eevee").copy(ivAttack = 0, ivDefense = 0, ivStamina = 0),
        alert(5, listOf("PvP"), "Marill"),
        alert(6, listOf("Raid", "5"), "Mewtwo"),
        alert(7, listOf("Raid", "Mega"), "Gengar"),
        alert(8, listOf("Rocket"), null).copy(gruntType = "Water"),
        alert(9, listOf("Quest"), null).copy(questTask = "Catch 5 Pokémon", questReward = "500 Stardust"),
        alert(10, listOf("Quest"), null).copy(questTask = "Make 3 Great Throws", questReward = "Rare Candy"),
        alert(11, listOf("Kecleon"), "Kecleon"),
        alert(12, listOf("WeatherChange"), null),
        alert(13, listOf("FutureType"), null)
    )

    @Test fun everyConsumerUsesTheSameFixtureMatrix() {
        val definitions = listOf(
            FilterDefinition(),
            FilterDefinition(alertTypes = FilterSelection.None),
            FilterDefinition(alertTypes = FilterSelection.only(listOf("RAID")), raidSpecies = FilterSelection.only(listOf("Mewtwo")), raidTiers = FilterSelection.only(listOf("5"))),
            FilterDefinition(alertTypes = FilterSelection.only(listOf("ROCKET")), rocketTypes = FilterSelection.only(listOf("Water"))),
            FilterDefinition(alertTypes = FilterSelection.only(listOf("QUEST")), quests = QuestFilterRules(exactPairs = setOf(QuestPairRule.of("Make 3 Great Throws", "Rare Candy")!!))),
            FilterDefinition(alertTypes = FilterSelection.only(listOf("QUEST")), quests = QuestFilterRules(facetEnabled = true, rewards = FilterSelection.only(listOf("500 Stardust")))),
            FilterDefinition(alertTypes = FilterSelection.only(listOf("OTHER"))),
            FilterDefinition(areas = FilterSelection.only(listOf("Missing")))
        ) + FilterAlertType.entries.map { type -> FilterDefinition(alertTypes = FilterSelection.only(listOf(type.name))) }
        definitions.forEach { definition ->
            val expected = fixtures.filter { AlertFilterMatcher.matches(it, definition) }.map { it.id }
            val map = visibleMapAlerts(fixtures, emptySet(), definition, emptySet(), false, 0L).map { it.id }
            val widget = WidgetAlertFilter.filterAlerts(fixtures, WidgetAlertFilter.Criteria(emptySet(), "All", 0, emptySet(), definition, 0L), null) as WidgetAlertFilter.Result.Filtered
            val notification = fixtures.filter { notificationSettings(definition).shouldNotify(it) }.map { it.id }
            assertEquals("Map mismatch for $definition", expected, map)
            assertEquals("Widget mismatch for $definition", expected, widget.alerts.map { it.id })
            assertEquals("Notification mismatch for $definition", expected, notification)
        }
    }

    @Test fun legacyMigrationPreservesIndependentTypesAndSharedLocation() {
        val preferences = mutablePreferencesOf(
            stringSetPreferencesKey("feed_filter_categories") to setOf("QUEST"),
            stringSetPreferencesKey("map_filter_categories") to setOf("RAID"),
            stringPreferencesKey("selected_area") to "Alsbach",
            intPreferencesKey("max_distance") to 5,
            intPreferencesKey("max_walking_minutes") to 15,
            stringSetPreferencesKey("allowed_spawn_species") to setOf("_none_"),
            stringSetPreferencesKey("allowed_hundo_species") to setOf("Pikachu"),
            stringSetPreferencesKey("excluded_raid_tiers") to setOf("Mega")
        )
        val migrated = migrateLegacyFilterState(preferences)
        val feed = migrated.feed.resolve(migrated)
        val map = migrated.map.resolve(migrated)
        val notifications = migrated.notifications.resolve(migrated)
        assertFalse(feed.alertTypes.contains("QUEST"))
        assertTrue(map.alertTypes.contains("QUEST"))
        assertFalse(map.alertTypes.contains("RAID"))
        assertEquals(feed.areas, notifications.areas)
        assertEquals(5, notifications.maxDistanceKm)
        assertEquals(15, notifications.maxWalkingMinutes)
        assertEquals(FilterSelection.None, notifications.spawnSpecies)
        assertTrue(notifications.hundoSpecies.contains("pikachu"))
        assertFalse(notifications.raidTiers.contains("Mega"))
        assertEquals(setOf("_none_"), preferences[stringSetPreferencesKey("allowed_spawn_species")])
    }

    @Test fun oldSingleSelectIsNotInvertedDuringMigration() {
        val migrated = migrateLegacyFilterState(mutablePreferencesOf(stringPreferencesKey("selected_alert_filter") to "RAIDS"))
        assertTrue(migrated.feed.definition.alertTypes.contains("RAID"))
        assertFalse(migrated.feed.definition.alertTypes.contains("SPAWN"))
    }

    @Test fun linksFollowEditsButCopiesDoNotAndNewerDocumentsWin() {
        val profile = FilterProfile("custom", "Custom", FilterDefinition(alertTypes = FilterSelection.only(listOf("RAID"))))
        val copy = FilterAssignment.local(profile.definition)
        val linked = FilterAssignment.linked(profile)
        val document = FilterStateDocument(schemaVersion = 2, profiles = listOf(profile.copy(definition = FilterDefinition(alertTypes = FilterSelection.only(listOf("QUEST"))))), feed = linked, map = copy)
        val decoded = FilterStateCodec.decode(FilterStateCodec.encode(document))!!
        assertTrue(decoded.feed.resolve(decoded).alertTypes.contains("QUEST"))
        assertFalse(decoded.map.resolve(decoded).alertTypes.contains("QUEST"))
        assertEquals(2, decoded.schemaVersion)
    }

    @Test fun normalizationMissingFieldsAndReachabilityAreExplicit() {
        assertEquals("flabebes task", normalizeFilterToken("Flabébé’s  task"))
        assertTrue(FilterSelection.All.contains(null))
        assertFalse(FilterSelection.None.contains(null))
        assertFalse(FilterSelection.only(listOf("Alsbach")).contains(null))
        val definition = FilterDefinition(maxDistanceKm = 2, maxWalkingMinutes = 10)
        assertTrue(AlertFilterMatcher.matches(fixtures.first(), definition, FilterMatchContext(Float.NaN, null)))
        assertFalse(AlertFilterMatcher.matches(fixtures.first(), definition, FilterMatchContext(null, 601)))
        assertTrue(AlertFilterMatcher.matches(fixtures.first(), definition, FilterMatchContext(2000f, 600)))
    }

    private fun alert(id: Int, type: List<String>, species: String?) = PokemonAlert(id = id, name = species ?: "Alert $id", type = type, pokemon = species, area = "Alsbach", latitude = 49.7, longitude = 8.6, endTime = "2099-01-01T12:00:00Z")

    private fun notificationSettings(definition: FilterDefinition) = AlertNotifier.NotificationSettings(
        notificationsEnabled = true, raidsEnabled = true, spawnsEnabled = true, questsEnabled = true,
        hundosEnabled = true, pvpEnabled = true, nundosEnabled = true, kecleonEnabled = true,
        rocketEnabled = true, vibrateEnabled = false, silenceUntil = 0L, maxWalkingMinutes = 0,
        quietHoursEnabled = false, quietHoursStartMinute = 0, quietHoursEndMinute = 0,
        selectedArea = "All", maxDistance = 0, excludedHundoTypes = emptySet(),
        excludedNundoTypes = emptySet(), excludedPvpTypes = emptySet(), excludedSpawnTypes = emptySet(),
        excludedRocketTypes = emptySet(), excludedRaidTiers = emptySet(), allowedHundoSpecies = emptySet(),
        allowedNundoSpecies = emptySet(), allowedPvpSpecies = emptySet(), allowedSpawnSpecies = emptySet(),
        nowMillis = 0L, filterDefinition = definition
    )
}
