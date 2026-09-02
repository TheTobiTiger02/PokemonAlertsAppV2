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
            maxDistanceKm = 2,
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
