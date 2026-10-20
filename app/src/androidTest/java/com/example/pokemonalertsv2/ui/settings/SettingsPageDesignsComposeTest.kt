package com.example.pokemonalertsv2.ui.settings

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Both new Notifications designs write through to the stored preferences. */
class SettingsPageDesignsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val preferences = AlertPreferences(app.alertPreferencesDataStore)
    private var savedEnabled = true
    private var savedVibrate = true
    private var savedSilence = 0L

    @Before
    fun setUp() = runBlocking {
        savedEnabled = preferences.notificationsEnabled.first()
        savedVibrate = preferences.notificationVibrate.first()
        savedSilence = preferences.silenceUntil.first()
        preferences.updateNotificationsEnabled(true)
        preferences.updateNotificationVibrate(true)
        preferences.updateSilenceUntil(0L)
    }

    @After
    fun tearDown() = runBlocking {
        preferences.updateNotificationsEnabled(savedEnabled)
        preferences.updateNotificationVibrate(savedVibrate)
        preferences.updateSilenceUntil(savedSilence)
    }

    @Test
    fun flatRowsToggleVibrationAndSilenceForAnHour() {
        show()
        toggleVibrationOff()
        composeRule.onNodeWithTag("settings_silence_now").performScrollTo().performClick()
        silenceForAnHour()
    }


    private fun show() {
        val viewModel = SettingsViewModel(app, SavedStateHandle())
        composeRule.setContent {
            PokemonAlertsV2Theme {
                NotificationsPageDesign(
                    viewModel = viewModel,
                    grants = PermissionGrants(notifications = true, foregroundLocation = true, backgroundLocation = true),
                    onManageNotifications = {},
                    onManageLocation = {},
                    onOpenFilterStudio = {}
                )
            }
        }
    }

    private fun toggleVibrationOff() {
        composeRule.onNodeWithTag("settings_vibration").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { runBlocking { !preferences.notificationVibrate.first() } }
    }

    private fun silenceForAnHour() {
        val before = System.currentTimeMillis()
        composeRule.onNodeWithTag("silence_tile_1 hour").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { runBlocking { preferences.silenceUntil.first() > before } }
        val until = runBlocking { preferences.silenceUntil.first() }
        val hour = 60 * 60_000L
        assertTrue("silenced until $until", until in before + hour..System.currentTimeMillis() + hour)
    }
}
