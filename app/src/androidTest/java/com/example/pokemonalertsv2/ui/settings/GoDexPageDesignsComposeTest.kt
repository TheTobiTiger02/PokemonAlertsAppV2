package com.example.pokemonalertsv2.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.data.godex.GoDexConfig
import com.example.pokemonalertsv2.data.godex.GoDexSessionState
import com.example.pokemonalertsv2.data.godex.GoDexSyncUiState
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The connected GoDex page in both new designs, on a 348dp phone at font scale 1.3. The emulator
 * has no GoDex account, so this is where the signed-in rows are seen; each step is saved to
 * the app's cache under design-shots/ for comparison.
 */
class GoDexPageDesignsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val config = GoDexConfig(
        url = "https://godex.site/public-collection/abc",
        collectionTitle = "Hundo hunt 2026",
        lastSuccessfulSyncMillis = System.currentTimeMillis() - 12 * 60_000L,
        notificationFilterEnabled = true,
        sessionCookies = "session",
        writeBackUrl = "https://godex.site/collection/hundo-hunt",
        sessionState = GoDexSessionState.AUTHENTICATED
    )

    @Test
    fun connectedPagesFitANarrowPhone() {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.3f)) {
                PokemonAlertsV2Theme {
                    Box(
                        Modifier
                            .width(348.dp)
                            .height(775.dp)
                            .background(MaterialTheme.colorScheme.background)
                            .testTag("godex_frame")
                    ) {
                        GoDexPageContent(
                            config = config,
                            needed = 37,
                            total = 412,
                            sync = GoDexSyncUiState(sessionState = GoDexSessionState.AUTHENTICATED),
                            urlInput = "",
                            onUrlInputChange = {},
                            actions = GoDexPageActions(
                                onConnect = { calls += "connect" },
                                onSync = { calls += "sync" },
                                onNotifyNeededChange = { calls += "notify:$it" },
                                onSignOut = { calls += "signOut" },
                                onOpenCollection = { calls += "collection" },
                                onDisconnect = { calls += "disconnect" }
                            )
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("37 needed of 412").assertIsDisplayed()
        composeRule.onNodeWithText("Sync now").performClick()
        composeRule.onNodeWithText("Signed in · two-way sync").performClick()
        composeRule.onNodeWithText("Change checklist").assertIsDisplayed()
        composeRule.onNodeWithText("Disconnect").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("Only notify needed").performClick()

        composeRule.runOnIdle { assertEquals(listOf("sync", "disconnect", "notify:false"), calls) }
    }

}
