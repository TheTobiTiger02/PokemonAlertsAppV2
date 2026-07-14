package com.example.pokemonalertsv2.fcm

import com.example.pokemonalertsv2.data.PokemonAlert
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FcmAlertProcessorTest {

    @Test
    fun process_newAlert_runsDurableStepsInOrder() = runTest {
        val calls = mutableListOf<String>()
        val alert = sampleAlert()
        val processor = processor(calls, isNew = true)

        val result = processor.process(alert)

        assertEquals(FcmAlertHandlingResult.HANDLED, result)
        assertEquals(
            listOf("detect", "upsert", "clearExpired", "notify", "markSeen", "widget"),
            calls
        )
    }

    @Test
    fun process_duplicateAlert_updatesCacheAndWidgetWithoutRenotifying() = runTest {
        val calls = mutableListOf<String>()
        val processor = processor(calls, isNew = false)

        val result = processor.process(sampleAlert())

        assertEquals(FcmAlertHandlingResult.DUPLICATE, result)
        assertEquals(listOf("detect", "upsert", "clearExpired", "widget"), calls)
    }

    private fun processor(calls: MutableList<String>, isNew: Boolean) = FcmAlertProcessor(
        detectNew = {
            calls += "detect"
            isNew
        },
        upsert = { calls += "upsert" },
        clearExpired = { calls += "clearExpired" },
        notify = { calls += "notify" },
        markSeen = { calls += "markSeen" },
        requestWidgetUpdate = { calls += "widget" }
    )

    private fun sampleAlert() = PokemonAlert(
        id = 6215,
        name = "Test alert",
        description = "Background delivery",
        longitude = 8.6,
        latitude = 49.8,
        endTime = "2030-01-01 12:00:00"
    )
}
