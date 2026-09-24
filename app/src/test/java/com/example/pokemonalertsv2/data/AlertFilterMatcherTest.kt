package com.example.pokemonalertsv2.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertFilterMatcherTest {
    @Test fun emptyQuestPairSelectionsNeverImplicitlyMeanAll() {
        val quest = PokemonAlert(name = "Quest", type = listOf("Quest"), questTask = "Catch 5", questReward = "Dust")
        assertTrue(AlertFilterMatcher.matches(quest, FilterDefinition()))
        for (mode in listOf(FilterSelectionMode.NONE, FilterSelectionMode.ONLY)) {
            assertFalse(AlertFilterMatcher.matches(quest, FilterDefinition(quests = QuestFilterRules(exactMode = mode))))
        }
    }

    @Test
    fun explicitSelectionHasDistinctAllNoneAndOnlyStates() {
        assertTrue(FilterSelection.All.contains("Pikachu"))
        assertFalse(FilterSelection.None.contains("Pikachu"))
        assertTrue(FilterSelection.only(listOf("Mr. Mime")).contains("mr mime"))
        assertFalse(FilterSelection.only(listOf("Mr. Mime")).contains("Mime Jr."))
    }

    @Test
    fun overlappingAlertMayPassAnyEnabledIndependentBranch() {
        val hundoSpawn = alert(type = listOf("Spawn", "Hundo"), pokemon = "Pikachu", ivs = 15)
        val definition = FilterDefinition(
            alertTypes = FilterSelection.only(listOf("SPAWN", "HUNDO")),
            spawnSpecies = FilterSelection.only(listOf("Pikachu")),
            hundoSpecies = FilterSelection.only(listOf("Bulbasaur"))
        )

        assertTrue(AlertFilterMatcher.matches(hundoSpawn, definition))
    }

    @Test
    fun raidRequiresSelectedSpeciesAndTierWithinRaidBranch() {
        val raid = alert(type = listOf("Raid", "5"), pokemon = "Mewtwo")
        assertTrue(
            AlertFilterMatcher.matches(
                raid,
                FilterDefinition(
                    alertTypes = FilterSelection.only(listOf("RAID")),
                    raidSpecies = FilterSelection.only(listOf("Mewtwo")),
                    raidTiers = FilterSelection.only(listOf("5"))
                )
            )
        )
        assertFalse(
            AlertFilterMatcher.matches(
                raid,
                FilterDefinition(
                    alertTypes = FilterSelection.only(listOf("RAID")),
                    raidTiers = FilterSelection.only(listOf("Mega"))
                )
            )
        )
    }

    @Test
    fun questExactPairsUnionWithIndependentFacets() {
        val exact = alert(type = listOf("Quest"), task = "Make 3 Great Throws", reward = "Rare Candy")
        val facet = alert(type = listOf("Quest"), task = "Catch 5 Pokémon", reward = "500 Stardust")
        val rules = QuestFilterRules(
            exactPairs = setOf(QuestPairRule.of("Make 3 Great Throws", "Rare Candy")!!),
            facetEnabled = true,
            tasks = FilterSelection.only(listOf("Catch 5 Pokémon")),
            rewards = FilterSelection.only(listOf("500 Stardust"))
        )
        val definition = FilterDefinition(
            alertTypes = FilterSelection.only(listOf("QUEST")),
            quests = rules
        )

        assertTrue(AlertFilterMatcher.matches(exact, definition))
        assertTrue(AlertFilterMatcher.matches(facet, definition))
        assertFalse(
            AlertFilterMatcher.matches(
                alert(type = listOf("Quest"), task = "Catch 5 Pokémon", reward = "Rare Candy"),
                definition
            )
        )
    }

    @Test
    fun locationRulesRejectWrongAreaButKeepUnknownDistances() {
        val definition = FilterDefinition(
            areas = FilterSelection.only(listOf("Alsbach")),
            maxDistanceMeters = 2_000,
            maxWalkingMinutes = 10
        )
        assertTrue(AlertFilterMatcher.matches(alert(area = "Alsbach"), definition))
        assertFalse(AlertFilterMatcher.matches(alert(area = "Darmstadt"), definition))
        assertFalse(
            AlertFilterMatcher.matches(
                alert(area = "Alsbach"), definition,
                FilterMatchContext(effectiveDistanceMeters = 2_001f)
            )
        )
    }

    @Test
    fun subKilometerLimitsMatchAtHundredMeterPrecision() {
        val definition = FilterDefinition(maxDistanceMeters = 500)
        assertTrue(AlertFilterMatcher.matches(alert(), definition, FilterMatchContext(effectiveDistanceMeters = 500f)))
        assertFalse(AlertFilterMatcher.matches(alert(), definition, FilterMatchContext(effectiveDistanceMeters = 501f)))

        val tight = FilterDefinition(maxDistanceMeters = 100)
        assertTrue(AlertFilterMatcher.matches(alert(), tight, FilterMatchContext(effectiveDistanceMeters = 100f)))
        assertFalse(AlertFilterMatcher.matches(alert(), tight, FilterMatchContext(effectiveDistanceMeters = 101f)))
    }

    @Test
    fun unclassifiedAlertsUseOtherBranch() {
        val unknown = alert(type = listOf("FutureType"), pokemon = null)
        assertTrue(
            AlertFilterMatcher.matches(
                unknown,
                FilterDefinition(alertTypes = FilterSelection.only(listOf("OTHER")))
            )
        )
        assertFalse(
            AlertFilterMatcher.matches(
                unknown,
                FilterDefinition(alertTypes = FilterSelection.only(listOf("RAID")))
            )
        )
    }

    @Test
    fun deletingLinkedProfileCopiesRulesIntoConsumers() {
        val profile = FilterProfile(
            id = "p1",
            name = "Nearby raids",
            definition = FilterDefinition(alertTypes = FilterSelection.only(listOf("RAID")))
        )
        val document = FilterStateDocument(
            profiles = listOf(profile),
            feed = FilterAssignment.linked(profile),
            notifications = FilterAssignment.linked(profile)
        ).deleteProfilePreservingConsumers(profile.id)

        assertTrue(document.profiles.isEmpty())
        assertTrue(document.feed.mode == FilterAssignmentMode.LOCAL)
        assertTrue(document.notifications.resolve(document).alertTypes.contains("RAID"))
    }

    @Test
    fun distanceOverridesResolveMostSpecificFirst() {
        val definition = FilterDefinition(
            maxDistanceMeters = 10_000,
            distanceOverrides = DistanceOverrides(
                perType = mapOf(FilterAlertType.QUEST.name to 5_000),
                perSpecies = mapOf("spinda" to 20_000)
            )
        )
        val spindaQuest = alert(type = listOf("Quest"), task = "Catch 5 Pokémon", reward = "Spinda")
        val otherQuest = alert(type = listOf("Quest"), task = "Catch 5 Pokémon", reward = "500 Stardust")
        val spawn = alert(type = listOf("Spawn"), pokemon = "Pikachu")
        val at15km = FilterMatchContext(effectiveDistanceMeters = 15_000f)

        // Species override (20 km) beats the quest type override (5 km).
        assertTrue(AlertFilterMatcher.matches(spindaQuest, definition, at15km))
        // Type override (5 km) beats the 10 km default.
        assertFalse(AlertFilterMatcher.matches(otherQuest, definition, at15km))
        // No override, so the 10 km default applies.
        assertFalse(AlertFilterMatcher.matches(spawn, definition, at15km))
        assertTrue(
            AlertFilterMatcher.matches(spawn, definition, FilterMatchContext(effectiveDistanceMeters = 9_000f))
        )
    }

    @Test
    fun distanceOverrideOfZeroMeansUnlimitedAtEveryLevel() {
        val unlimitedQuests = FilterDefinition(
            maxDistanceMeters = 5_000,
            distanceOverrides = DistanceOverrides(perType = mapOf(FilterAlertType.QUEST.name to 0))
        )
        val quest = alert(type = listOf("Quest"), task = "Catch 5 Pokémon", reward = "Dust")
        assertTrue(
            AlertFilterMatcher.matches(quest, unlimitedQuests, FilterMatchContext(effectiveDistanceMeters = 40_000f))
        )
        assertFalse(
            AlertFilterMatcher.matches(
                alert(type = listOf("Spawn")), unlimitedQuests,
                FilterMatchContext(effectiveDistanceMeters = 40_000f)
            )
        )
    }

    @Test
    fun multiTypeAlertPassesWhenAnyBranchIsWithinItsOwnLimit() {
        val hundoSpawn = alert(type = listOf("Spawn", "Hundo"), pokemon = "Pikachu", ivs = 15)
        val definition = FilterDefinition(
            alertTypes = FilterSelection.only(listOf("SPAWN", "HUNDO")),
            maxDistanceMeters = 2_000,
            distanceOverrides = DistanceOverrides(perType = mapOf(FilterAlertType.HUNDO.name to 30_000))
        )
        // SPAWN is out of range at 2 km, but HUNDO allows 30 km, so the alert survives.
        assertTrue(
            AlertFilterMatcher.matches(hundoSpawn, definition, FilterMatchContext(effectiveDistanceMeters = 20_000f))
        )
    }

    @Test
    fun unknownDistanceNeverHidesAnAlertEvenWithOverrides() {
        val definition = FilterDefinition(
            maxDistanceMeters = 1_000,
            distanceOverrides = DistanceOverrides(perType = mapOf(FilterAlertType.SPAWN.name to 1_000))
        )
        assertTrue(AlertFilterMatcher.matches(alert(), definition, FilterMatchContext()))
        assertTrue(
            AlertFilterMatcher.matches(alert(), definition, FilterMatchContext(effectiveDistanceMeters = Float.NaN))
        )
    }

    @Test
    fun withoutOverridesDistanceBehaviorIsUnchanged() {
        val definition = FilterDefinition(maxDistanceMeters = 3_000)
        assertTrue(
            AlertFilterMatcher.matches(alert(), definition, FilterMatchContext(effectiveDistanceMeters = 3_000f))
        )
        assertFalse(
            AlertFilterMatcher.matches(alert(), definition, FilterMatchContext(effectiveDistanceMeters = 3_001f))
        )
    }

    @Test
    fun distanceScaleStepsOneHundredMetersBelowOneKilometerThenKilometers() {
        assertTrue(ALERT_DISTANCE_STEPS_METERS.first() == 0)
        assertTrue(ALERT_DISTANCE_STEPS_METERS[1] == 100)
        assertTrue(ALERT_DISTANCE_STEPS_METERS[9] == 900)
        assertTrue(ALERT_DISTANCE_STEPS_METERS[10] == 1_000)
        assertTrue(ALERT_DISTANCE_STEPS_METERS[11] == 2_000)
        assertTrue(ALERT_DISTANCE_STEPS_METERS.last() == MAX_FILTER_DISTANCE_METERS)
        assertTrue(ALERT_DISTANCE_STEPS_METERS.zipWithNext().all { (a, b) -> a < b })
        assertTrue(distanceStepIndex(500) == 5)
        assertTrue(distanceLabel(500) == "500 m")
        assertTrue(distanceLabel(3_000) == "3 km")
        assertTrue(distanceLabel(0) == "Unlimited")
    }

    @Test
    fun schemaVersionTwoDocumentsStillDecodeVersionOnePayloads() {
        val legacy = """{"schemaVersion":1,"feed":{"mode":"LOCAL","definition":{"maxDistanceKm":7}}}"""
        val decoded = FilterStateCodec.decode(legacy)
        assertTrue(decoded != null)
        assertTrue(decoded!!.feed.resolve(decoded).maxDistanceMeters == 7_000)
        assertTrue(decoded.feed.resolve(decoded).distanceOverrides.ruleCount == 0)
    }

    @Test
    fun schemaVersionThreeDecodingRescalesKilometerOverridesToMeters() {
        val legacy = """
            {"schemaVersion":2,
             "profiles":[{"id":"p1","name":"Nearby","definition":{"maxDistanceKm":3,"distanceOverrides":{"perType":{"QUEST":2},"perSpecies":{"spinda":10}}}}],
             "feed":{"mode":"LINKED","profileId":"p1","definition":{}},
             "notifications":{"mode":"LOCAL","definition":{"maxDistanceKm":1}}}
        """.trimIndent()
        val decoded = FilterStateCodec.decode(legacy)!!
        val resolved = decoded.feed.resolve(decoded)
        assertTrue(resolved.maxDistanceMeters == 3_000)
        assertTrue(resolved.distanceOverrides.perType[FilterAlertType.QUEST.name] == 2_000)
        assertTrue(resolved.distanceOverrides.perSpecies["spinda"] == 10_000)
        assertTrue(decoded.notifications.resolve(decoded).maxDistanceMeters == 1_000)
        assertTrue(decoded.schemaVersion == CURRENT_FILTER_SCHEMA_VERSION)

        // Round-trip: re-encoding writes meters, and decoding that output changes nothing.
        val reencoded = FilterStateCodec.decode(FilterStateCodec.encode(decoded))!!
        assertTrue(reencoded.feed.resolve(reencoded).maxDistanceMeters == 3_000)
    }

    @Test
    fun savedAssignmentsFromOlderVersionsDecodeWithMeterDistances() {
        val legacyAssignment = """{"mode":"LOCAL","definition":{"schemaVersion":2,"maxDistanceKm":4}}"""
        val decoded = FilterStateCodec.decodeAssignment(legacyAssignment)!!
        assertTrue(decoded.definition.maxDistanceMeters == 4_000)
    }

    private fun alert(
        type: List<String> = listOf("Spawn"),
        pokemon: String? = "Pikachu",
        ivs: Int? = null,
        task: String? = null,
        reward: String? = null,
        area: String? = "Alsbach"
    ) = PokemonAlert(
        name = pokemon ?: "Alert",
        type = type,
        pokemon = pokemon,
        ivAttack = ivs,
        ivDefense = ivs,
        ivStamina = ivs,
        questTask = task,
        questReward = reward,
        area = area
    )
}
