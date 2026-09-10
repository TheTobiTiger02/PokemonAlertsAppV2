package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.data.AlertSyncResponse
import com.example.pokemonalertsv2.data.CurrentWeatherResponse
import com.example.pokemonalertsv2.data.HistoryResponse
import com.example.pokemonalertsv2.data.PokemonAlertsService
import com.example.pokemonalertsv2.data.RouteMatrixRequest
import com.example.pokemonalertsv2.data.RouteMatrixResponse
import com.example.pokemonalertsv2.data.TotalStatsResponse
import com.example.pokemonalertsv2.data.WalkingRouteRequest
import com.example.pokemonalertsv2.data.WalkingRoutesResponse
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuntRouteMatrixCacheTest {

    private val originLat = 49.87275
    private val originLon = 8.65112

    private val a = HuntRoutePoint("server-1", 49.87410, 8.64890)
    private val b = HuntRoutePoint("server-2", 49.87058, 8.65401)

    /** A square answer where every leg is `metres` long, in request order. */
    private fun square(request: RouteMatrixRequest, metres: Int = 400): RouteMatrixResponse {
        val n = request.points.size
        return RouteMatrixResponse(
            provider = "test",
            calculatedAt = "2026-09-10T14:41:02Z",
            ids = request.points.map { it.id },
            distanceMeters = List(n) { i -> List(n) { j -> if (i == j) 0 else metres } },
            durationSeconds = List(n) { i -> List(n) { j -> if (i == j) 0 else metres / 2 } }
        )
    }

    private fun TestScope.cache(
        clock: () -> Long = { testScheduler.currentTime },
        handler: suspend (RouteMatrixRequest) -> RouteMatrixResponse
    ): Pair<HuntRouteMatrixCache, FakeService> {
        val service = FakeService(handler)
        return HuntRouteMatrixCache(
            service = service,
            nowMillis = clock,
            requestScope = backgroundScope
        ) to service
    }

    @Test
    fun `a fetched matrix publishes legs the ordering can read`() = runTest {
        val (cache, service) = cache { square(it) }

        val published = cache.prefetch(originLat, originLon, listOf(a, b))

        assertTrue(published)
        assertEquals(1, service.requests.size)
        val costs = cache.snapshot()
        assertEquals(400.0, costs.walkedMetersOrNull(null, a.id)!!, 0.001)
        assertEquals(400.0, costs.walkedMetersOrNull(a.id, b.id)!!, 0.001)
    }

    @Test
    fun `the request is what the endpoint requires`() = runTest {
        val (cache, service) = cache { square(it) }

        cache.prefetch(originLat, originLon, listOf(a, b))

        val request = service.requests.single()
        assertEquals(RouteMatrixRequest.PEDESTRIAN, request.costing)
        assertEquals(3, request.points.size)
        // The trainer leads, under an id no alert can collide with.
        assertEquals(HuntRouteMatrixCache.HUNT_ORIGIN_ID, request.points.first().id)
        assertEquals(request.points.size, request.points.map { it.id }.toSet().size)
    }

    @Test
    fun `never more than thirty points go out`() = runTest {
        val (cache, service) = cache { square(it) }
        val many = (0 until 60).map { HuntRoutePoint("server-$it", 49.87 + it * 0.0004, 8.65) }

        cache.prefetch(originLat, originLon, many)

        // 30 is the endpoint's ceiling; 31 is a 400, which backoff would then hide.
        assertTrue(service.requests.single().points.size <= 30)
        assertEquals(HUNT_MATRIX_MAX_TARGETS + 1, service.requests.single().points.size)
    }

    @Test
    fun `a second prefetch inside the TTL asks nothing but still publishes`() = runTest {
        val (cache, service) = cache { square(it) }

        assertTrue(cache.prefetch(originLat, originLon, listOf(a, b)))
        assertTrue(cache.prefetch(originLat, originLon, listOf(a, b)))

        assertEquals(1, service.requests.size)
    }

    @Test
    fun `an expired leg is asked for again`() = runTest {
        var clock = 0L
        val (cache, service) = cache(clock = { clock }) { square(it) }

        cache.prefetch(originLat, originLon, listOf(a, b))
        clock = HUNT_LEG_TTL_MILLIS + 1
        cache.prefetch(originLat, originLon, listOf(a, b))

        assertEquals(2, service.requests.size)
    }

    @Test
    fun `ids answered in a different order are still matched by name`() = runTest {
        // The highest-value assertion here: a positional read that happened to be
        // wrong would walk the trainer to a different Pokemon than the list shows.
        val (cache, _) = cache { request ->
            val ids = request.points.map { it.id }.reversed()
            val n = ids.size
            RouteMatrixResponse(
                ids = ids,
                // Row i is the point named ids[i]; make the trainer's row distinctive.
                distanceMeters = List(n) { i ->
                    List(n) { j -> if (i == j) 0 else if (ids[i] == HuntRouteMatrixCache.HUNT_ORIGIN_ID) 111 else 999 }
                },
                durationSeconds = List(n) { i -> List(n) { j -> if (i == j) 0 else 50 } }
            )
        }

        cache.prefetch(originLat, originLon, listOf(a, b))

        val costs = cache.snapshot()
        assertEquals(111.0, costs.walkedMetersOrNull(null, a.id)!!, 0.001)
        assertEquals(999.0, costs.walkedMetersOrNull(a.id, b.id)!!, 0.001)
    }

    @Test
    fun `a null cell is remembered as unreachable and not re-asked at once`() = runTest {
        val (cache, service) = cache { request ->
            val n = request.points.size
            RouteMatrixResponse(
                ids = request.points.map { it.id },
                distanceMeters = List(n) { i -> List(n) { j -> if (i == j) 0 else if (i == 0 && j == 1) null else 400 } },
                durationSeconds = List(n) { i -> List(n) { j -> if (i == j) 0 else if (i == 0 && j == 1) null else 200 } }
            )
        }

        cache.prefetch(originLat, originLon, listOf(a, b))

        assertNull(cache.snapshot().walkedMetersOrNull(null, a.id))
        assertNotNull(cache.snapshot().walkedMetersOrNull(null, b.id))

        cache.prefetch(originLat, originLon, listOf(a, b))
        assertEquals(1, service.requests.size)
    }

    @Test
    fun `a failure leaves the last snapshot alone and backs off`() = runTest {
        var clock = 0L
        var fail = false
        val (cache, service) = cache(clock = { clock }) { request ->
            if (fail) throw IllegalStateException("503") else square(request)
        }

        cache.prefetch(originLat, originLon, listOf(a, b))
        val before = cache.snapshot()

        fail = true
        clock = HUNT_LEG_TTL_MILLIS + 1
        assertFalse(cache.prefetch(originLat, originLon, listOf(a, b)))
        assertEquals(2, service.requests.size)

        // Armed: the next attempt inside the window must not reach the network.
        assertFalse(cache.prefetch(originLat, originLon, listOf(a, b)))
        assertEquals(2, service.requests.size)

        // And the ordering still has whatever it had before the outage.
        assertEquals(before.calculatedAtMillis, cache.snapshot().calculatedAtMillis)
    }

    @Test
    fun `backoff lifts, and success clears it`() = runTest {
        var clock = 0L
        var fail = true
        val (cache, service) = cache(clock = { clock }) { request ->
            if (fail) throw IllegalStateException("503") else square(request)
        }

        cache.prefetch(originLat, originLon, listOf(a, b))
        assertEquals(1, service.requests.size)

        clock += HuntRouteMatrixCache.BASE_BACKOFF_MILLIS + 1
        fail = false
        assertTrue(cache.prefetch(originLat, originLon, listOf(a, b)))
        assertEquals(2, service.requests.size)
        assertNotNull(cache.snapshot().walkedMetersOrNull(null, a.id))
    }

    @Test
    fun `a response that does not describe the request is discarded whole`() = runTest {
        val (cache, _) = cache { request ->
            // One row short: a contract bug, not an outage.
            val n = request.points.size
            RouteMatrixResponse(
                ids = request.points.map { it.id },
                distanceMeters = List(n - 1) { List(n) { 400 } },
                durationSeconds = List(n - 1) { List(n) { 200 } }
            )
        }

        assertFalse(cache.prefetch(originLat, originLon, listOf(a, b)))
        assertEquals(HuntLegCosts.None, cache.snapshot())
    }

    @Test
    fun `an unknown id in the answer is discarded whole`() = runTest {
        val (cache, _) = cache { request ->
            val n = request.points.size
            RouteMatrixResponse(
                ids = request.points.map { it.id }.dropLast(1) + "server-someone-else",
                distanceMeters = List(n) { List(n) { 400 } },
                durationSeconds = List(n) { List(n) { 200 } }
            )
        }

        assertFalse(cache.prefetch(originLat, originLon, listOf(a, b)))
        assertEquals(HuntLegCosts.None, cache.snapshot())
    }

    @Test
    fun `fewer than two points never reaches the network`() = runTest {
        val (cache, service) = cache { square(it) }

        assertFalse(cache.prefetch(originLat, originLon, emptyList()))

        assertTrue(service.requests.isEmpty())
    }

    private class FakeService(
        private val handler: suspend (RouteMatrixRequest) -> RouteMatrixResponse
    ) : PokemonAlertsService {
        val requests = mutableListOf<RouteMatrixRequest>()

        override suspend fun getRouteMatrix(request: RouteMatrixRequest): RouteMatrixResponse {
            requests += request
            return handler(request)
        }

        override suspend fun getPokemonAlerts(since: Long?, etag: String?): retrofit2.Response<AlertSyncResponse> =
            retrofit2.Response.success(AlertSyncResponse())

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

        override suspend fun getWalkingRoutes(request: WalkingRouteRequest): WalkingRoutesResponse =
            WalkingRoutesResponse(provider = "test", calculatedAt = "")
    }
}
