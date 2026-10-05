package com.example.pokemonalertsv2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.espresso.Espresso
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

        listOf("Alerts", "Map", "Events", "Tools").forEach { label ->
            composeRule.onNodeWithText(label)
                .assertIsDisplayed()
                .assertHasClickAction()
        }
    }

    @Test
    fun historyIsASectionOfTheAlertsTab() {
        waitForMainNavigation()

        composeRule.onAllNodesWithText("History").onFirst().performClick()
        composeRule.onNodeWithText("History").assertIsSelected()

        composeRule.onNodeWithText("Live").performClick()
        composeRule.onNodeWithText("Live").assertIsSelected()
    }

    @Test
    fun alertsIntentSwitchesExistingTaskToAlertsRoot() {
        waitForMainNavigation()

        composeRule.onNodeWithText("Map").performClick()
        composeRule.onNodeWithTag("map_full_content").assertIsDisplayed()

        composeRule.runOnIdle {
            composeRule.activity.handleNavigationIntent(
                MainActivity.createAlertsIntent(composeRule.activity)
            )
        }
        composeRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) {
            runCatching { composeRule.onNodeWithText("Live").fetchSemanticsNode() }.isSuccess
        }

        composeRule.onNodeWithText("Live").assertIsDisplayed()
    }

    @Test
    fun mapIntentOpensRootMapWithoutToolbarBack() {
        openMapFromIntent()

        composeRule.onNodeWithTag("map_full_content").assertIsDisplayed()
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

        composeRule.onNodeWithTag("open_settings").performClick()
        listOf(
            "Appearance",
            "Filters",
            "GoDex checklist",
            "Notifications & permissions",
            "Backup & about"
        ).forEach { label ->
            composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed().assertHasClickAction()
        }
        composeRule.onNodeWithText("Theme").assertDoesNotExist()
        // Settings opens from a gear on top of the current tab, so its overview has a Back
        // that returns there.
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed().assertHasClickAction()

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
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
    }

    @Test
    fun toolsHuntOpensThePickerAndBackReturnsToTools() {
        openTools()
        composeRule.onAllNodesWithText("Hunt").onFirst().performClick()
        // Straight to the picker: "What are you hunting?", or editing a hunt already running.
        composeRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) {
            listOf("What are you hunting?", "Edit hunt targets").any { title ->
                composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
            }
        }
        Espresso.pressBack()
        waitForToolsScreen()
        assertFalse(composeRule.activity.isFinishing)
    }

    @Test
    fun toolsHuntHistoryOpensItsScreenAndBackReturnsToTools() {
        openTools()
        composeRule.onNodeWithText("Hunt history").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) { huntHistoryResumed() }
        Espresso.pressBack()
        composeRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) { !huntHistoryResumed() }
        waitForToolsScreen()
    }

    @Test
    fun toolsInsightsBackReturnsToTools() {
        openTools()
        composeRule.onNodeWithText("Spawn insights").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) {
            // Tools stays composed behind the Alerts tab, so wait for insights itself.
            composeRule.onAllNodesWithContentDescription("Back to history").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        waitForToolsScreen()
    }

    @Test
    fun toolsGoDexBackReturnsToTools() {
        openTools()
        composeRule.onNodeWithText("GoDex checklist").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("GoDex Hundo checklist").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        waitForToolsScreen()
        composeRule.onNodeWithText("Appearance").assertDoesNotExist()
    }

    @Test
    fun settingsSubpageSurvivesActivityRecreation() {
        waitForMainNavigation()

        composeRule.onNodeWithTag("open_settings").performClick()
        composeRule.onNodeWithText("Appearance").performClick()
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
            // "Import" when there is no roster yet, "Replace roster" when one would be overwritten.
            val confirmLabel = if (originalSettings.pokeGenieCount > 0) "Replace roster" else "Import"
            composeRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithText(confirmLabel).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(confirmLabel).performClick()
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
            runCatching { composeRule.onNodeWithTag("map_full_content").fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNodeWithTag("map_full_content").assertIsDisplayed()
    }

    private fun huntHistoryResumed(): Boolean {
        var resumed = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            resumed = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                .any { it is com.example.pokemonalertsv2.hunt.HuntHistoryActivity }
        }
        return resumed
    }

    private fun openTools() {
        waitForMainNavigation()
        composeRule.onAllNodesWithText("Tools").onFirst().performClick()
        waitForToolsScreen()
    }

    private fun waitForToolsScreen() {
        composeRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) {
            runCatching { composeRule.onNodeWithTag("tools_screen").fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNodeWithTag("tools_screen").assertIsDisplayed()
    }

    private fun waitForAlertsScreen() {
        composeRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MILLIS) {
            runCatching { composeRule.onNodeWithText("Live").fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNodeWithText("Live").assertIsDisplayed()
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
