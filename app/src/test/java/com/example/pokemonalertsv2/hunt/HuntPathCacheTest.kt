package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.catchroutes.CatchPathGeometry
import com.example.pokemonalertsv2.catchroutes.CatchPathLeg
import com.example.pokemonalertsv2.catchroutes.CatchPathResponse
import com.example.pokemonalertsv2.catchroutes.CatchPoint
import com.example.pokemonalertsv2.catchroutes.CatchRoutesService
import com.example.pokemonalertsv2.catchroutes.CatchSnappedPoint
import com.example.pokemonalertsv2.data.RouteMatrixRequest
import com.example.pokemonalertsv2.data.RouteMatrixResponse
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class HuntPathCacheTest {
    private val originLat = 49.8700
    private val originLon = 8.6500
    private val a = HuntRoutePoint("a", 49.8720, 8.6510)
    private val b = HuntRoutePoint("b", 49.8740, 8.6530)
    private val c = HuntRoutePoint("c", 49.8760, 8.6490)

    private class FakePathService(val handler: suspend (RouteMatrixRequest) -> Response<CatchPathResponse>) : CatchRoutesService {
        val requests = mutableListOf<RouteMatrixRequest>()
        override suspend fun path(request: RouteMatrixRequest): Response<CatchPathResponse> {
            requests += request
            return handler(request)
        }
        override suspend fun windows(query: Map<String, String>): Response<JsonObject> = error("unused")
        override suspend fun catalogue(query: Map<String, String>, etag: String?): Response<JsonObject> = error("unused")
        override suspend fun matrix(request: RouteMatrixRequest): Response<RouteMatrixResponse> = error("unused")
        override suspend fun spawnpoint(id: String): Response<JsonObject> = error("unused")
    }

    /** A straight two-point leg between each consecutive pair, as the endpoint answers. */
    private fun ok(request: RouteMatrixRequest): Response<CatchPathResponse> = Response.success(
        CatchPathResponse(
            status = "ok", provider = "test", calculatedAt = "2026-09-16T10:00:00Z",
            snappedPoints = request.points.map { CatchSnappedPoint(it.id, it.latitude, it.longitude) },
            legs = request.points.zipWithNext { from, to ->
                CatchPathLeg(from.id, to.id, 250.0, 180.0, CatchPathGeometry("LineString",
                    listOf(listOf(from.longitude, from.latitude), listOf(to.longitude, to.latitude))))
            }
        )
    )

    private fun busy(): Response<CatchPathResponse> = Response.error(
        "".toResponseBody(),
        okhttp3.Response.Builder()
            .code(429).message("Too Many Requests").protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("https://example.test/api/routes/path").build())
            .header("Retry-After", "120")
            .build()
    )

    private var clock = 0L

    private fun TestScope.cache(handler: suspend (RouteMatrixRequest) -> Response<CatchPathResponse>) =
        FakePathService(handler).let { service ->
            HuntPathCache(service, nowMillis = { clock }, requestScope = backgroundScope) to service
        }

    @Before fun openGate() = HuntRoutingGate.clear()
    @After fun closeGate() = HuntRoutingGate.clear()

    @Test fun `the first request draws the whole numbered route in one call`() = runTest {
        val (cache, service) = cache(::ok)
        assertTrue(cache.request(originLat, originLon, listOf(a, b, c)))
        assertEquals(1, service.requests.size)
        assertEquals(4, service.requests.single().points.size)
        val path = cache.snapshot() as HuntPath.Resolved
        assertEquals(2, path.next.size)
        assertEquals(3, path.rest.size)
    }

    @Test fun `an unchanged plan, or a trainer standing close by, costs nothing`() = runTest {
        val (cache, service) = cache(::ok)
        cache.request(originLat, originLon, listOf(a, b))
        clock += 60_000
        assertTrue(cache.request(originLat, originLon, listOf(a, b)))
        // About 20 m on: the line keeps starting where they stood rather than being re-asked or dropped.
        assertTrue(cache.request(originLat + 0.0002, originLon, listOf(a, b)))
        assertEquals(1, service.requests.size)
        assertTrue(cache.snapshot() is HuntPath.Resolved)
    }

    @Test fun `walking on asks only for the trainer's leg`() = runTest {
        val (cache, service) = cache(::ok)
        cache.request(originLat, originLon, listOf(a, b, c))
        clock += 20_000
        assertTrue(cache.request(originLat - 0.0010, originLon, listOf(a, b, c)))
        assertEquals(2, service.requests.size)
        assertEquals(2, service.requests.last().points.size)
    }

    @Test fun `requests are spaced out`() = runTest {
        val (cache, service) = cache(::ok)
        cache.request(originLat, originLon, listOf(a))
        clock += 5_000
        assertFalse(cache.request(originLat, originLon, listOf(a, b)))
        assertEquals(1, service.requests.size)
        clock += HuntPathCache.MIN_INTERVAL_MILLIS
        assertTrue(cache.request(originLat, originLon, listOf(a, b)))
        assertEquals(2, service.requests.size)
    }

    @Test fun `a rate limit holds every later request, the matrix's included`() = runTest {
        val (cache, service) = cache { busy() }
        assertFalse(cache.request(originLat, originLon, listOf(a, b)))
        assertTrue(HuntRoutingGate.blockedUntilMillis > System.currentTimeMillis())
        clock += 30_000
        assertFalse(cache.request(originLat, originLon, listOf(a, b)))
        assertEquals(1, service.requests.size)
        // Held back, the route is still drawn: straight, until the streets can be asked for.
        val waiting = cache.snapshot() as HuntPath.Resolved
        assertEquals(listOf(CatchPoint(originLat, originLon), CatchPoint(a.latitude, a.longitude)), waiting.next)
    }

    @Test fun `a failure keeps the line already drawn and backs off`() = runTest {
        var fail = false
        val (cache, service) = cache { if (fail) throw IOException("offline") else ok(it) }
        cache.request(originLat, originLon, listOf(a, b))
        val drawn = cache.snapshot()
        fail = true
        clock += 20_000
        assertFalse(cache.request(originLat, originLon, listOf(a, b, c)))
        assertEquals((drawn as HuntPath.Resolved).next, (cache.snapshot() as HuntPath.Resolved).next)
        assertEquals(clock + HuntPathCache.BASE_BACKOFF_MILLIS, HuntRoutingGate.blockedUntilMillis)
        assertEquals(2, service.requests.size)
    }

    @Test fun `recalculate asks again right away`() = runTest {
        val (cache, service) = cache(::ok)
        cache.request(originLat, originLon, listOf(a, b))
        assertTrue(cache.request(originLat, originLon, listOf(a, b), force = true))
        assertEquals(2, service.requests.size)
        assertEquals(2, service.requests.last().points.size)
    }

    @Test fun `the line always walks the order asked for, and never blinks out on a re-plan`() = runTest {
        val (cache, _) = cache(::ok)
        assertEquals(HuntPath.None, cache.pathFor(listOf(a, b, c)))
        cache.request(originLat, originLon, listOf(a, b, c))
        assertEquals(listOf("a", "b", "c"), (cache.pathFor(listOf(a, b, c)) as HuntPath.Resolved).stopIds)
        // Re-ordered before a new path arrived: drawn in the new order, the unknown legs straight.
        val reordered = cache.pathFor(listOf(b, a, c)) as HuntPath.Resolved
        assertEquals(listOf("b", "a", "c"), reordered.stopIds)
        assertEquals(listOf(CatchPoint(originLat, originLon), CatchPoint(b.latitude, b.longitude)), reordered.next)
        assertEquals(listOf("a", "b"), (cache.pathFor(listOf(a, b)) as HuntPath.Resolved).stopIds)
    }

    @Test fun `a leg due for refresh is still drawn until its replacement lands`() = runTest {
        var answered = 0
        // Streets with a bend in them, so they cannot be mistaken for the straight stand-in.
        val (cache, service) = cache { request ->
            if (answered++ > 0) return@cache busy()
            val body = ok(request).body()!!
            Response.success(body.copy(legs = body.legs.map { leg ->
                val (from, to) = leg.geometry.coordinates
                leg.copy(geometry = leg.geometry.copy(coordinates = listOf(from, listOf(from[0], to[1]), to)))
            }))
        }
        cache.request(originLat, originLon, listOf(a, b))
        val streets = (cache.pathFor(listOf(a, b)) as HuntPath.Resolved).next
        assertEquals(3, streets.size)
        clock += HUNT_LEG_TTL_MILLIS + 1
        assertFalse(cache.request(originLat, originLon, listOf(a, b)))
        assertEquals(2, service.requests.size)
        assertEquals(streets, (cache.pathFor(listOf(a, b)) as HuntPath.Resolved).next)
    }
}
