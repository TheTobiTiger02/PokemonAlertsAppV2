package com.example.pokemonalertsv2.widget

import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.*
import com.example.pokemonalertsv2.data.database.AppDatabase
import com.example.pokemonalertsv2.data.database.toEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UnifiedWidgetFilterTest {
    @Test fun linkedWidgetRefreshesWhileCopiedWidgetKeepsItsOwnRules() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = AlertPreferences(context.alertPreferencesDataStore)
        val original = preferences.filterStateDocument.first()
        val dao = AppDatabase.getDatabase(context).alertDao()
        val ids = listOf(994701, 994702)
        val pikachu = PokemonAlert(id = 9_947_001, name = "Widget filter QA Pikachu", pokemon = "Pikachu", type = listOf("Spawn"), area = "Filter QA", endTime = "2099-01-01T00:00:00Z")
        val eevee = pikachu.copy(id = 9_947_002, name = "Widget filter QA Eevee", pokemon = "Eevee")
        val definition = FilterDefinition(areas = FilterSelection.only(listOf("Filter QA")), spawnSpecies = FilterSelection.only(listOf("Pikachu")))
        val profile = FilterProfile("widget-qa", "Widget QA", definition)
        try {
            dao.insertAlerts(listOf(pikachu.toEntity(), eevee.toEntity()))
            preferences.updateFilterStateDocument { it.copy(profiles = it.profiles + profile) }
            WidgetConfigurationStore.save(context, ids[0], WidgetConfiguration(filterAssignment = FilterAssignment.linked(profile)))
            WidgetConfigurationStore.save(context, ids[1], WidgetConfiguration(filterAssignment = FilterAssignment.local(definition)))
            assertEquals(listOf(pikachu.id), WidgetAlertLoader.load(context, ids[0]).alerts.map { it.id })
            assertEquals(listOf(pikachu.id), WidgetAlertLoader.load(context, ids[1]).alerts.map { it.id })
            preferences.updateFilterStateDocument { document -> document.copy(profiles = document.profiles.map { if (it.id == profile.id) it.copy(definition = definition.copy(spawnSpecies = FilterSelection.only(listOf("Eevee")))) else it }) }
            AlertsWidgetProvider.requestUpdate(context)
            assertEquals(listOf(eevee.id), WidgetAlertLoader.load(context, ids[0]).alerts.map { it.id })
            assertEquals(listOf(pikachu.id), WidgetAlertLoader.load(context, ids[1]).alerts.map { it.id })
        } finally {
            ids.forEach { WidgetConfigurationStore.remove(context, it); WidgetAlertSnapshotStore.remove(it) }
            dao.deleteByUniqueIds(listOf(pikachu.uniqueId, eevee.uniqueId))
            preferences.updateFilterStateDocument { original }
        }
    }
}
