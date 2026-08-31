package com.example.pokemonalertsv2.ui.counters

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.pokemonalertsv2.data.HundoCP
import com.example.pokemonalertsv2.data.counters.AvailableRaidBoss
import com.example.pokemonalertsv2.raidwatch.ManualRaidBoss
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ManualRaidPickerComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pickerShowsHundoCpFiltersAndSelectsBoss() {
        var selected: ManualRaidBoss? = null
        val mewtwo = boss("MEWTWO", "Mewtwo", "5", 2387, 2984)
        val absol = boss("ABSOL", "Absol", "Mega", 1443, 1805)
        composeRule.setContent {
            PokemonAlertsV2Theme {
                ManualRaidBossPickerScreen(
                    state = ManualRaidPickerUiState(loading = false, bosses = listOf(mewtwo, absol)),
                    onBack = {},
                    onRetry = {},
                    onBossSelected = { selected = it }
                )
            }
        }

        composeRule.onNodeWithText("Mewtwo").assertIsDisplayed()
        composeRule.onNodeWithText("100% catch CP · L20: 2387 | L25: 2984").assertIsDisplayed()
        composeRule.onNodeWithTag("manual_raid_search").performTextInput("Mew")
        composeRule.onNodeWithText("Absol").assertDoesNotExist()
        composeRule.onNodeWithTag("manual_raid_boss_MEWTWO").performClick()
        assertEquals(mewtwo, selected)
    }

    private fun boss(id: String, name: String, tier: String, l20: Int, l25: Int) =
        ManualRaidBoss(
            catalogue = AvailableRaidBoss(id, name, "RAID_LEVEL_5", null, false),
            tierLabel = tier,
            hundoCP = HundoCP(l20, l25),
            pokedexId = null,
            spriteUrls = emptyList()
        )
}
