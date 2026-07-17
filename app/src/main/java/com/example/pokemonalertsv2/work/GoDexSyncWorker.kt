package com.example.pokemonalertsv2.work

import android.content.Context
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
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import java.util.concurrent.TimeUnit

class GoDexSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        GoDexRepository.getInstance(applicationContext).syncConfigured()
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { if (runAttemptCount < 3) Result.retry() else Result.failure() }
    )

    companion object {
        private const val PERIODIC_NAME = "godex_hundo_periodic_sync"
        private const val IMMEDIATE_NAME = "godex_hundo_immediate_sync"
        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<GoDexSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun enqueueImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<GoDexSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                IMMEDIATE_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_NAME)
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(IMMEDIATE_NAME)
        }
    }
}
