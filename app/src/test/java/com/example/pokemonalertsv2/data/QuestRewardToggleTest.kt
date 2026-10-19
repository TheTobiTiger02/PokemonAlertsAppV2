package com.example.pokemonalertsv2.data

import org.junit.Assert.assertEquals
import org.junit.Test

class QuestRewardToggleTest {
    private val stardust = listOf(QuestPairRule("catch 5", "500 stardust"), QuestPairRule("spin 3", "500 stardust"))
    private val berry = listOf(QuestPairRule("throw 3", "razz berry"))
    private val universe = stardust + berry

    @Test
    fun fromAllSwitchingARewardOffKeepsTheOthers() {
        val rules = QuestFilterRules().withRewardToggled(stardust, universe)
        assertEquals(FilterSelectionMode.ONLY, rules.exactMode)
        assertEquals(berry.toSet(), rules.exactPairs)
    }

    @Test
    fun fromNoneSwitchingARewardOnSelectsIt() {
        val none = QuestFilterRules(exactMode = FilterSelectionMode.NONE)
        assertEquals(berry.toSet(), none.withRewardToggled(berry, universe).exactPairs)
    }

    @Test
    fun aPartlyChosenRewardIsCompletedFirst() {
        val partial = QuestFilterRules(exactPairs = setOf(stardust.first()))
        assertEquals(stardust.toSet(), partial.withRewardToggled(stardust, universe).exactPairs)
    }

    @Test
    fun choosingEverythingSettlesBackToAll() {
        val rules = QuestFilterRules(exactPairs = berry.toSet()).withRewardToggled(stardust, universe)
        assertEquals(QuestFilterRules(), rules)
    }

    @Test
    fun emptyingTheSelectionSettlesToNone() {
        val rules = QuestFilterRules(exactPairs = berry.toSet()).withRewardToggled(berry, universe)
        assertEquals(FilterSelectionMode.NONE, rules.exactMode)
        assertEquals(emptySet<QuestPairRule>(), rules.exactPairs)
    }

    @Test
    fun singleTasksCanBeHiddenAndShownAgain() {
        val oneHidden = QuestFilterRules().withPairs(listOf(stardust.first()), show = false, universe = universe)
        assertEquals((universe - stardust.first()).toSet(), oneHidden.exactPairs)
        assertEquals(QuestFilterRules(), oneHidden.withPairs(listOf(stardust.first()), show = true, universe = universe))
    }

    @Test
    fun showingAlreadyShownPairsChangesNothing() {
        val partial = QuestFilterRules(exactPairs = berry.toSet())
        assertEquals(partial, partial.withPairs(berry, show = true, universe = universe))
    }

    @Test
    fun taskAndRewardRulesAreLeftAlone() {
        val facet = QuestFilterRules(facetEnabled = true, rewards = FilterSelection.only(listOf("rare candy")), exactMode = FilterSelectionMode.NONE)
        val rules = facet.withRewardToggled(universe, universe)
        assertEquals(true, rules.facetEnabled)
        assertEquals(facet.rewards, rules.rewards)
        // With a facet rule in play, choosing every pair stays an explicit list rather than "all".
        assertEquals(FilterSelectionMode.ONLY, rules.exactMode)
    }
}
