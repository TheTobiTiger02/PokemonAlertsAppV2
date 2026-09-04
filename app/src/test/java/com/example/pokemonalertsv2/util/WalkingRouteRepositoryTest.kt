package com.example.pokemonalertsv2.util

import com.example.pokemonalertsv2.data.HistoryResponse
import com.example.pokemonalertsv2.data.CurrentWeatherResponse
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonAlertsService
import com.example.pokemonalertsv2.data.TotalStatsResponse
import com.example.pokemonalertsv2.data.WalkingRouteRequest
import com.example.pokemonalertsv2.data.WalkingRouteResult
import com.example.pokemonalertsv2.data.WalkingRoutesResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sqrt

@OptIn(ExperimentalCoroutinesApi::class)
class WalkingRouteRepositoryTest {
    private val origin = WalkingRouteOrigin(49.738, 8.603, accuracyMeters = 10f)
    private val alert = PokemonAlert(
        name = "Pikachu",
        latitude = 49.742,
        longitude = 8.615,
        endTime = "2026-07-24 14:00:00"
    )

    @Test
    fun getWalkingRoutes_mapsProviderResultAndReusesNearbyCache() = runTest {
        var now = 1_000L
        val service = FakeService { request ->
            assertEquals(listOf(alert.uniqueId), request.destinations.map { it.id })
            response(
                WalkingRouteResult(
                    id = alert.uniqueId,
                    status = "OK",
                    distanceMeters = 1432,
                    durationSeconds = 1061
                )
            )
        }
        val repository = repository(service, nowMillis = { now })

        val first = repository.getWalkingRoutes(origin, listOf(alert))
        val nearby = repository.getWalkingRoutes(
            origin.copy(latitude = origin.latitude + 0.0002),
            listOf(alert)
        )

        assertEquals(WalkingRouteInfo(1432, 1061), first[alert.uniqueId])
        assertEquals(first, nearby)
        assertEquals(1, service.requests.size)
    }

    @Test
    fun getWalkingRoutes_refreshesAfterMovementExpiryOrDestinationChange() = runTest {
        var now = 1_000L
        val service = FakeService {
            response(
                WalkingRouteResult(
                    id = it.destinations.single().id,
                    status = "OK",
                    distanceMeters = 1000 + it.destinations.single().latitude.toInt(),
                    durationSeconds = 800
                )
            )
        }
        val repository = repository(
            service = service,
            nowMillis = { now },
            cacheTtlMillis = 1_000L,
            maxOriginMovementMeters = 75f
        )

        repository.getWalkingRoutes(origin, listOf(alert))
        repository.getWalkingRoutes(origin.copy(latitude = origin.latitude + 0.001), listOf(alert))
        now += 1_001L
        repository.getWalkingRoutes(origin, listOf(alert))
        repository.getWalkingRoutes(origin, listOf(alert.copy(latitude = 49.743)))

        assertEquals(4, service.requests.size)
    }

    @Test
    fun getWalkingRoutes_skipsImpreciseLocationAndUnreachableResults() = runTest {
        val service = FakeService {
            response(
                WalkingRouteResult(
                    id = alert.uniqueId,
                    status = "UNREACHABLE"
                )
            )
        }
        val repository = repository(service)

        assertTrue(
            repository.getWalkingRoutes(
                origin.copy(accuracyMeters = 101f),
                listOf(alert)
            ).isEmpty()
        )
        assertTrue(repository.getWalkingRoutes(origin, listOf(alert)).isEmpty())
        assertEquals(1, service.requests.size)
    }

    @Test
    fun getWalkingRoutes_coalescesConcurrentRequestsAndCapsBatchAtFiveHundred() = runTest {
        val alerts = (0 until 520).map { index ->
            alert.copy(
                name = "Alert $index",
                latitude = origin.latitude + (index + 1) * 0.00001,
                endTime = "end-$index"
            )
        }
        val service = FakeService { request ->
            WalkingRoutesResponse(
                provider = "test",
                calculatedAt = "2026-07-24T12:00:00Z",
                routes = request.destinations.map {
                    WalkingRouteResult(it.id, "OK", 1000, 800)
                }
            )
        }
        val repository = repository(service)

        listOf(
            async { repository.getWalkingRoutes(origin, alerts) },
            async { repository.getWalkingRoutes(origin, alerts) }
        ).awaitAll()

        assertEquals(1, service.requests.size)
        assertEquals(500, service.requests.single().destinations.size)
    }

    @Test
    fun getWalkingRoutes_doesNotSerialiseUnrelatedBatches() = runTest {
        val other = alert.copy(name = "Bulbasaur", latitude = 49.744, endTime = "later")
        val service = FakeService { request ->
            delay(1_000)
            response(*request.destinations.map { WalkingRouteResult(it.id, "OK", 1_000, 800) }.toTypedArray())
        }
        val repository = repository(service)

        val startedAt = testScheduler.currentTime
        val results = listOf(
            async { repository.getWalkingRoutes(origin, listOf(alert)) },
            async { repository.getWalkingRoutes(origin, listOf(other)) }
        ).awaitAll()

        assertEquals(2, service.requests.size)
        assertEquals(WalkingRouteInfo(1_000, 800), results[0][alert.uniqueId])
        assertEquals(WalkingRouteInfo(1_000, 800), results[1][other.uniqueId])
        // Both requests were in flight at once; the old global mutex made this 2_000.
        assertEquals(1_000L, testScheduler.currentTime - startedAt)
    }

    @Test
    fun getWalkingRoutes_failedRequestAnswersWaitersImmediately() = runTest {
        val service = FakeService { error("backend down") }
        val repository = repository(service)

        val startedAt = testScheduler.currentTime
        val result = repository.getWalkingRoutes(origin, listOf(alert), timeoutMillis = 6_000)

        assertTrue(result.isEmpty())
        assertEquals(1, service.requests.size)
        // Waiters are completed with null rather than left hanging until the timeout.
        assertEquals(0L, testScheduler.currentTime - startedAt)
    }

    @Test
    fun getWalkingRoutes_suppressesRetriesAfterAFailureThenRecovers() = runTest {
        var now = 1_000L
        var fail = true
        val service = FakeService {
            if (fail) error("429") else response(WalkingRouteResult(alert.uniqueId, "OK", 1_000, 800))
        }
        val repository = repository(service, nowMillis = { now })

        // First failure arms the backoff.
        assertTrue(repository.getWalkingRoutes(origin, listOf(alert)).isEmpty())
        assertEquals(1, service.requests.size)

        // Every refresh inside the window is served from cache only -- this is the
        // storm that hammered the backend before the backoff existed.
        repeat(5) { assertTrue(repository.getWalkingRoutes(origin, listOf(alert)).isEmpty()) }
        assertEquals(1, service.requests.size)

        // Once it elapses, exactly one retry goes out, and it fails again -> 4 s.
        now += WalkingRouteRepository.BASE_BACKOFF_MILLIS
        assertTrue(repository.getWalkingRoutes(origin, listOf(alert)).isEmpty())
        assertEquals(2, service.requests.size)
        repeat(3) { repository.getWalkingRoutes(origin, listOf(alert)) }
        assertEquals(2, service.requests.size)

        // A success clears the backoff and caches normally again.
        fail = false
        now += 2 * WalkingRouteRepository.BASE_BACKOFF_MILLIS
        assertEquals(
            WalkingRouteInfo(1_000, 800),
            repository.getWalkingRoutes(origin, listOf(alert))[alert.uniqueId]
        )
        assertEquals(3, service.requests.size)
        repository.getWalkingRoutes(origin, listOf(alert))
        assertEquals(3, service.requests.size)
    }

    @Test
    fun getWalkingRoutes_backoffNeverExceedsTheCeiling() = runTest {
        var now = 1_000L
        val service = FakeService { error("429") }
        val repository = repository(service, nowMillis = { now })

        repeat(12) {
            repository.getWalkingRoutes(origin, listOf(alert))
            now += WalkingRouteRepository.MAX_BACKOFF_MILLIS
        }

        // Capped, so a long outage still retries once per minute rather than stalling.
        assertEquals(12, service.requests.size)
    }

    @Test
    fun getWalkingRoutes_boundsTheWholeBackgroundWait() = runTest {
        val service = FakeService {
            delay(10_000)
            response(WalkingRouteResult(alert.uniqueId, "OK", 1_000, 800))
        }
        val repository = repository(service)

        val result = repository.getWalkingRoutes(
            origin = origin,
            alerts = listOf(alert),
            timeoutMillis = 2_500
        )

        assertTrue(result.isEmpty())
        assertEquals(1, service.requests.size)
    }

    private fun response(vararg routes: WalkingRouteResult) = WalkingRoutesResponse(
        provider = "test",
        calculatedAt = "2026-07-24T12:00:00Z",
        routes = routes.toList()
    )

    private fun TestScope.repository(
        service: PokemonAlertsService,
        nowMillis: () -> Long = System::currentTimeMillis,
        cacheTtlMillis: Long = 10 * 60 * 1000L,
        maxOriginMovementMeters: Float = 75f
    ) = WalkingRouteRepository(
        service = service,
        nowMillis = nowMillis,
        cacheTtlMillis = cacheTtlMillis,
        maxOriginMovementMeters = maxOriginMovementMeters,
        // Virtual time: requests must run on the test scheduler, not Dispatchers.IO.
        requestScope = backgroundScope,
        distanceBetween = { fromLatitude, fromLongitude, toLatitude, toLongitude ->
            val latitudeMeters = (toLatitude - fromLatitude) * 111_320.0
            val longitudeMeters =
                (toLongitude - fromLongitude) * 111_320.0 * cos(Math.toRadians(fromLatitude))
            sqrt(latitudeMeters * latitudeMeters + longitudeMeters * longitudeMeters).toFloat()
        }
    )

    private class FakeService(
        private val routeHandler: suspend (WalkingRouteRequest) -> WalkingRoutesResponse
    ) : PokemonAlertsService {
        val requests = mutableListOf<WalkingRouteRequest>()

        override suspend fun getWalkingRoutes(request: WalkingRouteRequest): WalkingRoutesResponse {
            requests += request
            return routeHandler(request)
        }

        override suspend fun getPokemonAlerts(): List<PokemonAlert> = emptyList()

        override suspend fun getCurrentWeather(area: String): CurrentWeatherResponse = CurrentWeatherResponse()

        override suspend fun getHistory(
            type: String?,
            date: String?,
            startDate: String?,
            endDate: String?,
            q: String?
        ): HistoryResponse = HistoryResponse()

        override suspend fun getHistoryPaged(
            limit: Int,
            offset: Int,
            type: String?,
            date: String?,
            startDate: String?,
            endDate: String?,
            q: String?
        ): HistoryResponse = HistoryResponse()

        override suspend fun getTotalStats(date: String?): TotalStatsResponse = TotalStatsResponse()
    }
}
