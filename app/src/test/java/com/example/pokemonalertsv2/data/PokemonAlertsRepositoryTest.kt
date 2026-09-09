package com.example.pokemonalertsv2.data

import com.example.pokemonalertsv2.data.AffectedAlert
import com.example.pokemonalertsv2.data.database.AlertDao
import com.example.pokemonalertsv2.data.database.AlertEntity
import com.example.pokemonalertsv2.data.database.HistoryAlertDao
import com.example.pokemonalertsv2.data.database.HistoryAlertEntity
import com.example.pokemonalertsv2.data.database.toEntity
import com.example.pokemonalertsv2.data.database.toHistoryEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

class PokemonAlertsRepositoryTest {

    private lateinit var preferences: FakeAlertPreferencesStore
    private lateinit var dao: FakeAlertDao
    private lateinit var historyDao: FakeHistoryAlertDao
    private lateinit var repository: PokemonAlertsRepository
    private val service = FakePokemonAlertsService()

    @Before
    fun setUp() {
        preferences = FakeAlertPreferencesStore()
        dao = FakeAlertDao()
        historyDao = FakeHistoryAlertDao()
        repository = PokemonAlertsRepository(service, preferences, dao, historyDao)
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
    fun detectNewAlerts_skipsAlertsSeenByServerId() = runTest {
        val seenAlert = sampleAlert(name = "Seen", id = 6215)
        preferences.updateSeenAlertIds(setOf("server:6215"))

        val freshAlerts = repository.detectNewAlerts(listOf(seenAlert, sampleAlert("New", id = 6216)))

        assertEquals(listOf(sampleAlert("New", id = 6216)), freshAlerts)
    }

    @Test
    fun markAlertsAsSeen_persistsIdentifiers() = runTest {
        val alertA = sampleAlert("A", id = 101)
        val alertB = sampleAlert("B", endTime = "2025-10-08 08:00:00")

        repository.markAlertsAsSeen(listOf(alertA, alertB))

        val storedIds = preferences.getSeenAlertIds()
        assertTrue(storedIds.containsAll(listOf("server:101", alertA.uniqueId, alertB.uniqueId)))
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

    @Test
    fun fetchAlerts_excludesInvalidatedAlertsFromActiveCacheAndResult() = runTest {
        val active = sampleAlert("Active", id = 1)
        val invalidated = sampleAlert("Invalidated", id = 2).copy(
            invalidatedAt = "2026-07-16 19:04:21"
        )
        service.alerts = listOf(active, invalidated)

        val fetched = repository.fetchAlerts()

        assertEquals(listOf(active), fetched)
        assertEquals(listOf(active.uniqueId), dao.alerts.value.map { it.uniqueId })
    }

    @Test
    fun fetchAlerts_skipsDaoWriteWhenServerReportsNotModified() = runTest {
        val alert = sampleAlert("Service")
        dao.alerts.value = listOf(alert.toEntity().copy(createdAt = 123L))
        preferences.syncCursor = AlertSyncCursor(revision = 7L, etag = "etag-a")
        service.syncStatus = 304

        val fetched = repository.fetchAlerts()

        assertEquals(listOf(alert), fetched)
        assertEquals(0, dao.insertCalls)
        assertEquals(0, dao.clearCalls)
        assertEquals(listOf(7L to "etag-a"), service.syncRequests)
        // A 304 tells us nothing new, so the cursor must not move.
        assertEquals(AlertSyncCursor(revision = 7L, etag = "etag-a"), preferences.syncCursor)
    }

    @Test
    fun fetchAlerts_prunesExpiredCacheEvenWhenNotModified() = runTest {
        // The server's own expiry timer and this device's clock can disagree, so
        // a 304 must not stop the local sweep.
        val expired = sampleAlert("Expired", endTime = "1500000000000")
        dao.alerts.value = listOf(expired.toEntity().copy(createdAt = 123L))
        preferences.syncCursor = AlertSyncCursor(revision = 7L, etag = "etag-a")
        service.syncStatus = 304

        val fetched = repository.fetchAlerts()

        assertTrue(fetched.isEmpty())
        assertTrue(dao.alerts.value.isEmpty())
        assertEquals(1, dao.clearCalls)
    }

    @Test
    fun fetchAlerts_appliesDeltaUpsertsAndRemovals() = runTest {
        val kept = sampleAlert("Kept", id = 1)
        val doomed = sampleAlert("Doomed", id = 2)
        dao.alerts.value = listOf(kept.toEntity(), doomed.toEntity())
        preferences.syncCursor = AlertSyncCursor(revision = 4L, etag = "etag-old")

        val added = sampleAlert("Added", id = 3)
        service.alerts = listOf(added)
        service.syncFull = false
        service.syncRevision = 9L
        service.syncEtag = "etag-new"
        service.syncRemoved = listOf(RemovedAlert(id = 2, name = "Doomed", endTime = doomed.endTime))

        repository.fetchAlerts()

        assertEquals(
            setOf("server-1", "server-3"),
            dao.alerts.value.map { it.uniqueId }.toSet()
        )
        assertEquals(AlertSyncCursor(revision = 9L, etag = "etag-new"), preferences.syncCursor)
    }

    @Test
    fun fetchAlerts_replacesCacheWhenServerCannotAnswerFromHistory() = runTest {
        val stale = sampleAlert("Stale", id = 1)
        dao.alerts.value = listOf(stale.toEntity())
        preferences.syncCursor = AlertSyncCursor(revision = 4L, etag = "etag-old")

        service.alerts = listOf(sampleAlert("Fresh", id = 2))
        service.syncFull = true
        service.syncRevision = 1L

        repository.fetchAlerts()

        assertEquals(listOf("server-2"), dao.alerts.value.map { it.uniqueId })
    }

    @Test
    fun fetchAlerts_replacesCacheOnFirstSyncEvenWhenServerSendsADelta() = runTest {
        // No stored cursor means the cache's provenance is unknown; a delta
        // cannot be trusted to reconcile it.
        val orphan = sampleAlert("Orphan", id = 1)
        dao.alerts.value = listOf(orphan.toEntity())
        service.alerts = listOf(sampleAlert("Fresh", id = 2))
        service.syncFull = false
        service.syncRevision = 3L

        repository.fetchAlerts()

        assertEquals(listOf("server-2"), dao.alerts.value.map { it.uniqueId })
    }

    @Test
    fun processIncomingAlert_insertsSingleAlertWithoutClearingCache() = runTest {
        val existing = sampleAlert("Existing", id = 1)
        dao.alerts.value = listOf(existing.toEntity())
        val pushed = sampleAlert("Pushed", id = 6215)

        repository.processIncomingAlert(pushed)

        assertEquals(
            listOf(existing.uniqueId, pushed.uniqueId),
            dao.alerts.value.map { it.uniqueId }
        )
        assertEquals("Pushed", dao.alerts.value.last().name)
        assertEquals(1, dao.insertCalls)
        assertEquals(0, dao.clearCalls)
    }

    @Test
    fun fetchAlerts_cachesAllAlertsSharingNameAndEndTimeWithDistinctIds() = runTest {
        val first = sampleAlert("Quest Alert", endTime = "", id = 201)
        val second = sampleAlert("Quest Alert", endTime = "", id = 202)
        service.alerts = listOf(first, second)

        repository.fetchAlerts()

        assertEquals(
            setOf(first.uniqueId, second.uniqueId),
            dao.alerts.value.map { it.uniqueId }.toSet()
        )
    }

    @Test
    fun processIncomingAlert_keepsPreviousAlertWithSameNameAndEndTimeButDifferentId() = runTest {
        val existing = sampleAlert("Quest Alert", endTime = "", id = 101)
        dao.alerts.value = listOf(existing.toEntity())
        val pushed = sampleAlert("Quest Alert", endTime = "", id = 102)

        repository.processIncomingAlert(pushed)

        assertEquals(
            setOf(existing.uniqueId, pushed.uniqueId),
            dao.alerts.value.map { it.uniqueId }.toSet()
        )
    }

    @Test
    fun processIncomingAlert_survivesConcurrentFetchReplaceAll() = runTest {
        val stale = sampleAlert("Stale", id = 1)
        dao.alerts.value = listOf(stale.toEntity())
        service.alerts = listOf(sampleAlert("Fresh", id = 2))
        val gate = CompletableDeferred<Unit>()
        service.fetchGate = gate

        val fetchJob = launch { repository.fetchAlerts() }
        testScheduler.runCurrent() // fetch owns fetchMutex and parks on the gate

        val pushed = sampleAlert("Pushed", id = 3)
        val processJob = launch { repository.processIncomingAlert(pushed) }
        testScheduler.runCurrent() // process parks on fetchMutex behind the fetch

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()
        fetchJob.join()
        processJob.join()

        assertEquals(
            setOf("server-2", "server-3"),
            dao.alerts.value.map { it.uniqueId }.toSet()
        )
    }

    @Test
    fun processIncomingAlert_weatherRemovesAffectedAlertByServerId() = runTest {
        val affected = sampleAlert("Zubat", id = 25).copy(
            pokemon = "Zubat",
            cp = 239,
            type = listOf("PvP"),
            area = "Alsbach"
        )
        dao.alerts.value = listOf(affected.toEntity())
        val weather = weatherAlert(
            affectedAlerts = listOf(AffectedAlert(id = 25, name = affected.name))
        )

        val removed = repository.processIncomingAlert(weather)

        assertEquals(setOf(affected.uniqueId), removed)
        assertEquals(listOf(weather.uniqueId), dao.alerts.value.map { it.uniqueId })
    }

    @Test
    fun processIncomingAlert_weatherUsesExactNormalizedFallbackIdentity() = runTest {
        val affected = sampleAlert(
            name = "Zubat",
            endTime = "2026-07-16 20:00:00"
        ).copy(
            pokemon = "  ZUBAT ",
            pokemonForm = null,
            cp = 239,
            type = listOf("PvP", "Rare"),
            area = " ALSBACH "
        )
        dao.alerts.value = listOf(affected.toEntity())
        val weather = weatherAlert(
            affectedAlerts = listOf(
                AffectedAlert(
                    name = affected.name,
                    pokemon = "zubat",
                    pokemonForm = null,
                    cp = 239,
                    type = listOf("rare", "pvp"),
                    endTime = " 2026-07-16 20:00:00 ",
                    area = "alsbach"
                )
            )
        )

        val removed = repository.processIncomingAlert(weather)

        assertEquals(setOf(affected.uniqueId), removed)
        assertEquals(listOf(weather.uniqueId), dao.alerts.value.map { it.uniqueId })
    }

    @Test
    fun processIncomingAlert_weatherRemovesMultipleExactMatches() = runTest {
        val first = affectedPokemon("Zubat", cp = 239, endTime = "2026-07-16 20:00:00")
        val second = affectedPokemon("Zubat", cp = 303, endTime = "2026-07-16 20:01:00")
        val unaffected = affectedPokemon("Pikachu", cp = 500, endTime = "2026-07-16 20:02:00")
        dao.alerts.value = listOf(first, second, unaffected).map { it.toEntity() }
        val weather = weatherAlert(
            affectedAlerts = listOf(first.toAffectedAlert(), second.toAffectedAlert())
        )

        val removed = repository.processIncomingAlert(weather)

        assertEquals(setOf(first.uniqueId, second.uniqueId), removed)
        assertEquals(
            setOf(unaffected.uniqueId, weather.uniqueId),
            dao.alerts.value.map { it.uniqueId }.toSet()
        )
    }

    @Test
    fun processIncomingAlert_duplicateWeatherEventIsIdempotent() = runTest {
        val affected = affectedPokemon(
            "Zubat",
            cp = 239,
            endTime = "2026-07-16 20:00:00"
        )
        dao.alerts.value = listOf(affected.toEntity())
        val weather = weatherAlert(listOf(affected.toAffectedAlert()))

        repository.processIncomingAlert(weather)
        repository.processIncomingAlert(weather)

        assertEquals(listOf(weather.uniqueId), dao.alerts.value.map { it.uniqueId })
    }

    @Test
    fun processIncomingAlert_incompleteAndNearFallbackMatchesRemainActive() = runTest {
        val exactCandidate = affectedPokemon(
            "Zubat",
            cp = 239,
            endTime = "2026-07-16 20:00:00"
        )
        dao.alerts.value = listOf(exactCandidate.toEntity())

        val incompleteWeather = weatherAlert(
            affectedAlerts = listOf(
                exactCandidate.toAffectedAlert().copy(area = null)
            )
        )
        assertTrue(repository.processIncomingAlert(incompleteWeather).isEmpty())

        dao.alerts.value = listOf(exactCandidate.toEntity())
        val nearWeather = weatherAlert(
            affectedAlerts = listOf(
                exactCandidate.toAffectedAlert().copy(cp = 240)
            )
        )
        assertTrue(repository.processIncomingAlert(nearWeather).isEmpty())
        assertTrue(dao.alerts.value.any { it.uniqueId == exactCandidate.uniqueId })
    }

    @Test
    fun processIncomingAlert_invalidatedAlertIsRemovedInsteadOfStored() = runTest {
        val active = sampleAlert("Zubat", id = 55)
        dao.alerts.value = listOf(active.toEntity())

        repository.processIncomingAlert(
            active.copy(
                invalidatedAt = "2026-07-16 19:04:21",
                invalidationReason = "Weather changed",
                invalidatedByAlertId = 10587
            )
        )

        assertTrue(dao.alerts.value.isEmpty())
    }

    @Test
    fun getLocalAlerts_filtersInvalidatedRowsAlreadyPresentInCache() = runTest {
        dao.alerts.value = listOf(
            sampleAlert("Visible").toEntity(),
            sampleAlert("Hidden").copy(
                invalidationReason = "Weather changed"
            ).toEntity()
        )

        assertEquals(listOf("Visible"), repository.getLocalAlerts().map { it.name })
    }

    @Test
    fun clearExpiredAlerts_removesExpiredAndKeepsUnknownEndTimes() = runTest {
        val expired = sampleAlert("Expired", endTime = "1500000000000")
        val active = sampleAlert("Active", endTime = "3000")
        val invalid = sampleAlert("Invalid", endTime = "not-a-date")
        val missing = sampleAlert("Missing", endTime = "")
        dao.alerts.value = listOf(
            expired.toEntity(),
            active.toEntity(),
            invalid.toEntity(),
            missing.toEntity()
        )

        repository.clearExpiredAlerts(nowMillis = 2000000000000)

        assertEquals(
            listOf(active.uniqueId, invalid.uniqueId, missing.uniqueId),
            dao.alerts.value.map { it.uniqueId }
        )
        assertEquals(1, dao.clearCalls)
        assertEquals(1, dao.insertCalls)
    }

    @Test
    fun clearExpiredAlerts_treatsBogusNumericEndTimesAsUnknownNotExpired() = runTest {
        // A yyyymmdd-style number must not be read as an epoch in 1970.
        val bogus = sampleAlert("Bogus", endTime = "20251231")
        dao.alerts.value = listOf(bogus.toEntity())

        repository.clearExpiredAlerts(nowMillis = 2000000000000)

        assertEquals(listOf(bogus.uniqueId), dao.alerts.value.map { it.uniqueId })
        assertEquals(0, dao.clearCalls)
    }

    @Test
    fun clearExpiredAlerts_doesNotRewriteWhenAllCachedAlertsAreActive() = runTest {
        val active = sampleAlert("Active", endTime = "3000")
        dao.alerts.value = listOf(active.toEntity())

        repository.clearExpiredAlerts(nowMillis = 2000)

        assertEquals(listOf(active.uniqueId), dao.alerts.value.map { it.uniqueId })
        assertEquals(0, dao.clearCalls)
        assertEquals(0, dao.insertCalls)
    }

    @Test
    fun refreshHistory_passesTrimmedSearchQuery_andReplacesCache() = runTest {
        val alert = sampleAlert("Pikachu")
        service.historyResponse = HistoryResponse(total = 1, limit = 50, offset = 0, count = 1, data = listOf(alert))

        val response = repository.refreshHistory(
            pageSize = 50,
            date = "2026-05-19",
            type = "Quest",
            q = "  pikachu  "
        )

        assertEquals(listOf(alert), response.data)
        assertEquals(1, historyDao.alerts.value.size)
        assertEquals(
            HistoryPagedRequest(
                limit = 50,
                offset = 0,
                type = "Quest",
                date = "2026-05-19",
                startDate = null,
                endDate = null,
                q = "pikachu"
            ),
            service.pagedHistoryRequests.single()
        )
    }

    @Test
    fun refreshHistory_treatsBlankSearchQueryAsNull() = runTest {
        repository.refreshHistory(pageSize = 50, q = "   ")

        assertEquals(null, service.pagedHistoryRequests.single().q)
    }

    @Test
    fun fetchHistoryPage_passesSearchQueryAndOffset_andAppendsCache() = runTest {
        historyDao.alerts.value = listOf(sampleAlert("Existing").toHistoryEntity())
        val alert = sampleAlert("Alsbach")
        service.historyResponse = HistoryResponse(total = 75, limit = 50, offset = 50, count = 1, data = listOf(alert))

        repository.fetchHistoryPage(
            limit = 50,
            offset = 50,
            date = "2026-05-19",
            type = "Raid",
            q = "alsbach"
        )

        assertEquals(2, historyDao.alerts.value.size)
        assertEquals(
            HistoryPagedRequest(
                limit = 50,
                offset = 50,
                type = "Raid",
                date = "2026-05-19",
                startDate = null,
                endDate = null,
                q = "alsbach"
            ),
            service.pagedHistoryRequests.single()
        )
    }

    @Test
    fun getTotalStats_passesDateToService() = runTest {
        service.totalStatsResponse = TotalStatsResponse(
            totalAlerts = 75,
            byType = mapOf("Raid" to 10, "Quest" to 65)
        )

        val response = repository.getTotalStats(date = "2026-05-27")

        assertEquals(75, response.totalAlerts)
        assertEquals(listOf("2026-05-27"), service.totalStatsRequests)
    }

    @Test
    fun getCurrentWeather_trimsArea_andPreservesFreshness() = runTest {
        service.currentWeatherResponse = CurrentWeatherResponse(
            area = "Alsbach",
            currentWeather = "Rain 🌧",
            observedAt = "2026-08-30T12:15:00.000Z",
            isCurrentHour = true
        )

        val response = repository.getCurrentWeather("  Alsbach  ")

        assertEquals("Alsbach", response.area)
        assertEquals("Rain 🌧", response.currentWeather)
        assertTrue(response.isCurrentHour)
        assertTrue(response.confirmed)
        assertEquals(listOf("Alsbach"), service.currentWeatherRequests)
    }

    @Test
    fun getCurrentWeather_prefersExplicitConfirmationOverFreshness() = runTest {
        service.currentWeatherResponse = CurrentWeatherResponse(
            area = "Alsbach",
            currentWeather = "Partly Cloudy",
            isCurrentHour = true,
            currentWeatherConfirmed = false
        )

        val response = repository.getCurrentWeather("Alsbach")

        assertFalse(response.confirmed)
    }

    @Test
    fun getCurrentWeather_fallsBackToFreshnessWhenConfirmationAbsent() = runTest {
        service.currentWeatherResponse = CurrentWeatherResponse(
            area = "Alsbach",
            currentWeather = "Snow",
            isCurrentHour = false
        )

        val response = repository.getCurrentWeather("Alsbach")

        assertFalse(response.confirmed)
    }

    private fun sampleAlert(
        name: String,
        endTime: String = "2099-10-07 23:59:59",
        id: Int? = null
    ) = PokemonAlert(
        id = id,
        name = name,
        description = "description $name",
        imageUrl = null,
        longitude = 8.62,
        latitude = 49.74,
        endTime = endTime,
        type = listOf("Quest"),
        thumbnailUrl = null
    )

    private fun affectedPokemon(
        pokemon: String,
        cp: Int,
        endTime: String
    ) = sampleAlert(pokemon, endTime = endTime).copy(
        pokemon = pokemon,
        cp = cp,
        type = listOf("PvP"),
        area = "Alsbach"
    )

    private fun PokemonAlert.toAffectedAlert() = AffectedAlert(
        id = id,
        name = name,
        pokemon = pokemon,
        pokemonForm = pokemonForm,
        cp = cp,
        type = type,
        endTime = endTime,
        area = area
    )

    private fun weatherAlert(affectedAlerts: List<AffectedAlert>) = PokemonAlert(
        id = 10587,
        name = "Weather change",
        endTime = "2026-07-16 20:04:21",
        type = listOf("WeatherChange"),
        weatherFrom = "Partly Cloudy",
        weatherTo = "Cloudy",
        affectedAlerts = affectedAlerts,
        area = "Alsbach"
    )

    private class FakePokemonAlertsService : PokemonAlertsService {
        var alerts: List<PokemonAlert> = emptyList()
        var fetchGate: CompletableDeferred<Unit>? = null
        var historyResponse: HistoryResponse = HistoryResponse(data = emptyList())
        var totalStatsResponse: TotalStatsResponse = TotalStatsResponse()
        var currentWeatherResponse: CurrentWeatherResponse = CurrentWeatherResponse()
        val pagedHistoryRequests = mutableListOf<HistoryPagedRequest>()
        val totalStatsRequests = mutableListOf<String?>()
        val currentWeatherRequests = mutableListOf<String>()

        /** Set to 304 to make the next sync a no-op the way an unchanged server would. */
        var syncStatus: Int = 200
        var syncFull: Boolean = true
        var syncRevision: Long = 1L
        var syncRemoved: List<RemovedAlert> = emptyList()
        var syncEtag: String? = null
        val syncRequests = mutableListOf<Pair<Long?, String?>>()

        override suspend fun getPokemonAlerts(since: Long?, etag: String?): Response<AlertSyncResponse> {
            fetchGate?.await()
            syncRequests += since to etag
            if (syncStatus == 304) {
                // Retrofit only produces a 304 through its error branch: OkHttp
                // counts only 200..299 as successful.
                val raw = okhttp3.Response.Builder()
                    .code(304)
                    .message("Not Modified")
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .request(okhttp3.Request.Builder().url("http://localhost/api/pokemon").build())
                    .build()
                return Response.error("".toResponseBody(null), raw)
            }
            val headers = okhttp3.Headers.Builder().apply {
                syncEtag?.let { add("ETag", it) }
            }.build()
            return Response.success(
                AlertSyncResponse(
                    revision = syncRevision,
                    full = syncFull,
                    alerts = alerts,
                    removed = syncRemoved
                ),
                headers
            )
        }
        override suspend fun getHistory(type: String?, date: String?, startDate: String?, endDate: String?, q: String?): HistoryResponse =
            historyResponse
        override suspend fun getHistoryPaged(
            limit: Int,
            offset: Int,
            type: String?,
            date: String?,
            startDate: String?,
            endDate: String?,
            q: String?
        ): HistoryResponse {
            pagedHistoryRequests += HistoryPagedRequest(limit, offset, type, date, startDate, endDate, q)
            return historyResponse
        }
        override suspend fun getTotalStats(date: String?): TotalStatsResponse {
            totalStatsRequests += date
            return totalStatsResponse
        }
        override suspend fun getCurrentWeather(area: String): CurrentWeatherResponse {
            currentWeatherRequests += area
            return currentWeatherResponse
        }
        override suspend fun getWalkingRoutes(request: WalkingRouteRequest): WalkingRoutesResponse =
            WalkingRoutesResponse(
                provider = "test",
                calculatedAt = "2026-07-24T12:00:00Z"
            )
    }

    private data class HistoryPagedRequest(
        val limit: Int,
        val offset: Int,
        val type: String?,
        val date: String?,
        val startDate: String?,
        val endDate: String?,
        val q: String?
    )

    private class FakeHistoryAlertDao : HistoryAlertDao() {
        val alerts = MutableStateFlow<List<HistoryAlertEntity>>(emptyList())

        override fun observeAll(): Flow<List<HistoryAlertEntity>> = alerts.asStateFlow()
        override suspend fun count(): Int = alerts.value.size
        override suspend fun insertAll(alerts: List<HistoryAlertEntity>) {
            this.alerts.value = this.alerts.value + alerts
        }
        override suspend fun clearAll() {
            alerts.value = emptyList()
        }

    }

    private class FakeAlertPreferencesStore(initial: Set<String> = emptySet()) : AlertPreferencesStore {
        private val state = MutableStateFlow(initial)
        private val favoritesState = MutableStateFlow(emptySet<String>())
        private val themeModeState = MutableStateFlow(0)
        private val imperialState = MutableStateFlow(false)
        private val onboardingState = MutableStateFlow(false)
        private val sortState = MutableStateFlow(SortPreference.TIME_REMAINING)
        private val mapStyleState = MutableStateFlow(MapStylePreference.GOOGLE_STANDARD)
        private val showMapCountdownsState = MutableStateFlow(false)
        private val autoEnterMapPipState = MutableStateFlow(false)
        private val notificationsEnabledState = MutableStateFlow(true)
        private val raidsState = MutableStateFlow(true)
        private val spawnsState = MutableStateFlow(true)
        private val questsState = MutableStateFlow(true)
        private val hundosState = MutableStateFlow(true)
        private val pvpState = MutableStateFlow(true)
        private val nundosState = MutableStateFlow(true)
        private val kecleonState = MutableStateFlow(true)
        private val rocketState = MutableStateFlow(true)
        private val vibrateState = MutableStateFlow(true)
        private val silenceState = MutableStateFlow(0L)
        private val areaState = MutableStateFlow("All")
        private val distanceState = MutableStateFlow(0)
        private val snoozeDurationState = MutableStateFlow(10)
        private val excludedHundoState = MutableStateFlow(emptySet<String>())
        private val excludedNundoState = MutableStateFlow(emptySet<String>())
        private val excludedPvpState = MutableStateFlow(emptySet<String>())
        private val excludedSpawnState = MutableStateFlow(emptySet<String>())
        private val excludedRocketState = MutableStateFlow(emptySet<String>())
        private val excludedRaidTiersState = MutableStateFlow(emptySet<String>())
        private val dismissedState = MutableStateFlow(emptySet<String>())
        private val allowedHundoSpeciesState = MutableStateFlow(emptySet<String>())
        private val allowedNundoSpeciesState = MutableStateFlow(emptySet<String>())
        private val allowedPvpSpeciesState = MutableStateFlow(emptySet<String>())
        private val allowedSpawnSpeciesState = MutableStateFlow(emptySet<String>())
        private val lastSyncState = MutableStateFlow(0L)
        private val selectedAlertFilterState = MutableStateFlow("ALL")

        override val seenAlertIds: Flow<Set<String>> = state.asStateFlow()
        override suspend fun getSeenAlertIds(): Set<String> = state.value
        override suspend fun updateSeenAlertIds(alertIds: Set<String>) { state.value = alertIds }

        override val favoriteAlertIds: Flow<Set<String>> = favoritesState.asStateFlow()
        override suspend fun getFavoriteAlertIds(): Set<String> = favoritesState.value
        override suspend fun updateFavoriteAlertIds(alertIds: Set<String>) { favoritesState.value = alertIds }

        override val themeMode: Flow<Int> = themeModeState.asStateFlow()
        override suspend fun updateThemeMode(mode: Int) { themeModeState.value = mode }

        override val useImperialUnits: Flow<Boolean> = imperialState.asStateFlow()
        override suspend fun updateUseImperialUnits(useImperial: Boolean) { imperialState.value = useImperial }

        override val onboardingCompleted: Flow<Boolean> = onboardingState.asStateFlow()
        override suspend fun setOnboardingCompleted(completed: Boolean) { onboardingState.value = completed }

        override val sortPreference: Flow<SortPreference> = sortState.asStateFlow()
        override suspend fun updateSortPreference(preference: SortPreference) { sortState.value = preference }

        override val mapStylePreference: Flow<MapStylePreference> = mapStyleState.asStateFlow()
        override suspend fun updateMapStylePreference(preference: MapStylePreference) { mapStyleState.value = preference }

        override val showMapCountdowns: Flow<Boolean> = showMapCountdownsState.asStateFlow()
        override suspend fun updateShowMapCountdowns(enabled: Boolean) { showMapCountdownsState.value = enabled }

        override val autoEnterMapPip: Flow<Boolean> = autoEnterMapPipState.asStateFlow()
        override suspend fun updateAutoEnterMapPip(enabled: Boolean) { autoEnterMapPipState.value = enabled }

        override val notificationsEnabled: Flow<Boolean> = notificationsEnabledState.asStateFlow()
        override suspend fun updateNotificationsEnabled(enabled: Boolean) { notificationsEnabledState.value = enabled }

        override val raidsNotifications: Flow<Boolean> = raidsState.asStateFlow()
        override suspend fun updateRaidsNotifications(enabled: Boolean) { raidsState.value = enabled }

        override val spawnsNotifications: Flow<Boolean> = spawnsState.asStateFlow()
        override suspend fun updateSpawnsNotifications(enabled: Boolean) { spawnsState.value = enabled }

        override val questsNotifications: Flow<Boolean> = questsState.asStateFlow()
        override suspend fun updateQuestsNotifications(enabled: Boolean) { questsState.value = enabled }

        override val hundosNotifications: Flow<Boolean> = hundosState.asStateFlow()
        override suspend fun updateHundosNotifications(enabled: Boolean) { hundosState.value = enabled }

        override val pvpNotifications: Flow<Boolean> = pvpState.asStateFlow()
        override suspend fun updatePvpNotifications(enabled: Boolean) { pvpState.value = enabled }

        override val nundosNotifications: Flow<Boolean> = nundosState.asStateFlow()
        override suspend fun updateNundosNotifications(enabled: Boolean) { nundosState.value = enabled }

        override val kecleonNotifications: Flow<Boolean> = kecleonState.asStateFlow()
        override suspend fun updateKecleonNotifications(enabled: Boolean) { kecleonState.value = enabled }

        override val rocketNotifications: Flow<Boolean> = rocketState.asStateFlow()
        override suspend fun updateRocketNotifications(enabled: Boolean) { rocketState.value = enabled }

        override val notificationVibrate: Flow<Boolean> = vibrateState.asStateFlow()
        override suspend fun updateNotificationVibrate(enabled: Boolean) { vibrateState.value = enabled }

        override val silenceUntil: Flow<Long> = silenceState.asStateFlow()

        override val maxWalkingMinutes: Flow<Int> = MutableStateFlow(0)
        override suspend fun updateMaxWalkingMinutes(minutes: Int) = Unit

        override val filterPresets: Flow<List<FilterPreset>> = MutableStateFlow(emptyList())
        override suspend fun saveFilterPreset(preset: FilterPreset) = Unit
        override suspend fun deleteFilterPreset(name: String) = Unit

        override val quietHoursEnabled: Flow<Boolean> = MutableStateFlow(false)
        override val quietHoursStartMinute: Flow<Int> = MutableStateFlow(DEFAULT_QUIET_HOURS_START)
        override val quietHoursEndMinute: Flow<Int> = MutableStateFlow(DEFAULT_QUIET_HOURS_END)
        override suspend fun updateQuietHoursEnabled(enabled: Boolean) = Unit
        override suspend fun updateQuietHours(startMinute: Int, endMinute: Int) = Unit
        override suspend fun updateSilenceUntil(timestampMillis: Long) { silenceState.value = timestampMillis }
        
        override val selectedArea: Flow<String> = areaState.asStateFlow()
        override suspend fun updateSelectedArea(area: String) { areaState.value = area }
        
        override val maxDistance: Flow<Int> = distanceState.asStateFlow()
        override suspend fun updateMaxDistance(distance: Int) { distanceState.value = distance }

        override val snoozeDuration: Flow<Int> = snoozeDurationState.asStateFlow()
        override suspend fun updateSnoozeDuration(minutes: Int) { snoozeDurationState.value = minutes }

        override val excludedHundoTypes: Flow<Set<String>> = excludedHundoState.asStateFlow()
        override suspend fun updateExcludedHundoTypes(types: Set<String>) { excludedHundoState.value = types }

        override val excludedNundoTypes: Flow<Set<String>> = excludedNundoState.asStateFlow()
        override suspend fun updateExcludedNundoTypes(types: Set<String>) { excludedNundoState.value = types }

        override val excludedPvpTypes: Flow<Set<String>> = excludedPvpState.asStateFlow()
        override suspend fun updateExcludedPvpTypes(types: Set<String>) { excludedPvpState.value = types }

        override val excludedSpawnTypes: Flow<Set<String>> = excludedSpawnState.asStateFlow()
        override suspend fun updateExcludedSpawnTypes(types: Set<String>) { excludedSpawnState.value = types }

        override val excludedRocketTypes: Flow<Set<String>> = excludedRocketState.asStateFlow()
        override suspend fun updateExcludedRocketTypes(types: Set<String>) { excludedRocketState.value = types }

        override val excludedRaidTiers: Flow<Set<String>> = excludedRaidTiersState.asStateFlow()
        override suspend fun updateExcludedRaidTiers(types: Set<String>) { excludedRaidTiersState.value = types }

        override val dismissedAlertIds: Flow<Set<String>> = dismissedState.asStateFlow()
        override suspend fun addDismissedAlert(alertId: String) { dismissedState.value = dismissedState.value + alertId }
        override suspend fun removeDismissedAlert(alertId: String) { dismissedState.value = dismissedState.value - alertId }
        override suspend fun clearDismissedAlerts() { dismissedState.value = emptySet() }

        override val allowedHundoSpecies: Flow<Set<String>> = allowedHundoSpeciesState.asStateFlow()
        override suspend fun updateAllowedHundoSpecies(species: Set<String>) { allowedHundoSpeciesState.value = species }

        override val allowedNundoSpecies: Flow<Set<String>> = allowedNundoSpeciesState.asStateFlow()
        override suspend fun updateAllowedNundoSpecies(species: Set<String>) { allowedNundoSpeciesState.value = species }

        override val allowedPvpSpecies: Flow<Set<String>> = allowedPvpSpeciesState.asStateFlow()
        override suspend fun updateAllowedPvpSpecies(species: Set<String>) { allowedPvpSpeciesState.value = species }

        override val allowedSpawnSpecies: Flow<Set<String>> = allowedSpawnSpeciesState.asStateFlow()
        override suspend fun updateAllowedSpawnSpecies(species: Set<String>) { allowedSpawnSpeciesState.value = species }

        private val showSpawnRadiusState = MutableStateFlow(false)
        override val showSpawnRadius: Flow<Boolean> = showSpawnRadiusState.asStateFlow()
        override suspend fun updateShowSpawnRadius(enabled: Boolean) { showSpawnRadiusState.value = enabled }

        private val spacialRendEnabledState = MutableStateFlow(false)
        override val spacialRendEnabled: Flow<Boolean> = spacialRendEnabledState.asStateFlow()
        override suspend fun updateSpacialRendEnabled(enabled: Boolean) { spacialRendEnabledState.value = enabled }

        override val lastSuccessfulAlertSyncMillis: Flow<Long> = lastSyncState.asStateFlow()
        override suspend fun updateLastSuccessfulAlertSyncMillis(timestampMillis: Long) { lastSyncState.value = timestampMillis }
        var syncCursor: AlertSyncCursor = AlertSyncCursor()
        override suspend fun getAlertSyncCursor(): AlertSyncCursor = syncCursor
        override suspend fun updateAlertSyncCursor(cursor: AlertSyncCursor) { syncCursor = cursor }
        private val lastPushState = MutableStateFlow(0L)
        override val lastPushReceivedMillis: Flow<Long> = lastPushState.asStateFlow()
        override suspend fun updateLastPushReceivedMillis(timestampMillis: Long) { lastPushState.value = timestampMillis }
        override suspend fun applyNotificationPreset(preset: NotificationPreset) = Unit
        override val selectedAlertFilterName: Flow<String> = selectedAlertFilterState.asStateFlow()
        override suspend fun updateSelectedAlertFilterName(filterName: String) { selectedAlertFilterState.value = filterName }

        private val feedCategoriesState = MutableStateFlow(emptySet<String>())
        private val mapCategoriesState = MutableStateFlow(emptySet<String>())
        private val mapShowDismissedState = MutableStateFlow(false)

        override val feedCategories: Flow<Set<String>> = feedCategoriesState.asStateFlow()
        override suspend fun updateFeedCategories(categories: Set<String>) { feedCategoriesState.value = categories }
        override val mapCategories: Flow<Set<String>> = mapCategoriesState.asStateFlow()
        override suspend fun updateMapCategories(categories: Set<String>) { mapCategoriesState.value = categories }
        override val mapShowDismissed: Flow<Boolean> = mapShowDismissedState.asStateFlow()
        override suspend fun updateMapShowDismissed(enabled: Boolean) { mapShowDismissedState.value = enabled }
        private val showWeatherCellsState = MutableStateFlow(true)
        override val showWeatherCells: Flow<Boolean> = showWeatherCellsState.asStateFlow()
        override suspend fun updateShowWeatherCells(enabled: Boolean) { showWeatherCellsState.value = enabled }
    }

    private class FakeAlertDao : AlertDao() {
        val alerts = MutableStateFlow<List<AlertEntity>>(emptyList())
        var insertCalls = 0
        var clearCalls = 0

        override fun observeAllAlerts(): Flow<List<AlertEntity>> = alerts.asStateFlow()

        override suspend fun getAllAlerts(): List<AlertEntity> = alerts.value

        override suspend fun insertAlerts(newAlerts: List<AlertEntity>) {
            insertCalls++
            val byId = LinkedHashMap<String, AlertEntity>()
            alerts.value.forEach { byId[it.uniqueId] = it }
            newAlerts.forEach { byId[it.uniqueId] = it }
            alerts.value = byId.values.toList()
        }

        override suspend fun clearAll() {
            clearCalls++
            alerts.value = emptyList()
        }

        override suspend fun deleteByUniqueIds(uniqueIds: List<String>) {
            alerts.value = alerts.value.filterNot { it.uniqueId in uniqueIds }
        }

        override suspend fun deleteAlert(uniqueId: String, serverId: Int?) {
            alerts.value = alerts.value.filterNot {
                it.uniqueId == uniqueId || (serverId != null && it.id == serverId)
            }
        }
    }
}
