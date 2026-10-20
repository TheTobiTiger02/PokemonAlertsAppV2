package com.example.pokemonalertsv2.ui.settings

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.godex.GoDexConfig
import com.example.pokemonalertsv2.data.godex.GoDexSessionState
import com.example.pokemonalertsv2.data.godex.GoDexSyncUiState
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Walks every page of both new Settings designs at the default phone size and at a 348dp phone
 * with font scale 1.3, saving each step to the app's cache under design-shots/. It is how the
 * designs are compared when the shared emulator is too busy for live screenshots, and it fails
 * if a row the walk needs is missing at either size.
 */
class SettingsPageDesignCaptures {
    @get:Rule
    val composeRule = createComposeRule()

    private val connected = GoDexConfig(
        url = "https://godex.site/public-collection/abc",
        collectionTitle = "Hundo hunt 2026",
        lastSuccessfulSyncMillis = System.currentTimeMillis() - 12 * 60_000L,
        notificationFilterEnabled = true,
        sessionCookies = "session",
        writeBackUrl = "https://godex.site/collection/hundo-hunt",
        sessionState = GoDexSessionState.AUTHENTICATED
    )

    @Test
    fun walkBothDesignsAtBothSizes() {
        val viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), SavedStateHandle())
        var narrow by mutableStateOf(false)
        var option by mutableIntStateOf(SETTINGS_DESIGN_FLAT)
        var godex by mutableStateOf(false)
        var config by mutableStateOf(GoDexConfig())
        val noop = GoDexPageActions({}, {}, {}, {}, {}, {})
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = if (narrow) 1.3f else 1f)) {
                PokemonAlertsV2Theme {
                    Box(
                        Modifier
                            .width(if (narrow) 348.dp else 412.dp)
                            .height(if (narrow) 775.dp else 840.dp)
                            .background(MaterialTheme.colorScheme.background)
                            .testTag(FRAME)
                    ) {
                        if (godex) {
                            GoDexPageContent(
                                option = option,
                                config = config,
                                needed = if (config.isConnected) 37 else 0,
                                total = if (config.isConnected) 412 else 0,
                                sync = GoDexSyncUiState(sessionState = config.sessionState),
                                urlInput = "",
                                onUrlInputChange = {},
                                actions = noop
                            )
                        } else {
                            NotificationsPageDesign(
                                option = option,
                                viewModel = viewModel,
                                grants = PermissionGrants(notifications = true, foregroundLocation = true, backgroundLocation = false),
                                onManageNotifications = {},
                                onManageLocation = {},
                                onOpenFilterStudio = {}
                            )
                        }
                    }
                }
            }
        }

        for (size in listOf("def", "narrow")) {
            narrow = size == "narrow"
            for (design in listOf(SETTINGS_DESIGN_FLAT, SETTINGS_DESIGN_CARDS)) {
                val tag = if (design == SETTINGS_DESIGN_FLAT) "A" else "B"
                option = design
                godex = false
                save("${tag}_notif_root_$size")
                if (design == SETTINGS_DESIGN_FLAT) {
                    composeRule.onNodeWithTag(FRAME).performTouchInput { swipeUp() }
                    save("${tag}_notif_root2_$size")
                    composeRule.onNodeWithTag("settings_silence_now").performScrollTo().performClick()
                    save("${tag}_notif_silence_$size")
                    back()
                } else {
                    composeRule.onNodeWithTag("settings_card_delivery").performClick()
                    save("${tag}_notif_delivery_$size")
                    back()
                    composeRule.onNodeWithTag("settings_card_quiet").performClick()
                    save("${tag}_notif_quiet_$size")
                    back()
                }

                godex = true
                config = GoDexConfig()
                save("${tag}_godex_off_$size")
                config = connected
                save("${tag}_godex_on_$size")
                if (design == SETTINGS_DESIGN_FLAT) {
                    composeRule.onNodeWithText("Signed in · two-way sync").performScrollTo().performClick()
                    save("${tag}_godex_account_$size")
                    back()
                } else {
                    composeRule.onNodeWithTag("settings_card_checklist").performClick()
                    save("${tag}_godex_checklist_$size")
                    back()
                    composeRule.onNodeWithTag("settings_card_account").performClick()
                    save("${tag}_godex_account_$size")
                    back()
                }
            }
        }
    }

    private fun back() {
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
    }

    private fun save(name: String) {
        composeRule.waitForIdle()
        // PixelCopy times out now and then on a busy emulator; a second try usually lands.
        val bitmap = (1..3).firstNotNullOfOrNull {
            runCatching { composeRule.onNodeWithTag(FRAME).captureToImage().asAndroidBitmap() }.getOrNull()
        } ?: composeRule.onNodeWithTag(FRAME).captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "design-shots")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val FRAME = "capture_frame"
    }
}
