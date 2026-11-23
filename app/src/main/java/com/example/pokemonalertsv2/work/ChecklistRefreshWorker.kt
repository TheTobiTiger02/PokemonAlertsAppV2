package com.example.pokemonalertsv2.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.pokemonalertsv2.data.checklists.ChecklistRepository
import java.util.concurrent.TimeUnit

/** Imports the goDex checklists added by link again, every few hours (the Hundo one has [GoDexSyncWorker]). */
class ChecklistRefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val failed = ChecklistRepository.getInstance(applicationContext).refreshAll()
        return if (failed > 0 && runAttemptCount < 3) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "checklists_periodic_refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ChecklistRefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 60, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Imports now, e.g. a goDex link another device added. */
        fun refreshNow(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<ChecklistRefreshWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork("checklists_refresh_now", androidx.work.ExistingWorkPolicy.REPLACE, request)
            schedule(context)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_NAME)
        }
    }
}
