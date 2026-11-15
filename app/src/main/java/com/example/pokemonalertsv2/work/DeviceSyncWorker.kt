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
import com.example.pokemonalertsv2.sync.DeviceSync
import com.example.pokemonalertsv2.sync.SyncTransportException
import java.util.concurrent.TimeUnit

/**
 * Runs a device sync cycle. One-shot after a local change, an FCM ping or the app coming to
 * the foreground; periodic every 12 hours while a sync group exists. Modelled on
 * [PushTopicSyncWorker].
 *
 * Every run syncs all collections: the engine lists the server's versions first and only
 * touches a collection that changed here or there, so a full run costs one request when
 * nothing changed.
 */
class DeviceSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = try {
        DeviceSync.engine(applicationContext).syncNow()
        DeviceSync.ensureTopic(applicationContext)
        Result.success()
    } catch (error: SyncTransportException) {
        // A 4xx other than a conflict will not fix itself by retrying soon.
        Log.w(TAG, "Device sync refused: ${error.status}")
        if (error.status in 500..599 || error.status == 429) Result.retry() else Result.failure()
    } catch (error: Exception) {
        Log.w(TAG, "Device sync failed; retrying", error)
        Result.retry()
    }

    companion object {
        private const val TAG = "DeviceSyncWorker"
        private const val ONCE_WORK_NAME = "device-sync"
        private const val PERIODIC_WORK_NAME = "device-sync-periodic"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Queues a sync. Appended rather than replacing: a running cycle is never cancelled
         * halfway through applying the group's data.
         */
        fun trigger(context: Context) {
            runCatching {
                val request = OneTimeWorkRequestBuilder<DeviceSyncWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniqueWork(ONCE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
            }.onFailure { Log.w(TAG, "Failed to enqueue device sync", it) }
        }

        fun schedulePeriodic(context: Context) {
            runCatching {
                val request = PeriodicWorkRequestBuilder<DeviceSyncWorker>(12, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }.onFailure { Log.w(TAG, "Failed to schedule periodic device sync", it) }
        }

        fun cancelPeriodic(context: Context) {
            runCatching { WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_WORK_NAME) }
        }
    }
}
