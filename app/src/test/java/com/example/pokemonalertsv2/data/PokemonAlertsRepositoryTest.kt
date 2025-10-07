package com.example.pokemonalertsv2.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.test.runTest

class PokemonAlertsRepositoryTest {

    private lateinit var preferences: FakeAlertPreferencesStore
    private lateinit var repository: PokemonAlertsRepository
    private val service = FakePokemonAlertsService()

    @Before
    fun setUp() {
        preferences = FakeAlertPreferencesStore()
        repository = PokemonAlertsRepository(service, preferences)
    }

    @Test
    fun detectNewAlerts_skipsAlreadySeen() = runTest {
        val seenAlert = sampleAlert(name = "Seen", endTime = "2025-10-07 23:59:59")
        preferences.updateSeenAlertIds(setOf(seenAlert.uniqueId))

        val newAlert = sampleAlert(name = "New", endTime = "2025-10-08 10:00:00")
        val alerts = listOf(seenAlert, newAlert)

        val freshAlerts = repository.detectNewAlerts(alerts)

        assertEquals(listOf(newAlert), freshAlerts)
    }

    @Test
    fun markAlertsAsSeen_persistsIdentifiers() = runTest {
        val alertA = sampleAlert("A")
        val alertB = sampleAlert("B", endTime = "2025-10-08 08:00:00")

        repository.markAlertsAsSeen(listOf(alertA, alertB))

        val storedIds = preferences.getSeenAlertIds()
        assertTrue(storedIds.containsAll(listOf(alertA.uniqueId, alertB.uniqueId)))
    }

    @Test
    fun fetchAlerts_returnsServiceData() = runTest {
        val alerts = listOf(sampleAlert("Service"))
        service.alerts = alerts

        val fetched = repository.fetchAlerts()

        assertEquals(alerts, fetched)
    }

    private fun sampleAlert(name: String, endTime: String = "2025-10-07 23:59:59") = PokemonAlert(
        name = name,
        description = "description $name",
        imageUrl = null,
        longitude = 8.62,
        latitude = 49.74,
        endTime = endTime,
        type = "Quest",
        thumbnailUrl = null
    )

    private class FakePokemonAlertsService : PokemonAlertsService {
        var alerts: List<PokemonAlert> = emptyList()

        override suspend fun getPokemonAlerts(): List<PokemonAlert> = alerts
    }

    private class FakeAlertPreferencesStore(initial: Set<String> = emptySet()) : AlertPreferencesStore {
        private val state = MutableStateFlow(initial)

        override val seenAlertIds: Flow<Set<String>> = state.asStateFlow()

        override suspend fun getSeenAlertIds(): Set<String> = state.value

        override suspend fun updateSeenAlertIds(alertIds: Set<String>) {
            state.value = alertIds
        }
    }
}
