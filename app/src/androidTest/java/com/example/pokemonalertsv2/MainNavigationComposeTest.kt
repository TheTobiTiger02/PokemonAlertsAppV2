package com.example.pokemonalertsv2

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

class MainNavigationComposeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun primaryDestinationsAreVisibleAndClickable() {
        waitForMainNavigation()

        listOf("Alerts", "History", "Map", "Settings").forEach { label ->
            composeRule.onNodeWithText(label)
                .assertIsDisplayed()
                .assertHasClickAction()
        }
    }

    @Test
    fun historyIsAnIndependentRootDestination() {
        waitForMainNavigation()

        composeRule.onAllNodesWithText("History").onFirst().performClick()
        composeRule.onNodeWithText("Alert History").assertIsDisplayed()

        composeRule.onNodeWithText("Alerts").performClick()
        composeRule.onNodeWithText("Pokémon Alerts").assertIsDisplayed()
    }

    @Test
    fun alertsIntentSwitchesExistingTaskToAlertsRoot() {
        waitForMainNavigation()

        composeRule.onNodeWithText("Map").performClick()
        composeRule.onNodeWithText("Alerts Map").assertIsDisplayed()

        composeRule.activity.handleNavigationIntent(
            MainActivity.createAlertsIntent(composeRule.activity)
        )
        composeRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) {
            runCatching { composeRule.onNodeWithText("Pokémon Alerts").fetchSemanticsNode() }.isSuccess
        }

        composeRule.onNodeWithText("Pokémon Alerts").assertIsDisplayed()
    }

    @Test
    fun settingsUsesOverviewAndFocusedSubpages() {
        waitForMainNavigation()

        composeRule.onNodeWithText("Settings").performClick()
        listOf(
            "Appearance & behavior",
            "Alert filters",
            "Notifications",
            "About & updates"
        ).forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed().assertHasClickAction()
        }
        composeRule.onNodeWithText("Theme").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()

        composeRule.onNodeWithText("Alert filters").performClick()
        composeRule.onNodeWithText("Maximum Distance").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Appearance & behavior").assertIsDisplayed()
    }

    @Test
    fun settingsSubpageSurvivesActivityRecreation() {
        waitForMainNavigation()

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Appearance & behavior").performClick()
        composeRule.onNodeWithText("Theme").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) {
            runCatching { composeRule.onNodeWithText("Theme").fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNodeWithText("Theme").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    private fun waitForMainNavigation() {
        composeRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) {
            runCatching {
                composeRule.onNodeWithText("Alerts").fetchSemanticsNode()
            }.isSuccess
        }
    }

    private companion object {
        const val NAVIGATION_TIMEOUT_MILLIS = 10_000L

        private lateinit var preferences: AlertPreferences
        private var originalOnboardingCompleted: Boolean? = null

        @BeforeClass
        @JvmStatic
        fun completeOnboardingBeforeLaunchingActivity() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            preferences = AlertPreferences(context.alertPreferencesDataStore)
            runBlocking {
                originalOnboardingCompleted = preferences.onboardingCompleted.first()
                preferences.setOnboardingCompleted(true)
            }
            listOf(
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ).forEach { permission ->
                if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
                }
            }
        }

        @AfterClass
        @JvmStatic
        fun restoreOnboardingCompletionState() {
            originalOnboardingCompleted?.let { originalValue ->
                runBlocking { preferences.setOnboardingCompleted(originalValue) }
            }
        }
    }
}
