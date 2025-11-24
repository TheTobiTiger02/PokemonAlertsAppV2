package com.example.pokemonalertsv2.data

import com.example.pokemonalertsv2.data.database.AlertDao
import com.example.pokemonalertsv2.data.database.AlertEntity
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
    private lateinit var dao: FakeAlertDao
    private lateinit var repository: PokemonAlertsRepository
    private val service = FakePokemonAlertsService()

    @Before
    fun setUp() {
        preferences = FakeAlertPreferencesStore()
        dao = FakeAlertDao()
        repository = PokemonAlertsRepository(service, preferences, dao)
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
    fun fetchAlerts_returnsServiceData_andInsertsToDao() = runTest {
        val alerts = listOf(sampleAlert("Service"))
        service.alerts = alerts

        val fetched = repository.fetchAlerts()

        assertEquals(alerts, fetched)
        assertEquals(1, dao.alerts.value.size)
        assertEquals("Service", dao.alerts.value.first().name)
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
        override suspend fun getHistory(): List<PokemonAlert> = emptyList()
    }

    private class FakeAlertPreferencesStore(initial: Set<String> = emptySet()) : AlertPreferencesStore {
        private val state = MutableStateFlow(initial)
        private val favoritesState = MutableStateFlow(emptySet<String>())
        private val themeModeState = MutableStateFlow(0)
        private val imperialState = MutableStateFlow(false)
        private val onboardingState = MutableStateFlow(false)

        override val seenAlertIds: Flow<Set<String>> = state.asStateFlow()

        override suspend fun getSeenAlertIds(): Set<String> = state.value

        override suspend fun updateSeenAlertIds(alertIds: Set<String>) {
            state.value = alertIds
        }

        override val favoriteAlertIds: Flow<Set<String>> = favoritesState.asStateFlow()

        override suspend fun getFavoriteAlertIds(): Set<String> = favoritesState.value

        override suspend fun updateFavoriteAlertIds(alertIds: Set<String>) {
            favoritesState.value = alertIds
        }

        override val themeMode: Flow<Int> = themeModeState.asStateFlow()

        override suspend fun updateThemeMode(mode: Int) {
            themeModeState.value = mode
        }

        override val useImperialUnits: Flow<Boolean> = imperialState.asStateFlow()

        override suspend fun updateUseImperialUnits(useImperial: Boolean) {
            imperialState.value = useImperial
        }

        override val onboardingCompleted: Flow<Boolean> = onboardingState.asStateFlow()

        override suspend fun setOnboardingCompleted(completed: Boolean) {
            onboardingState.value = completed
        }
    }

    private class FakeAlertDao : AlertDao {
        val alerts = MutableStateFlow<List<AlertEntity>>(emptyList())

        override fun observeAllAlerts(): Flow<List<AlertEntity>> = alerts.asStateFlow()

        override suspend fun getAllAlerts(): List<AlertEntity> = alerts.value

        override suspend fun insertAlerts(newAlerts: List<AlertEntity>) {
            alerts.value = newAlerts
        }

        override suspend fun clearAll() {
            alerts.value = emptyList()
        }

        override suspend fun deleteExpired(currentTime: String) {
            // No-op for test
        }
    }
}
