package com.example.pokemonalertsv2.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The matrix cells must survive decoding as nulls.
 *
 * `coerceInputValues = true` turns a null into the type's default wherever the type
 * is not nullable, so declaring these cells `Int` instead of `Int?` would silently
 * decode "no walking route" as a distance of zero -- an unreachable target reading
 * as the nearest one. The config below is a copy of PokemonAlertsApi's.
 */
class RouteMatrixSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val payload = """
        {
          "provider": "valhalla",
          "calculatedAt": "2026-09-10T14:41:02Z",
          "ids": ["origin", "a", "b"],
          "distanceMeters": [[0, 412, null], [418, 0, 601], [null, 604, 0]],
          "durationSeconds": [[0, 303, null], [307, 0, 442], [null, 444, 0]],
          "somethingTheServerAddedLater": 7
        }
    """.trimIndent()

    @Test
    fun `an unreachable cell decodes as null, never as zero`() {
        val response = json.decodeFromString(RouteMatrixResponse.serializer(), payload)

        assertNull(response.distanceMeters[0][2])
        assertNull(response.durationSeconds[0][2])
        assertNull(response.distanceMeters[2][0])
        assertNull(response.durationSeconds[2][0])
    }

    @Test
    fun `reachable cells and identity survive`() {
        val response = json.decodeFromString(RouteMatrixResponse.serializer(), payload)

        assertEquals(listOf("origin", "a", "b"), response.ids)
        assertEquals("valhalla", response.provider)
        assertEquals(412, response.distanceMeters[0][1])
        assertEquals(303, response.durationSeconds[0][1])
        assertEquals(0, response.distanceMeters[1][1])
    }

    @Test
    fun `a response missing every field decodes to empty rather than throwing`() {
        // The server answers 4xx/5xx with {"error": ...}; Retrofit only reaches the
        // decoder on 2xx, but a proxy returning an empty object must not crash a hunt.
        val response = json.decodeFromString(RouteMatrixResponse.serializer(), "{}")

        assertEquals(emptyList<String>(), response.ids)
        assertEquals(emptyList<List<Int?>>(), response.distanceMeters)
    }

    @Test
    fun `the request names the costing the server requires`() {
        val encoded = json.encodeToString(
            RouteMatrixRequest.serializer(),
            RouteMatrixRequest.pedestrian(listOf(RouteMatrixPoint("origin", 49.87275, 8.65112)))
        )

        assertEquals(encoded, true, encoded.contains("\"costing\":\"pedestrian\""))
        assertEquals(encoded, true, encoded.contains("\"id\":\"origin\""))
    }
}
