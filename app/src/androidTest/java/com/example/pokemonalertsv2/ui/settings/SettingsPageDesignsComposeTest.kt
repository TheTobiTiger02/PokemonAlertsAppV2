package com.example.pokemonalertsv2.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.FilterAlertType
import com.example.pokemonalertsv2.data.NotificationContentPreferences
import com.example.pokemonalertsv2.data.NotificationField
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
    private var savedContent = NotificationContentPreferences()

    @Before
    fun setUp() = runBlocking {
        savedEnabled = preferences.notificationsEnabled.first()
        savedVibrate = preferences.notificationVibrate.first()
        savedSilence = preferences.silenceUntil.first()
        savedContent = preferences.notificationContent.first()
        preferences.updateNotificationContent { NotificationContentPreferences() }
        preferences.updateNotificationsEnabled(true)
        preferences.updateNotificationVibrate(true)
        preferences.updateSilenceUntil(0L)
    }

    @After
    fun tearDown() = runBlocking {
        preferences.updateNotificationsEnabled(savedEnabled)
        preferences.updateNotificationVibrate(savedVibrate)
        preferences.updateSilenceUntil(savedSilence)
        preferences.updateNotificationContent { savedContent }
    }

    @Test
    fun notificationContentPersistsSeparateViewsAndResetsOneCategory() {
        show()
        composeRule.onNodeWithTag("settings_notification_content").performScrollTo().performClick()
        composeRule.onNodeWithTag("notification_category_RAID").performScrollTo().performClick()
        composeRule.onNodeWithTag("notification_field_WEATHER").performScrollTo().assertIsSelected().performClick()
        composeRule.waitUntil(5_000) {
            runBlocking { NotificationField.WEATHER !in preferences.notificationContent.first().fields(FilterAlertType.RAID, false) }
        }
        composeRule.onNodeWithTag("notification_view_expanded").performScrollTo().performClick()
        composeRule.onNodeWithTag("notification_field_WEATHER").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("notification_preview_body", useUnmergedTree = true).assertTextContains("320 m", substring = true)
        composeRule.onNodeWithTag("notification_image_NONE").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            runBlocking { preferences.notificationContent.first().imageStyle(FilterAlertType.RAID) == com.example.pokemonalertsv2.data.NotificationImageStyle.NONE }
        }
        composeRule.onNodeWithTag("notification_title_VENUE").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            runBlocking { com.example.pokemonalertsv2.data.NotificationTitlePart.VENUE in preferences.notificationContent.first().titleParts(FilterAlertType.RAID) }
        }
        composeRule.onAllNodesWithTag("notification_preview_in_range").assertCountEquals(0)
        composeRule.onNodeWithTag("notification_content_reset").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            runBlocking { preferences.notificationContent.first() == NotificationContentPreferences() }
        }
        // The preview and the test button are pinned above the scrolling choices.
        composeRule.onNodeWithTag("notification_preview_state_in_range").assertIsDisplayed()
        composeRule.onNodeWithTag("notification_test_send").assertIsDisplayed()
        com.example.pokemonalertsv2.notifications.captureNotificationProof(app, "content-settings")
    }

    @Test
    fun flatRowsToggleVibrationAndSilenceForAnHour() {
        show()
        toggleVibrationOff()
        composeRule.onNodeWithTag("settings_silence_now").performScrollTo().performClick()
        silenceForAnHour()
    }


    @Test
    fun silenceOffersAnyLengthAndAnyDateAndQuietHoursKeepFromTo() {
        show()
        composeRule.onNodeWithTag("settings_silence_now").performScrollTo().performClick()
        composeRule.onNodeWithTag("silence_tile_For…").performClick()
        composeRule.onNodeWithText("Silence for").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("silence_tile_Until…").performClick()
        composeRule.onNodeWithText("Silence until").assertIsDisplayed()
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
                    onOpenFilterStudio = {},
                    modifier = Modifier.statusBarsPadding()
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
