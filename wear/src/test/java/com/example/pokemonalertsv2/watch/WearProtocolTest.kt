package com.example.pokemonalertsv2.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearProtocolTest {
    @Test fun `reads the phone's session JSON, ignoring fields it does not know`() {
        val json = """{"kind":"hunt","name":"Hundos","startedAtMillis":1,"snapshotAtMillis":10000,"activeMillis":60000,
            "paused":false,"caught":2,"distanceMeters":1250.0,"target":"Dratini","remaining":null,"futureField":true}"""
        val state = WearProtocol.decodeState(json)!!
        assertTrue(state.active && state.isHunt)
        assertEquals("Dratini", state.target)
        // Keeps counting from the snapshot while running...
        assertEquals(65_000L, state.activeMillisAt(15_000))
        // ...and stands still while paused.
        assertEquals(60_000L, state.copy(paused = true).activeMillisAt(15_000))
        assertNull(WearProtocol.decodeState("not json"))
    }

    @Test fun `formats the clock and distance`() {
        assertEquals("4:05", formatElapsed(245_000))
        assertEquals("1:02:03", formatElapsed(3_723_000))
        assertEquals("850 m", formatDistance(850.0))
        assertEquals("1.3 km", formatDistance(1_250.0))
    }

    @Test fun `samples use the phone's field names`() {
        val text = WearProtocol.encodeSample(WatchSample(5, 120, 300, 12.5)).decodeToString()
        assertEquals("""{"atMillis":5,"heartRate":120,"stepsTotal":300,"caloriesTotal":12.5}""", text)
    }
}
