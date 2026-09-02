package com.example.pokemonalertsv2.ui.settings

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.*
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class FilterStudioComposeTest {
    @get:Rule val rule = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val quests = listOf(
        FilterCatalogQuest("catch 5::pikachu", "catch 5", "pikachu", "Catch 5", "Pikachu", true),
        FilterCatalogQuest("spin 3::rare candy", "spin 3", "rare candy", "Spin 3", "Rare Candy", false)
    )

    @Test fun selectorNoneIsExplicitAndUnavailableSelectionIsRetained() {
        var saved: FilterSelection? = null
        rule.setContent { PokemonAlertsV2Theme {
            SelectionDialog("Raid tiers", listOf("1", "5", "Mega"), FilterSelection.only(listOf("Future tier")), {},
                onSave = { saved = it })
        } }
        rule.onNodeWithText("future tier").assertIsDisplayed()
        rule.onNodeWithText("Unavailable selections are preserved").assertIsDisplayed()
        rule.onNodeWithText("Done").performClick()
        assertTrue(saved!!.contains("Future tier"))
        rule.onNodeWithText("None").performClick()
        rule.onNodeWithText("Done").performClick()
        assertEquals(FilterSelection.None, saved)
    }

    @Test fun questExactPairsAndTaskRewardFacetsRemainIndependent() {
        var saved: QuestFilterRules? = null
        rule.setContent { PokemonAlertsV2Theme {
            QuestRulesDialog(QuestFilterRules(), quests, {}, { saved = it })
        } }
        rule.onNodeWithText("Catch 5 → Pikachu").performClick()
        rule.onNodeWithText("Tasks").performClick()
        rule.onNodeWithText("Spin 3").performClick()
        rule.onNodeWithText("Rewards").performClick()
        rule.onNodeWithText("Rare Candy").performClick()
        capture("quest-rewards")
        rule.onNodeWithText("Done").performClick()
        assertEquals(setOf(QuestPairRule("catch 5", "pikachu")), saved!!.exactPairs)
        assertTrue(saved!!.facetEnabled)
        assertTrue(saved!!.tasks.contains("Spin 3"))
        assertFalse(saved!!.tasks.contains("Catch 5"))
        assertTrue(saved!!.rewards.contains("Rare Candy"))
    }

    @Test fun profileDeletionWarnsAboutEveryLinkedConsumer() {
        val profile = FilterProfile("qa-profile", "QA shared", FilterDefinition())
        val document = FilterStateDocument(profiles = listOf(profile), feed = FilterAssignment.linked(profile), map = FilterAssignment.linked(profile))
        var deleted: String? = null
        rule.setContent { PokemonAlertsV2Theme {
            ProfilePickerDialog(document, {}, { _, _ -> }, { deleted = it }, { _, _, _ -> }, { listOf("Widget #42") })
        } }
        rule.onNodeWithText("Delete").performScrollTo().performClick()
        rule.onNodeWithText("Feed, Map, Widget #42 will keep independent copies of these rules before the profile is deleted.").assertIsDisplayed()
        capture("profile-delete-warning")
        rule.onNodeWithText("Delete profile").performClick()
        assertEquals(profile.id, deleted)
    }

    @Test fun editorCancelDiscardsAndApplyCommitsTheWholeDraft() {
        val context = instrumentation.targetContext
        val preferences = AlertPreferences(context.alertPreferencesDataStore)
        val original = runBlocking { preferences.filterStateDocument.first() }
        val store = ViewModelStore()
        val viewModel = SettingsViewModel(context.applicationContext as Application, SavedStateHandle())
        store.put("filter-qa", viewModel)
        var open by mutableStateOf(true)
        runBlocking { preferences.updateFilterStateDocument { FilterStateDocument() } }
        try {
            rule.setContent { PokemonAlertsV2Theme {
                if (open) FilterStudioDialog(FilterSurface.FEED, viewModel) { open = false }
            } }
            rule.onNodeWithText("None").performClick()
            capture("editor-draft")
            rule.onNodeWithText("Apply").assertIsDisplayed()
            rule.onNodeWithContentDescription("Cancel").performClick()
            assertEquals(FilterSelection.All, runBlocking { preferences.filterStateDocument.first().feed.definition.areas })
            rule.runOnIdle { open = true }
            rule.onNodeWithText("None").performClick()
            rule.onNodeWithText("Apply").performClick()
            rule.waitUntil(5000) { runBlocking { preferences.filterStateDocument.first().feed.definition.areas.mode == FilterSelectionMode.NONE } }
            assertEquals(FilterSelection.All, runBlocking { preferences.filterStateDocument.first().map.definition.areas })
        } finally {
            store.clear()
            runBlocking { preferences.updateFilterStateDocument { original } }
        }
    }

    @Test fun linkedProfileApplyWarnsBeforeUpdatingBothConsumers() {
        val context = instrumentation.targetContext
        val preferences = AlertPreferences(context.alertPreferencesDataStore)
        val original = runBlocking { preferences.filterStateDocument.first() }
        val profile = FilterProfile("qa-linked", "QA linked", FilterDefinition())
        val linked = FilterStateDocument(profiles = listOf(profile), feed = FilterAssignment.linked(profile), map = FilterAssignment.linked(profile))
        runBlocking { preferences.updateFilterStateDocument { linked } }
        val store = ViewModelStore()
        val viewModel = SettingsViewModel(context.applicationContext as Application, SavedStateHandle())
        store.put("linked-qa", viewModel)
        var open by mutableStateOf(true)
        try {
            rule.setContent { PokemonAlertsV2Theme {
                if (open) FilterStudioDialog(FilterSurface.FEED, viewModel) { open = false }
            } }
            rule.waitUntil(5000) { viewModel.filterStateDocument.value.feed.profileId == profile.id }
            rule.onNodeWithText("None").performClick()
            rule.onNodeWithText("Apply").performClick()
            rule.onNodeWithText("Update linked profile?").assertIsDisplayed()
            capture("profile-linked-apply-warning")
            assertEquals(FilterSelection.All, runBlocking { preferences.filterStateDocument.first().map.resolve(linked).areas })
            rule.onNodeWithText("Apply to all").performClick()
            rule.waitUntil(5000) {
                val document = runBlocking { preferences.filterStateDocument.first() }
                document.feed.resolve(document).areas.mode == FilterSelectionMode.NONE &&
                    document.map.resolve(document).areas.mode == FilterSelectionMode.NONE
            }
        } finally {
            store.clear()
            runBlocking { preferences.updateFilterStateDocument { original } }
        }
    }

    @Test fun advancedSelectorsRemainAccessibleAcrossThemeAndFontMatrix() {
        var dark by mutableStateOf(false)
        var fontScale by mutableFloatStateOf(1f)
        var title by mutableStateOf("Spawn species")
        val titles = listOf("Spawn species", "Rare species", "Hundo species", "Nundo species", "PvP species", "Raid species", "Raid tiers", "Rocket types", "Quest rules")
        rule.setContent {
            PokemonAlertsV2Theme(darkTheme = dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    key(title, dark, fontScale) {
                        if (title == "Quest rules") QuestRulesDialog(QuestFilterRules(), quests, {}, {})
                        else SelectionDialog(title,
                            if (title == "Raid tiers") RaidTier.entries.map { it.displayLabel }
                            else if (title == "Rocket types") DEFAULT_ROCKET_FILTER_TYPES
                            else listOf("Pikachu", "Eevee", "Mewtwo"),
                            FilterSelection.All, {}, onSave = {})
                    }
                }
            }
        }
        for (theme in listOf(false, true)) for (scale in listOf(1f, 1.5f)) for (selector in titles) {
            rule.runOnIdle { dark = theme; fontScale = scale; title = selector }
            rule.onNodeWithText("Done").assertIsDisplayed().assertHasClickAction()
            rule.onNodeWithText("Cancel").assertIsDisplayed()
            capture("${selector.replace(' ', '-')}-${if (theme) "dark" else "light"}-$scale")
        }
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        // Compose idleness does not wait for the platform dialog window fade.
        android.os.SystemClock.sleep(350)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "filter-studio-qa").apply { mkdirs() }
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        val file = File(directory, "$name.png")
        file.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
        // Keep visual evidence after Gradle uninstalls the test application.
        listOf("mkdir -p /data/local/tmp/filter-studio-qa", "cp ${file.absolutePath} /data/local/tmp/filter-studio-qa/$name.png").forEach { command ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
    }
}
