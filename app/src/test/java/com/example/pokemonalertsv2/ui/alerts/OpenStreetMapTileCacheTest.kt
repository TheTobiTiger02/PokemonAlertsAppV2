package com.example.pokemonalertsv2.ui.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenStreetMapTileCacheTest {

    private val template = "https://tile.example.test/{z}/{x}/{y}.png"

    // Alsbach-Haehnlein, roughly.
    private val north = 49.75
    private val south = 49.73
    private val east = 8.62
    private val west = 8.59

    @Test
    fun `zoom zero is a single tile`() {
        assertEquals(0, OpenStreetMapTileCache.longitudeToTileX(-180.0, 0))
        assertEquals(0, OpenStreetMapTileCache.longitudeToTileX(179.9, 0))
        assertEquals(0, OpenStreetMapTileCache.latitudeToTileY(0.0, 0))
    }

    @Test
    fun `the antimeridian and the poles clamp instead of overflowing`() {
        // 180 degrees would otherwise land one tile past the edge of the world.
        assertEquals(1, OpenStreetMapTileCache.longitudeToTileX(180.0, 1))
        // Web Mercator is undefined past ~85 degrees; this must not become NaN.
        assertEquals(0, OpenStreetMapTileCache.latitudeToTileY(90.0, 2))
        assertEquals(3, OpenStreetMapTileCache.latitudeToTileY(-90.0, 2))
    }

    @Test
    fun `a known coordinate maps to the expected tile`() {
        // Greenwich at zoom 1 sits in the top-right quadrant of the world.
        assertEquals(1, OpenStreetMapTileCache.longitudeToTileX(0.1, 1))
        assertEquals(0, OpenStreetMapTileCache.latitudeToTileY(51.5, 1))
    }

    @Test
    fun `urls fill in the template placeholders`() {
        val urls = OpenStreetMapTileCache.tileUrls(
            template = template,
            north = north, south = south, east = east, west = west,
            zoom = 14, extraZoomLevels = 0
        )
        assertTrue(urls.isNotEmpty())
        urls.forEach {
            assertTrue(it.startsWith("https://tile.example.test/"))
            assertTrue(it.endsWith(".png"))
            assertTrue("no placeholder should survive", !it.contains("{"))
        }
    }

    @Test
    fun `extra zoom levels add tiles, and each level is finer than the last`() {
        val oneLevel = OpenStreetMapTileCache.tileUrls(
            template = template,
            north = north, south = south, east = east, west = west,
            zoom = 12, extraZoomLevels = 0
        )
        val threeLevels = OpenStreetMapTileCache.tileUrls(
            template = template,
            north = north, south = south, east = east, west = west,
            zoom = 12, extraZoomLevels = 2
        )
        assertTrue(threeLevels.size > oneLevel.size)
        assertTrue(threeLevels.any { it.contains("/14/") })
    }

    @Test
    fun `the tile count is capped so a world view cannot fire tens of thousands of requests`() {
        val urls = OpenStreetMapTileCache.tileUrls(
            template = template,
            north = 85.0, south = -85.0, east = 180.0, west = -180.0,
            zoom = 10, extraZoomLevels = 2, maxTiles = 500
        )
        assertEquals(500, urls.size)
    }

    // --- The centre-based variant the download button actually uses ------------

    @Test
    fun `a radius of tiles around a centre is an odd sided square`() {
        val urls = OpenStreetMapTileCache.tileUrlsAround(
            template = template,
            latitude = 49.74, longitude = 8.60,
            zoom = 14, tileRadius = 2, extraZoomLevels = 0
        )
        // 2 * 2 + 1 = 5 per side.
        assertEquals(25, urls.size)
    }

    @Test
    fun `the radius doubles per zoom level so the same ground area stays covered`() {
        val urls = OpenStreetMapTileCache.tileUrlsAround(
            template = template,
            latitude = 49.74, longitude = 8.60,
            zoom = 14, tileRadius = 1, extraZoomLevels = 1
        )
        // z14: 3x3 = 9, z15: radius 2 so 5x5 = 25.
        assertEquals(34, urls.size)
        assertTrue(urls.any { it.contains("/14/") })
        assertTrue(urls.any { it.contains("/15/") })
    }

    @Test
    fun `tiles off the edge of the world are skipped, not requested`() {
        val urls = OpenStreetMapTileCache.tileUrlsAround(
            template = template,
            latitude = 0.0, longitude = 0.0,
            zoom = 0, tileRadius = 3, extraZoomLevels = 0
        )
        // Zoom 0 has exactly one tile, however wide the radius.
        assertEquals(1, urls.size)
    }

    @Test
    fun `the centre variant is capped too`() {
        val urls = OpenStreetMapTileCache.tileUrlsAround(
            template = template,
            latitude = 49.74, longitude = 8.60,
            zoom = 14, tileRadius = 20, extraZoomLevels = 2, maxTiles = 100
        )
        assertEquals(100, urls.size)
    }

    @Test
    fun `zoom is clamped to the levels the tile source actually serves`() {
        val urls = OpenStreetMapTileCache.tileUrls(
            template = template,
            north = north, south = south, east = east, west = west,
            zoom = 25, extraZoomLevels = 2
        )
        assertTrue(urls.isNotEmpty())
        assertTrue("zoom must not exceed 19", urls.all { it.contains("/19/") })
    }
}
