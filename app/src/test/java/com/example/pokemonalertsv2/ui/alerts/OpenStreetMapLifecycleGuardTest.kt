package com.example.pokemonalertsv2.ui.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenStreetMapLifecycleGuardTest {

    @Test
    fun lifecycleTransitionsAndDestroyAreIdempotent() {
        val events = mutableListOf<String>()
        val guard = guardRecording(events)

        guard.start()
        guard.start()
        guard.resume()
        guard.resume()
        guard.pause()
        guard.pause()
        guard.stop()
        guard.stop()
        guard.destroy()
        guard.destroy()

        assertEquals(listOf("start", "resume", "pause", "stop", "destroy"), events)
        assertFalse(guard.isActive)
    }

    @Test
    fun destroyPausesAndStopsBeforeMarkingMapDestroyed() {
        val events = mutableListOf<String>()
        val guard = guardRecording(events)

        guard.resume()
        guard.destroy()

        assertEquals(listOf("start", "resume", "pause", "stop", "destroy"), events)
        assertFalse(guard.isActive)
    }

    @Test
    fun delayedCallbacksAreRejectedAfterDestroy() {
        val events = mutableListOf<String>()
        val guard = guardRecording(events)

        assertTrue(guard.runIfActive { events += "map-ready" })
        guard.destroy()
        assertFalse(guard.runIfActive { events += "style-loaded" })
        assertFalse(guard.runIfActive { events += "load-error" })

        assertEquals(listOf("map-ready", "destroy"), events)
    }

    private fun guardRecording(events: MutableList<String>) = OpenStreetMapLifecycleGuard(
        onStart = { events += "start" },
        onResume = { events += "resume" },
        onPause = { events += "pause" },
        onStop = { events += "stop" },
        onDestroy = { events += "destroy" }
    )
}
