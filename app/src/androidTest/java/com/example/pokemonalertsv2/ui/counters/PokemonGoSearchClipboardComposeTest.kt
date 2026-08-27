package com.example.pokemonalertsv2.ui.counters

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.junit4.createComposeRule
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
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Pixel/device check for the actual Copy for GO clipboard path. */
@Suppress("DEPRECATION")
class PokemonGoSearchClipboardComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun copyForGoMatchesTheNumberedWebsiteOutput() {
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

        composeRule.onNodeWithText("Copy for GO")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { clipboard.getText() != null }

        val copied = checkNotNull(clipboard.getText()).text
        assertEquals(2_316, copied.length)
        assertEquals(100, copied.count { it == '&' } + 1)
        assertEquals(
            "45b87c0087b6da81dcd75b9247bfe1f75f3b8614e2ffd07ac4962309475b1a66",
            copied.sha256()
        )
    }

    private fun counter(
        displayName: String,
        pokemonId: String,
        cp: Int,
        quick: String,
        charge: String,
        shadow: Boolean
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

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private class RecordingClipboardManager : ClipboardManager {
        private var value: AnnotatedString? = null

        override fun setText(annotatedString: AnnotatedString) {
            value = annotatedString
        }

        override fun getText(): AnnotatedString? = value
    }
}
