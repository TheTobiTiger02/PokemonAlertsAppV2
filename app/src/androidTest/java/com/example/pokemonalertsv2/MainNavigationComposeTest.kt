package com.example.pokemonalertsv2

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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

        listOf("Alerts", "Map", "Settings").forEach { label ->
            composeRule.onNodeWithText(label)
                .assertIsDisplayed()
                .assertHasClickAction()
        }
    }

    @Test
    fun alertsDestinationExposesLiveAndHistoryTabsAndSwitchesSections() {
        waitForMainNavigation()

        composeRule.onNodeWithText("Live")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithText("History")
            .assertIsDisplayed()
            .assertHasClickAction()

        composeRule.onNodeWithText("History").performClick()
        composeRule.onNodeWithText("Past activity").assertIsDisplayed()

        composeRule.onNodeWithText("Live").performClick()
        composeRule.onNodeWithText("Nearby activity").assertIsDisplayed()
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
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            preferences = AlertPreferences(context.alertPreferencesDataStore)
            runBlocking {
                originalOnboardingCompleted = preferences.onboardingCompleted.first()
                preferences.setOnboardingCompleted(true)
            }
        }

        @AfterClass
        @JvmStatic
        fun restoreOnboardingCompletionState() {
            val originalValue = originalOnboardingCompleted ?: return
            runBlocking {
                preferences.setOnboardingCompleted(originalValue)
            }
        }
    }
}
