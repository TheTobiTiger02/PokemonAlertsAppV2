package com.example.pokemonalertsv2.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.pokemonalertsv2.data.database.AppDatabase
import com.example.pokemonalertsv2.data.godex.GoDexLivewireClient
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.data.godex.GoDexWriteResult
import java.util.concurrent.TimeUnit

class GoDexWriteWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val dao = AppDatabase.getDatabase(applicationContext).goDexEntryDao()
        if (dao.getNextPendingUpdate() == null) return Result.success()

        val repository = GoDexRepository.getInstance(applicationContext)
        val config = repository.currentConfig()
        if (!config.isConnected || !config.hasSession) {
            // Pending desired states survive sign-out and session expiry.
            return Result.success()
        }

        val client = GoDexLivewireClient()
        while (true) {
            val update = dao.getNextPendingUpdate() ?: break
            val currentConfig = repository.currentConfig()
            if (!currentConfig.hasSession) return Result.success()

            val result = client.setCaught(
                url = currentConfig.writeBackUrl.ifBlank { currentConfig.url },
                cookies = currentConfig.sessionCookies,
                entryKey = update.entryKey,
                caught = update.caught
            )
            repository.persistRefreshedCookies(result.refreshedCookies)

            when (result) {
                is GoDexWriteResult.Applied,
                is GoDexWriteResult.AlreadyApplied -> {
                    // A newer local action uses a newer revision and is not deleted here.
                    dao.deletePendingUpdateIfRevision(update.entryKey, update.revision)
                    repository.recordWriteSuccess()
                }

                is GoDexWriteResult.ReauthenticationRequired -> {
                    dao.recordPendingFailure(update.entryKey, update.revision, result.message)
                    repository.markReauthenticationRequired(result.message)
                    return Result.success()
                }

                is GoDexWriteResult.RetryableFailure -> {
                    dao.recordPendingFailure(update.entryKey, update.revision, result.message)
                    repository.recordWriteError(result.message)
                    android.util.Log.e(
                        "GoDexWriteWorker",
                        "Failed to sync pending update: ${update.entryKey}",
                        result.cause
                    )
                    return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                }

                is GoDexWriteResult.PermanentFailure -> {
                    dao.recordPendingFailure(update.entryKey, update.revision, result.message)
                    repository.recordWriteError(result.message)
                    dao.deletePendingUpdateIfRevision(update.entryKey, update.revision)
                }
            }
        }

        GoDexSyncWorker.enqueueImmediate(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "godex_write_sync"
        private const val MAX_RETRIES = 5
        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<GoDexWriteWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
