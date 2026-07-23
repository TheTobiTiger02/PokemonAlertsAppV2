package com.example.pokemonalertsv2.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertPreferencesUxTest {
    @Test
    fun syncTimestampAndBrowseFilterPersist() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val preferences = AlertPreferences(dataStore)

        preferences.updateLastSuccessfulAlertSyncMillis(123_456L)
        preferences.updateSelectedAlertFilterName("HUNDOS")

        assertEquals(123_456L, preferences.lastSuccessfulAlertSyncMillis.first())
        assertEquals("HUNDOS", preferences.selectedAlertFilterName.first())
    }

    @Test
    fun mapCountdownPreferencePersistsAcrossStoreRecreation() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        AlertPreferences(dataStore).updateShowMapCountdowns(true)

        val recreatedPreferences = AlertPreferences(dataStore)

        assertEquals(true, recreatedPreferences.showMapCountdowns.first())
    }

    @Test
    fun applyingPresetIsAtomicAndPreservesFineFilters() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val preferences = AlertPreferences(dataStore)
        preferences.updateExcludedRaidTiers(setOf("Mega"))

        preferences.applyNotificationPreset(NotificationPreset.QUIET_ESSENTIALS)

        assertEquals(false, preferences.raidsNotifications.first())
        assertEquals(true, preferences.hundosNotifications.first())
        assertEquals(setOf("Mega"), preferences.excludedRaidTiers.first())
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
