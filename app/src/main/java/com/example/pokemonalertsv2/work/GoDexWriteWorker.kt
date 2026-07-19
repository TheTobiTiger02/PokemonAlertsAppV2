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
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class GoDexWriteWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.goDexEntryDao()
        val pendingUpdates = dao.getPendingUpdates()

        if (pendingUpdates.isEmpty()) {
            return Result.success()
        }

        val repository = GoDexRepository.getInstance(applicationContext)
        val config = repository.config.value

        if (!config.isConnected || !config.hasSession) {
            // Write-back sync is disabled or user is logged out, clear queue
            dao.clearPendingUpdates()
            return Result.success()
        }

        val client = GoDexLivewireClient()

        for (update in pendingUpdates) {
            val targetUrl = config.writeBackUrl.ifBlank { config.url }
            val result = client.toggleCaught(
                url = targetUrl,
                cookies = config.sessionCookies,
                entryKey = update.entryKey,
                caught = update.caught
            )

            if (result.isSuccess) {
                dao.deletePendingUpdate(update.id)
            } else {
                val error = result.exceptionOrNull()
                android.util.Log.e("GoDexWriteWorker", "Failed to sync pending update: ${update.entryKey}", error)
                // If it is a bad request or missing resource error, we should discard it to prevent blocking the queue forever
                if (error is IllegalStateException && error.message?.contains("Target Pokémon not found") == true) {
                    dao.deletePendingUpdate(update.id)
                    continue
                }
                // For temporary network failures, retry the worker
                return if (runAttemptCount < 5) Result.retry() else Result.failure()
            }
        }

        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "godex_write_sync"
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
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
