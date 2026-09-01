package com.example.pokemonalertsv2.ui.alerts

import com.example.pokemonalertsv2.data.PokemonAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertCategoryFilterTest {

    @Test
    fun aHundoSpawnIsHiddenWhenEitherOfItsCategoriesIsMuted() {
        val hundoSpawn = alert(name = "Dragonite", type = listOf("Spawn", "Hundo"))

        assertEquals(
            setOf(AlertCategory.SPAWN, AlertCategory.HUNDO),
            hundoSpawn.alertCategories()
        )
        // Muting either category hides the alert; muting something unrelated keeps it.
        assertFalse(matchesCategorySelection(hundoSpawn, mutedCategories = setOf(AlertCategory.SPAWN)))
        assertFalse(matchesCategorySelection(hundoSpawn, mutedCategories = setOf(AlertCategory.HUNDO)))
        assertTrue(matchesCategorySelection(hundoSpawn, mutedCategories = setOf(AlertCategory.RAID)))
    }

    @Test
    fun specialAlertsNeverClaimTheSpawnCategory() {
        assertEquals(
            setOf(AlertCategory.RAID),
            alert(name = "Raid", type = listOf("Raid", "5")).alertCategories()
        )
        assertEquals(
            setOf(AlertCategory.QUEST),
            alert(name = "Quest", type = listOf("Quest")).alertCategories()
        )
        assertEquals(
            setOf(AlertCategory.WEATHER),
            alert(name = "Weather", type = listOf("WeatherChange")).alertCategories()
        )
    }

    @Test
    fun aPerfectIvAlertCountsAsHundoEvenWithoutTheToken() {
        val perfect = PokemonAlert(
            name = "Snorlax",
            iv = "100.0",
            ivAttack = 15,
            ivDefense = 15,
            ivStamina = 15
        )
        assertTrue(AlertCategory.HUNDO in perfect.alertCategories())
    }

    @Test
    fun emptySelectionShowsEverythingIncludingUnclassifiableAlerts() {
        val unclassifiable = PokemonAlert(name = "???")
        val anything = alert(name = "Raid", type = listOf("Raid"))

        assertTrue(matchesCategorySelection(unclassifiable, mutedCategories = emptySet()))
        assertTrue(matchesCategorySelection(anything, mutedCategories = emptySet()))
        // An alert no toggle could ever bring back must not be hidden.
        assertTrue(matchesCategorySelection(unclassifiable, mutedCategories = setOf(AlertCategory.RAID)))
    }

    @Test
    fun legacyWidgetTokensMapToCategories() {
        assertEquals(AlertCategory.HUNDO, legacyWidgetTokenToCategory("Hundo"))
        assertEquals(AlertCategory.PVP, legacyWidgetTokenToCategory("PvP"))
        assertEquals(AlertCategory.ROCKET, legacyWidgetTokenToCategory("Rocket"))
        assertNull(legacyWidgetTokenToCategory("Something Else"))
    }

    @Test
    fun storedNameSetsTolerateUnknownValues() {
        assertEquals(
            setOf(AlertCategory.RAID),
            setOf("RAID", "NOT_A_CATEGORY").toCategorySelection()
        )
        assertEquals(emptySet<AlertCategory>(), setOf("NOPE").toCategorySelection())
    }

    private fun alert(name: String, type: List<String>) = PokemonAlert(
        name = name,
        latitude = 49.74,
        longitude = 8.62,
        endTime = "2099-01-01T00:00:00Z",
        type = type
    )
}
