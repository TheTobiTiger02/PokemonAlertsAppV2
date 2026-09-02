package com.example.pokemonalertsv2.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.ui.alerts.FILTERABLE_ALERT_CATEGORIES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetFilterPrefsTest {

    @Test
    fun legacyAllowListMigratesToMutedComplement() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val widgetId = 987654
        // Old semantics: this widget showed ONLY hundos and raids.
        val legacyAllowList = setOf("Hundo", "Raid")
        try {
            WidgetFilterPrefs.saveFilters(context, widgetId, legacyAllowList)

            val muted = WidgetFilterPrefs.getFilters(context, widgetId)

            val expectedMuted = FILTERABLE_ALERT_CATEGORIES
                .filter { it.name !in setOf("HUNDO", "RAID") }
                .map { it.name }
                .toSet()
            assertEquals(expectedMuted, muted)
        } finally {
            WidgetFilterPrefs.removeFilters(context, widgetId)
        }
    }

    @Test
    fun migratedSetRoundTripsWithoutReMigration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val widgetId = 987655
        val mutedCategories = FILTERABLE_ALERT_CATEGORIES
            .filter { it != com.example.pokemonalertsv2.ui.alerts.AlertCategory.QUEST }
            .map { it.name }
            .toSet()
        try {
            WidgetFilterPrefs.saveFilters(context, widgetId, mutedCategories)
            assertEquals(mutedCategories, WidgetFilterPrefs.getFilters(context, widgetId))

            // A second read must be stable — migration must not run twice.
            assertEquals(mutedCategories, WidgetFilterPrefs.getFilters(context, widgetId))
            // Quest was explicitly excluded from the muted set above, so it stays enabled.
            assertFalse("QUEST" in WidgetFilterPrefs.getFilters(context, widgetId))
        } finally {
            WidgetFilterPrefs.removeFilters(context, widgetId)
        }
    }

    @Test
    fun emptySetMeansShowEverything() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val widgetId = 987656
        try {
            WidgetFilterPrefs.saveFilters(context, widgetId, emptySet())
            assertEquals(emptySet<String>(), WidgetFilterPrefs.getFilters(context, widgetId))
        } finally {
            WidgetFilterPrefs.removeFilters(context, widgetId)
        }
    }
}
