package com.example.pokemonalertsv2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.MapStylePreference
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.data.counters.RaidCounterPreferences
import com.example.pokemonalertsv2.data.database.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

        composeRule.runOnIdle {
            composeRule.activity.handleNavigationIntent(
                MainActivity.createAlertsIntent(composeRule.activity)
            )
        }
        composeRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) {
            runCatching { composeRule.onNodeWithText("Pokémon Alerts").fetchSemanticsNode() }.isSuccess
        }

        composeRule.onNodeWithText("Pokémon Alerts").assertIsDisplayed()
    }

    @Test
    fun mapIntentOpensRootMapWithoutToolbarBack() {
        openMapFromIntent()

        composeRule.onNodeWithText("Alerts Map").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()
        composeRule.onNodeWithText("Map").assertIsSelected()
    }

    @Test
    fun mapIntentSystemBackReturnsToAlertsWithoutFinishingActivity() {
        openMapFromIntent()

        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        waitForAlertsScreen()
        assertFalse(composeRule.activity.isFinishing)
    }

    @Test
    fun persistedMapStylesUseTheSameBackNavigation() {
        val originalStyle = runBlocking { preferences.mapStylePreference.first() }
        try {
            listOf(
                MapStylePreference.GOOGLE_STANDARD,
                MapStylePreference.GOOGLE_SATELLITE,
                MapStylePreference.OPENSTREETMAP
            ).forEach { style ->
                runBlocking { preferences.updateMapStylePreference(style) }
                openMapFromIntent()
                composeRule.runOnIdle {
                    composeRule.activity.onBackPressedDispatcher.onBackPressed()
                }
                waitForAlertsScreen()
            }
        } finally {
            runBlocking { preferences.updateMapStylePreference(originalStyle) }
        }
    }

    @Test
    fun settingsUsesOverviewAndFocusedSubpages() {
        waitForMainNavigation()

        composeRule.onNodeWithText("Settings").performClick()
        listOf(
            "Appearance & behavior",
            "Filters",
            "GoDex checklist",
            "Notifications",
            "About & updates"
        ).forEach { label ->
            composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed().assertHasClickAction()
        }
        composeRule.onNodeWithText("Theme").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()

        composeRule.onNodeWithText("Filters").performScrollTo().performClick()
        composeRule.onNodeWithText("Filter Studio").assertIsDisplayed()
        composeRule.onNodeWithText("Feed").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Basic rules").assertIsDisplayed()
        composeRule.onNodeWithText("Apply").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Cancel").performClick()
        composeRule.onNodeWithText("GoDex Hundo checklist").assertDoesNotExist()
        composeRule.activity.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.onNodeWithText("GoDex checklist").performClick()
        composeRule.onNodeWithText("GoDex Hundo checklist").performScrollTo().assertIsDisplayed()
        val isDisconnected = composeRule
            .onAllNodesWithText("Public GoDex collection URL")
            .fetchSemanticsNodes()
            .isNotEmpty()
        if (isDisconnected) {
            composeRule.onNodeWithText("Public GoDex collection URL").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Connect").assertHasClickAction()
        } else {
            val collectionAction = if (
                composeRule.onAllNodesWithText("Review needed").fetchSemanticsNodes().isNotEmpty()
            ) {
                "Review needed"
            } else {
                "View collection"
            }
            composeRule.onNodeWithText(collectionAction)
                .performScrollTo()
                .assertIsDisplayed()
                .assertHasClickAction()
                .performClick()
            composeRule.onNodeWithText("Search Pokémon, form, number, or key")
                .assertIsDisplayed()
            composeRule.activity.runOnUiThread {
                composeRule.activity.onBackPressedDispatcher.onBackPressed()
            }
            composeRule.onNodeWithText("GoDex Hundo checklist").assertIsDisplayed()
        }
        composeRule.activity.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
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

    @Test
    fun sharedPokeGenieCsvOpensReviewAndReplacesOnlyAfterConfirmation() {
        waitForMainNavigation()
        val activity = composeRule.activity
        val database = AppDatabase.getDatabase(activity)
        val preferences = RaidCounterPreferences(activity.alertPreferencesDataStore)
        val originalRows = runBlocking { database.pokeGenieDao().getAll() }
        val originalSettings = runBlocking { preferences.settings.first() }

        fun shareIntent(fileName: String): Intent {
            val file = activity.cacheDir.resolve(fileName).apply {
                writeText("Name,CP,Fast Move,Charged Move\nMewtwo,2387,Psycho Cut,Psystrike\n")
            }
            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                file
            )
            return Intent(Intent.ACTION_SEND)
                .setType("text/csv")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            val cancelIntent = shareIntent("pokegenie-share-cancel.csv")
            composeRule.runOnIdle {
                assertTrue(activity.handleExternalCsvIntent(cancelIntent))
                assertFalse(activity.handleExternalCsvIntent(cancelIntent))
            }
            composeRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithText("Review CSV import").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Raid counters").assertIsDisplayed()
            composeRule.onNodeWithText("Review CSV import").assertIsDisplayed()
            composeRule.onNodeWithText("1 Pokémon rows are ready to import.").assertIsDisplayed()
            composeRule.onNodeWithText("Cancel").performClick()
            composeRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithText("Review CSV import").fetchSemanticsNodes().isEmpty()
            }
            assertEquals(originalRows.size, runBlocking { database.pokeGenieDao().count() })

            val confirmIntent = shareIntent("pokegenie-share-confirm.csv")
            composeRule.runOnIdle {
                assertTrue(activity.handleExternalCsvIntent(confirmIntent))
            }
            composeRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithText("Replace roster").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Replace roster").performClick()
            composeRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) {
                runBlocking { database.pokeGenieDao().count() } == 1
            }
            assertEquals(1, runBlocking { database.pokeGenieDao().count() })
        } finally {
            runBlocking {
                database.pokeGenieDao().replaceAll(originalRows)
                if (originalSettings.pokeGenieCount > 0) {
                    preferences.recordPokeGenieImport(
                        fileName = originalSettings.pokeGenieFileName,
                        rowCount = originalSettings.pokeGenieCount,
                        matchedCount = originalSettings.pokeGenieMatchedCount,
                        timestamp = originalSettings.pokeGenieImportedAtMillis
                    )
                } else {
                    preferences.clearPokeGenie()
                }
            }
        }
    }

    private fun waitForMainNavigation() {
        composeRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) {
            runCatching {
                composeRule.onNodeWithText("Alerts").fetchSemanticsNode()
            }.isSuccess
        }
    }

    private fun openMapFromIntent() {
        waitForMainNavigation()
        composeRule.activity.handleNavigationIntent(
            MainActivity.createMapIntent(composeRule.activity)
        )
        composeRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) {
            runCatching { composeRule.onNodeWithText("Alerts Map").fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNodeWithText("Alerts Map").assertIsDisplayed()
    }

    private fun waitForAlertsScreen() {
        composeRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) {
            runCatching { composeRule.onNodeWithText("Pokémon Alerts").fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNodeWithText("Pokémon Alerts").assertIsDisplayed()
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
