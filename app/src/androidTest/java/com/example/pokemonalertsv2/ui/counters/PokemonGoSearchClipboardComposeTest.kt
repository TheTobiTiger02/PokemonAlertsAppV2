package com.example.pokemonalertsv2.ui.counters

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.AnnotatedString
import com.example.pokemonalertsv2.data.counters.CounterSourceId
import com.example.pokemonalertsv2.data.counters.PersonalCounter
import com.example.pokemonalertsv2.data.counters.PersonalRanking
import com.example.pokemonalertsv2.data.counters.PersonalTeamSlot
import com.example.pokemonalertsv2.data.counters.syntheticChargedMove
import com.example.pokemonalertsv2.data.counters.syntheticFastMove
import com.example.pokemonalertsv2.data.pokegenie.OwnedPokemon
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Pixel/device check for the actual Copy for GO clipboard path. */
@Suppress("DEPRECATION")
class PokemonGoSearchClipboardComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun copyForGoUsesTheCompactGroupedTeamOutput() {
        val shadowChandelure = counter(
            "Shadow Chandelure", "CHANDELURE_SHADOW_FORM", 3555, "Fire Spin", "Overheat", true
        )
        val shadowReshiram = counter(
            "Shadow Reshiram", "RESHIRAM_SHADOW_FORM", 4499, "Fire Fang", "Fusion Flare", true
        )
        val regularChandelure = counter(
            "Chandelure", "CHANDELURE", 3268, "Hex", "Shadow Ball", false
        )
        val team = listOf(
            slot(shadowChandelure),
            slot(shadowReshiram),
            slot(regularChandelure)
        )
        val clipboard = RecordingClipboardManager()
        val state = RaidCountersUiState(
            visible = true,
            bossPokemonId = "TEST_BOSS",
            bossDisplayName = "Website parity test",
            bossMove1 = "PSYCHO_CUT",
            bossMove2 = "SHADOW_BALL",
            raidEndTimeMillis = System.currentTimeMillis() + 10 * 60_000L,
            source = CounterSourceId.POKE_GENIE,
            personal = PersonalRanking(
                ranked = team.map { it.counter },
                team = team,
                combinedTdo = 300.0,
                bossHp = 15_000,
                serverBacked = true
            ),
            team = team,
            dexNumbers = mapOf(
                "CHANDELURE_SHADOW_FORM" to 609,
                "RESHIRAM_SHADOW_FORM" to 643,
                "CHANDELURE" to 609
            )
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                PokemonAlertsV2Theme {
                    RaidCountersScreen(
                        state = state,
                        actions = RaidCountersActions.Noop,
                        onBack = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Time remaining").assertIsDisplayed()
        composeRule.onNodeWithText("Boss moveset · Psycho Cut / Shadow Ball")
            .assertIsDisplayed()

        composeRule.onNodeWithText("Copy for GO")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { clipboard.getText() != null }

        val copied = checkNotNull(clipboard.getText()).text
        assertEquals(
            "609,643&CP3555,CP4499,CP3268&@Fire Spin,@Fire Fang,@Hex&" +
                "@Overheat,@Fusion Flare,@Shadow Ball",
            copied
        )
    }

    @Test
    fun shadowGiratinaTeamCopiesTheRequestedSpeciesCpAndMoveGroups() {
        val team = listOf(
            slot(counter("Garchomp", "GARCHOMP", 4370, "Dragon Tail", "Breaking Swipe")),
            slot(counter("Kyurem", "KYUREM_BLACK_FORM", 5206, "Dragon Tail", "Freeze Shock")),
            slot(counter("Necrozma", "NECROZMA_DAWN_WINGS", 4634, "Shadow Claw", "Moongeist Beam")),
            slot(counter("Garchomp", "GARCHOMP", 4436, "Dragon Tail", "Breaking Swipe")),
            slot(counter("Garchomp", "GARCHOMP", 4459, "Dragon Tail", "Breaking Swipe")),
            slot(counter("Eternatus", "ETERNATUS", 4966, "Dragon Tail", "Dynamax Cannon"))
        )
        val clipboard = RecordingClipboardManager()
        val state = RaidCountersUiState(
            visible = true,
            bossPokemonId = "GIRATINA_SHADOW_FORM",
            bossDisplayName = "Shadow Giratina Altered Forme",
            bossMove1 = "DRAGON_BREATH_FAST",
            bossMove2 = "ANCIENT_POWER",
            raidEndTimeMillis = System.currentTimeMillis() + 10 * 60_000L,
            source = CounterSourceId.POKE_GENIE,
            personal = PersonalRanking(
                ranked = team.map { it.counter },
                team = team,
                combinedTdo = 600.0,
                bossHp = 15_000,
                serverBacked = true
            ),
            team = team,
            dexNumbers = mapOf(
                "GARCHOMP" to 445,
                "KYUREM_BLACK_FORM" to 646,
                "NECROZMA_DAWN_WINGS" to 800,
                "ETERNATUS" to 890
            )
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                PokemonAlertsV2Theme {
                    RaidCountersScreen(
                        state = state,
                        actions = RaidCountersActions.Noop,
                        onBack = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Copy for GO").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { clipboard.getText() != null }

        assertEquals(
            "445,646,800,890&CP4370,CP5206,CP4634,CP4436,CP4459,CP4966&" +
                "@Dragon Tail,@Shadow Claw&" +
                "@Breaking Swipe,@Freeze Shock,@Moongeist Beam,@Dynamax Cannon",
            checkNotNull(clipboard.getText()).text
        )
    }

    @Test
    fun arrivedRaidExplainsHowToSetUpARealPersonalTeam() {
        composeRule.setContent {
            PokemonAlertsV2Theme {
                RaidCountersScreen(
                    state = RaidCountersUiState(
                        visible = true,
                        bossPokemonId = "MEWTWO",
                        bossDisplayName = "Mewtwo",
                        personalTeamRequested = true,
                        source = CounterSourceId.ALL_POKEMON,
                        pokeGenieCount = 0,
                        pokebattlerUserId = null
                    ),
                    actions = RaidCountersActions.Noop,
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("Set up your recommended team").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Import a Poké Genie CSV or link Pokébattler in Settings → Raid counters. " +
                "Copy for GO is enabled only for a real personal team."
        ).assertIsDisplayed()
    }

    private fun counter(
        displayName: String,
        pokemonId: String,
        cp: Int,
        quick: String,
        charge: String,
        shadow: Boolean = false
    ) = PersonalCounter(
        owned = OwnedPokemon(
            displayName = displayName,
            form = null,
            level = 40.0,
            atkIv = 15,
            defIv = 15,
            staIv = 15,
            cp = cp,
            quickMove = quick,
            chargeMove = charge,
            shadow = shadow,
            lucky = false,
            matchKeys = listOf(pokemonId)
        ),
        pokemonId = pokemonId,
        displayName = displayName,
        fastMove = syntheticFastMove(quick),
        chargedMove = syntheticChargedMove(charge),
        movesetAssumed = false,
        dps = 10.0,
        tdo = 100.0,
        rating = 50.0,
        estimatedAttackers = 6.0
    )

    private fun slot(counter: PersonalCounter) = PersonalTeamSlot(counter, 1, listOf(counter))

    private class RecordingClipboardManager : ClipboardManager {
        private var value: AnnotatedString? = null

        override fun setText(annotatedString: AnnotatedString) {
            value = annotatedString
        }

        override fun getText(): AnnotatedString? = value
    }
}
