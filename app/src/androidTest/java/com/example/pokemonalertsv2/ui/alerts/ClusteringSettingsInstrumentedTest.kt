package com.example.pokemonalertsv2.ui.alerts

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.*
import com.example.pokemonalertsv2.data.backup.SettingsBackup
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ClusteringSettingsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun preferenceRoundTripCustomRetentionResetAndBackup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "clustering-${System.nanoTime()}.preferences_pb")
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        try {
            val prefs = MapClusteringPreferences(store)
            assertEquals(MapClusteringConfig(), prefs.settings.first().config)
            val custom = MapClusteringConfig(distanceDp = 17, closeLimit = 1750)
            prefs.customize(custom)
            prefs.select(MapClusteringPreset.PERFORMANCE.name)
            assertEquals(custom, prefs.settings.first().custom)
            prefs.select("CUSTOM")
            assertEquals(custom, prefs.settings.first().config)
            val backup = SettingsBackup.parse(SettingsBackup.export(store.data.first(), 1L)).getOrThrow()
            assertTrue(backup.entries.containsKey(MapClusteringPreferences.KEY.name))
            // Round-trip the exact stored JSON using the same decoder used after process restart.
            assertEquals(custom, MapClusteringPreferences.decode(backup.entries.getValue(MapClusteringPreferences.KEY.name).value).config)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            val reopenedScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            try {
                val reopened = MapClusteringPreferences(PreferenceDataStoreFactory.create(
                    scope = reopenedScope, produceFile = { file }))
                assertEquals(custom, reopened.settings.first().config)
                reopened.reset()
                assertEquals(MapClusteringSettings(), reopened.settings.first())
            } finally { reopenedScope.coroutineContext[Job]!!.cancelAndJoin() }
        } finally { scope.cancel(); file.delete() }
    }

    @Test fun mainAlertDetailsExposeQuestAndItsFullDetailAction() {
        val rocket = PokemonAlert(id = 10, name = "Rocket at Test Stop", type = listOf("Rocket"),
            latitude = 49.87, longitude = 8.65, endTime = "2099-01-01T00:00:00Z")
        val quest = rocket.copy(id = 11, name = "Quest", type = listOf("Quest"),
            questTask = "Catch 5 Pokemon", questReward = "500 Stardust")
        var opened: PokemonAlert? = null
        rule.setContent { PokemonAlertsV2Theme {
            MapAlertDetailContent(alert = rocket, distanceInfo = null, onDismiss = {}, isGoing = false,
                onGoing = {}, onOpenMaps = {}, onShare = {}, onOpenFullDetail = {},
                companions = listOf(quest), onOpenCompanion = { opened = it }, goDexAction = {})
        } }
        rule.onNodeWithText("Also at this stop").assertIsDisplayed()
        rule.onNodeWithText("Catch 5 Pokemon").assertIsDisplayed()
        rule.onNodeWithText("500 Stardust").assertIsDisplayed()
        rule.onNodeWithTag("same_stop_${quest.uniqueId}").performClick()
        rule.runOnIdle { assertEquals(quest, opened) }
    }

    @Test fun presetsAndAdvancedControlsAreReachable() {
        // The controls used to open from a button of their own. They are a section of the
        // map panel now, collapsed until its header is tapped, and there is no Done to
        // dismiss -- the panel itself is the way out.
        rule.setContent { PokemonAlertsV2Theme { MapClusteringSettingsSection() } }
        rule.onNodeWithText("Density & grouping").performClick()
        rule.onNodeWithText("Overlap only").assertIsDisplayed()
        rule.onNodeWithText("Same-location stacks").assertIsDisplayed()
        // Below the fold in a section rendered on its own, so existence is the assertion.
        rule.onNodeWithText("Advanced").assertExists()
        rule.onNodeWithText("Reset").assertExists()
    }
}
