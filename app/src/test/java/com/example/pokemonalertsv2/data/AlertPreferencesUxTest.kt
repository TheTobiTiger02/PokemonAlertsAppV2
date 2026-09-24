package com.example.pokemonalertsv2.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test
    fun cardModeDefaultsToRichAndPersists() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val preferences = AlertPreferences(dataStore)
        assertEquals(CardDisplayMode.RICH, preferences.cardDisplayMode.first())

        preferences.updateCardDisplayMode(CardDisplayMode.COMPACT)
        assertEquals(CardDisplayMode.COMPACT, AlertPreferences(dataStore).cardDisplayMode.first())
    }

    @Test
    fun firstUseCommitsChoicesAndLinkedRulesTogether() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val preferences = AlertPreferences(dataStore)

        preferences.completeOnboardingSetup("Darmstadt", 3_000, NotificationPreset.EVERYTHING)
        assertEquals(1, dataStore.updateCount)

        val document = preferences.filterStateDocument.first()
        val starter = document.profiles.single()
        assertEquals(STARTER_PROFILE_ID, starter.id)
        assertEquals(starter.id, document.feed.profileId)
        assertEquals(starter.id, document.map.profileId)
        assertEquals(starter.id, document.notifications.profileId)
        assertEquals(FilterAssignmentMode.LINKED, document.feed.mode)
        assertEquals("Darmstadt", preferences.selectedArea.first())
        assertEquals(3_000, preferences.maxDistance.first())
        assertEquals(true, preferences.onboardingCompleted.first())
    }

    @Test
    fun notificationIntensityOverridesOnlyNotifications() = runTest {
        val preferences = AlertPreferences(InMemoryPreferencesDataStore())
        preferences.completeOnboardingSetup("All", 0, NotificationPreset.QUIET_ESSENTIALS)

        val document = preferences.filterStateDocument.first()
        assertEquals(FilterAssignmentMode.LINKED, document.feed.mode)
        assertEquals(FilterAssignmentMode.LINKED, document.map.mode)
        assertEquals(FilterAssignmentMode.LOCAL, document.notifications.mode)
        assertNotEquals(document.feed.resolve(document), document.notifications.resolve(document))
    }

    @Test
    fun existingFilterDocumentSurvivesReopenedOnboarding() = runTest {
        val preferences = AlertPreferences(InMemoryPreferencesDataStore())
        val custom = FilterStateDocument(
            feed = FilterAssignment.local(FilterDefinition(maxDistanceMeters = 8_000)),
            map = FilterAssignment.local(FilterDefinition(maxDistanceMeters = 500))
        )
        preferences.updateFilterStateDocument { custom }

        preferences.completeOnboardingSetup("Darmstadt", 3_000, NotificationPreset.EVERYTHING)

        assertEquals(custom, preferences.filterStateDocument.first())
    }

    @Test
    fun existingDefaultFilterDocumentAlsoSurvivesReopenedOnboarding() = runTest {
        val preferences = AlertPreferences(InMemoryPreferencesDataStore())
        val existing = FilterStateDocument()
        preferences.updateFilterStateDocument { existing }

        preferences.completeOnboardingSetup("Darmstadt", 3_000, NotificationPreset.EVERYTHING)

        assertEquals(existing, preferences.filterStateDocument.first())
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        var updateCount = 0
            private set
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            updateCount++
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
