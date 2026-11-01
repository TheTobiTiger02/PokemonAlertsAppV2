package com.example.pokemonalertsv2.hunt

import com.example.pokemonalertsv2.catchroutes.CatchPoint
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuntTrackTest {
    @Test fun `encodes and decodes to about a metre`() {
        val points = listOf(CatchPoint(49.7612345, 8.6123456), CatchPoint(49.7615, 8.6129), CatchPoint(-33.8688, 151.2093))
        val decoded = HuntTrack.decode(HuntTrack.encode(points))
        assertEquals(points.size, decoded.size)
        points.zip(decoded).forEach { (a, b) -> assertTrue(huntPathDistance(a, b) < 1.0) }
    }

    @Test fun `matches the reference polyline encoding`() {
        // Google's documented example.
        val points = listOf(CatchPoint(38.5, -120.2), CatchPoint(40.7, -120.95), CatchPoint(43.252, -126.453))
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", HuntTrack.encode(points))
    }

    @Test fun `append skips points too close to the last one`() {
        val start = HuntTrack.append("", CatchPoint(49.0, 8.0))
        assertEquals(start, HuntTrack.append(start, CatchPoint(49.00002, 8.0))) // ~2 m
        assertEquals(2, HuntTrack.decode(HuntTrack.append(start, CatchPoint(49.0002, 8.0))).size) // ~22 m
    }

    @Test fun `very long tracks are halved, keeping the newest point`() {
        var track = ""
        repeat(HuntTrack.MAX_POINTS + 1) { i -> track = HuntTrack.append(track, CatchPoint(49.0 + i * 0.0002, 8.0)) }
        val points = HuntTrack.decode(track)
        assertTrue(points.size <= HuntTrack.MAX_POINTS / 2 + 2)
        assertEquals(49.0 + HuntTrack.MAX_POINTS * 0.0002, points.last().latitude, 1e-5)
    }

    @Test fun `walking records the path alongside the distance`() {
        var stats = HuntStats()
        for (i in 0..10) stats = stats.withFix(49.0 + i * 0.0001, 8.0, 5f) // ~11 m steps
        val points = HuntTrack.decode(stats.track)
        assertEquals(11, points.size)
        assertTrue(stats.distanceMeters > 100)
        // Coarse fixes add nothing.
        assertEquals(stats, stats.withFix(49.01, 8.0, 80f))
    }

    @Test fun `summary carries the track, and hunts saved before 1_26 still decode`() {
        val session = HuntSession(name = "Test", definition = com.example.pokemonalertsv2.data.FilterDefinition(), startedAtMillis = 0L)
        val stats = HuntStats().withFix(49.0, 8.0, 5f).withFix(49.001, 8.0, 5f)
        val summary = huntSummary(session, stats, 120_000L)
        assertEquals(2, summary.trackPoints.size)
        assertNull(summary.watch)

        val legacy = """{"name":"Old","startedAtMillis":1,"endedAtMillis":2,"activeMillis":1,"distanceMeters":3.0,
            "catches":[{"id":"a","name":"Pikachu","atMillis":5}]}"""
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString(HuntSummary.serializer(), legacy)
        assertEquals("", decoded.track)
        assertEquals(false, decoded.catches.single().shiny)
        assertNull(decoded.catches.single().point)
    }
}
