package com.example.pokemonalertsv2.work

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.pokemonalertsv2.fcm.FcmAlertHandler
import com.example.pokemonalertsv2.fcm.FcmAlertHandlingResult
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Owns FCM alert processing after FirebaseMessagingService returns so Android can
 * safely stop the service process without losing the Room write, notification,
 * or widget refresh.
 */
class FcmAlertWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val encodedPayload = inputData.getString(KEY_ENCODED_PAYLOAD)
        val payload = encodedPayload?.let(FcmAlertWorkPayload::decode)

        if (payload == null) {
            Log.w(TAG, "FCM work payload is missing or invalid; requesting authoritative sync")
            enqueueAuthoritativeSync(applicationContext)
            return Result.success()
        }

        return runCatching {
            FcmAlertHandler.handle(applicationContext, payload)
        }.fold(
            onSuccess = { outcome ->
                if (shouldRequestAuthoritativeSync(outcome)) {
                    enqueueAuthoritativeSync(applicationContext)
                }
                when (outcome) {
                    FcmAlertHandlingResult.INVALID -> {
                        Log.w(TAG, "FCM alert payload could not be parsed; requesting authoritative sync")
                    }
                    FcmAlertHandlingResult.DUPLICATE ->
                        Log.d(TAG, "Skipped notification for an already handled FCM alert")
                    FcmAlertHandlingResult.HANDLED ->
                        Log.d(TAG, "Handled FCM alert in durable background work")
                }
                Result.success()
            },
            onFailure = { exception ->
                Log.w(TAG, "Durable FCM alert handling failed", exception)
                if (shouldRetry(runAttemptCount)) Result.retry() else Result.failure()
            }
        )
    }

    companion object {
        private const val TAG = "FcmAlertWorker"
        private const val KEY_ENCODED_PAYLOAD = "encoded_fcm_payload"
        private const val WORK_NAME_PREFIX = "pokemon_fcm_alert_"
        private const val MAX_RUN_ATTEMPTS = 3

        @VisibleForTesting
        internal val workPolicy = ExistingWorkPolicy.KEEP

        fun enqueue(context: Context, messageId: String?, data: Map<String, String>) {
            runCatching {
                val encodedPayload = FcmAlertWorkPayload.encode(data)
                val request = OneTimeWorkRequestBuilder<FcmAlertWorker>()
                    .setInputData(
                        Data.Builder()
                            .putString(KEY_ENCODED_PAYLOAD, encodedPayload)
                            .build()
                    )
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    FcmAlertWorkPayload.workName(messageId, encodedPayload),
                    workPolicy,
                    request
                )
            }.onFailure { exception ->
                Log.e(TAG, "Failed to persist FCM alert work; requesting authoritative sync", exception)
                enqueueAuthoritativeSync(context.applicationContext)
            }
        }

        internal fun enqueueAuthoritativeSync(context: Context) {
            AlertWorker.triggerImmediateSync(context.applicationContext)
        }

        @VisibleForTesting
        internal fun shouldRetry(runAttemptCount: Int): Boolean =
            runAttemptCount < MAX_RUN_ATTEMPTS - 1

        @VisibleForTesting
        internal fun shouldRequestAuthoritativeSync(outcome: FcmAlertHandlingResult): Boolean =
            outcome == FcmAlertHandlingResult.INVALID
    }

    internal object FcmAlertWorkPayload {
        private val payloadSerializer = MapSerializer(String.serializer(), String.serializer())
        private val json = Json {
            encodeDefaults = true
            explicitNulls = false
        }

        fun encode(data: Map<String, String>): String =
            json.encodeToString(payloadSerializer, data.toSortedMap())

        fun decode(encodedPayload: String): Map<String, String>? =
            runCatching {
                json.decodeFromString(payloadSerializer, encodedPayload)
            }.getOrNull()

        fun workName(messageId: String?, encodedPayload: String): String {
            val identity = messageId?.trim()?.takeIf(String::isNotEmpty) ?: encodedPayload
            return WORK_NAME_PREFIX + identity.sha256()
        }

        private fun String.sha256(): String =
            MessageDigest.getInstance("SHA-256")
                .digest(toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
