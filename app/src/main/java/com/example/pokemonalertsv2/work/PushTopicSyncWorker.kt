package com.example.pokemonalertsv2.work

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.pokemonalertsv2.fcm.FcmTopicSubscriptionManager
import java.util.concurrent.TimeUnit

/**
 * Reconciles the device's FCM topic subscriptions.
 *
 * Runs on demand whenever a filter changes, and periodically so a catalog change on the server —
 * a new area, a renamed base topic — is picked up on an install nobody has touched in weeks.
 */
class PushTopicSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        FcmTopicSubscriptionManager.sync(applicationContext)
        // sync() never throws: a failed refresh keeps the existing subscription, and a failed
        // subscribe stays out of the persisted set so the next run retries it.
        return Result.success()
    }

    companion object {
        private const val TAG = "PushTopicSyncWorker"
        private const val ONCE_WORK_NAME = "push_topic_sync_once"
        private const val PERIODIC_WORK_NAME = "push_topic_sync_periodic"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Coalesces the burst of edits a filter dialog produces into one reconcile. */
        fun triggerSync(context: Context, delaySeconds: Long = 5) {
            runCatching {
                val request = OneTimeWorkRequestBuilder<PushTopicSyncWorker>()
                    .setConstraints(constraints)
                    .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    ONCE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            }.onFailure { Log.w(TAG, "Failed to enqueue push topic sync", it) }
        }

        fun schedule(context: Context) {
            runCatching {
                val request = PeriodicWorkRequestBuilder<PushTopicSyncWorker>(12, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            }.onFailure { Log.w(TAG, "Failed to schedule periodic push topic sync", it) }
        }
    }
}
