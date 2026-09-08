package com.example.pokemonalertsv2.work

import androidx.work.ExistingWorkPolicy
import com.example.pokemonalertsv2.fcm.FcmAlertHandlingResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FcmAlertWorkerTest {

    @Test
    fun payload_roundTripsDeterministically() {
        val first = linkedMapOf("name" to "Pikachu", "alertId" to "6215")
        val second = linkedMapOf("alertId" to "6215", "name" to "Pikachu")

        val firstEncoded = FcmAlertWorker.FcmAlertWorkPayload.encode(first)
        val secondEncoded = FcmAlertWorker.FcmAlertWorkPayload.encode(second)

        assertEquals(firstEncoded, secondEncoded)
        assertEquals(first, FcmAlertWorker.FcmAlertWorkPayload.decode(firstEncoded))
    }

    @Test
    fun payload_rejectsMalformedJson() {
        assertNull(FcmAlertWorker.FcmAlertWorkPayload.decode("{not-json"))
    }

    @Test
    fun workName_usesMessageIdAndIsStable() {
        val payload = FcmAlertWorker.FcmAlertWorkPayload.encode(mapOf("alertId" to "6215"))

        val first = FcmAlertWorker.FcmAlertWorkPayload.workName(null, "message-1", payload)
        val repeated = FcmAlertWorker.FcmAlertWorkPayload.workName(null, "message-1", "different")
        val different = FcmAlertWorker.FcmAlertWorkPayload.workName(null, "message-2", payload)

        assertEquals(first, repeated)
        assertTrue(first.startsWith("pokemon_fcm_alert_"))
        assertEquals("pokemon_fcm_alert_".length + 64, first.length)
        assertFalse(first == different)
    }

    @Test
    fun workName_fallsBackToCanonicalPayload() {
        val firstPayload = FcmAlertWorker.FcmAlertWorkPayload.encode(
            linkedMapOf("name" to "Pikachu", "alertId" to "6215")
        )
        val samePayload = FcmAlertWorker.FcmAlertWorkPayload.encode(
            linkedMapOf("alertId" to "6215", "name" to "Pikachu")
        )

        assertEquals(
            FcmAlertWorker.FcmAlertWorkPayload.workName(null, null, firstPayload),
            FcmAlertWorker.FcmAlertWorkPayload.workName("  ", "  ", samePayload)
        )
    }

    /**
     * The point of the key: an alert on more than five topics is sent as two messages with
     * different message ids, and a device subscribed to topics in both halves receives both.
     */
    @Test
    fun workName_prefersPushKeyOverMessageId() {
        val payload = FcmAlertWorker.FcmAlertWorkPayload.encode(mapOf("alertId" to "6215"))

        val firstHalf = FcmAlertWorker.FcmAlertWorkPayload.workName("6215", "message-1", payload)
        val secondHalf = FcmAlertWorker.FcmAlertWorkPayload.workName("6215", "message-2", payload)

        assertEquals(firstHalf, secondHalf)
    }

    @Test
    fun workName_separatesAnAlertFromItsTombstone() {
        val payload = FcmAlertWorker.FcmAlertWorkPayload.encode(mapOf("alertId" to "6215"))

        val alert = FcmAlertWorker.FcmAlertWorkPayload.workName("6215", "message-1", payload)
        val tombstone = FcmAlertWorker.FcmAlertWorkPayload.workName(
            "6215:2026-09-08T12:00:00Z", "message-2", payload
        )

        assertFalse(alert == tombstone)
    }

    @Test
    fun invalidOutcome_requestsAuthoritativeSync() {
        assertTrue(FcmAlertWorker.shouldRequestAuthoritativeSync(FcmAlertHandlingResult.INVALID))
        assertFalse(FcmAlertWorker.shouldRequestAuthoritativeSync(FcmAlertHandlingResult.HANDLED))
        assertFalse(FcmAlertWorker.shouldRequestAuthoritativeSync(FcmAlertHandlingResult.DUPLICATE))
        assertTrue(FcmAlertWorker.shouldRequestAuthoritativeSync(FcmAlertHandlingResult.WEATHER_HANDLED))
        assertTrue(FcmAlertWorker.shouldRequestAuthoritativeSync(FcmAlertHandlingResult.WEATHER_DUPLICATE))
    }

    @Test
    fun retriesStopAfterThreeTotalAttempts() {
        assertTrue(FcmAlertWorker.shouldRetry(runAttemptCount = 0))
        assertTrue(FcmAlertWorker.shouldRetry(runAttemptCount = 1))
        assertFalse(FcmAlertWorker.shouldRetry(runAttemptCount = 2))
        assertFalse(FcmAlertWorker.shouldRetry(runAttemptCount = 3))
    }

    @Test
    fun duplicateMessagesKeepAlreadyEnqueuedWork() {
        assertEquals(ExistingWorkPolicy.KEEP, FcmAlertWorker.workPolicy)
    }
}
