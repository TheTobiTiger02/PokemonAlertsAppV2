package com.example.pokemonalertsv2.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetFilterPrefsTest {

    @Test
    fun legacyAllCategoriesAutomaticallyIncludesRareAndWeather() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val widgetId = 987654
        val legacyFilters = setOf(
            "Hundo", "Nundo", "PvP", "Spawn", "Raid", "Rocket", "Quest", "Kecleon"
        )
        try {
            WidgetFilterPrefs.saveFilters(context, widgetId, legacyFilters)

            val migrated = WidgetFilterPrefs.getFilters(context, widgetId)

            assertTrue("Rare" in migrated)
            assertTrue("Weather" in migrated)
        } finally {
            WidgetFilterPrefs.removeFilters(context, widgetId)
        }
    }

    @Test
    fun customSubsetRemainsUnchanged() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val widgetId = 987655
        val customFilters = setOf("Hundo", "Raid")
        try {
            WidgetFilterPrefs.saveFilters(context, widgetId, customFilters)

            assertEquals(customFilters, WidgetFilterPrefs.getFilters(context, widgetId))
        } finally {
            WidgetFilterPrefs.removeFilters(context, widgetId)
        }
    }
}
