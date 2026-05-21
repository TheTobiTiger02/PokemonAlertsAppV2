package com.example.pokemonalertsv2.work

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.notifications.AlertNotifier
import kotlinx.coroutines.Dispatchers
import com.example.pokemonalertsv2.widget.AlertsWidgetProvider
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class AlertWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val repository = PokemonAlertsRepository.create(appContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val alerts = repository.fetchAlerts()
            val newAlerts = repository.detectNewAlerts(alerts)
            if (newAlerts.isNotEmpty()) {
                AlertNotifier.notifyAlerts(applicationContext, newAlerts)
                repository.markAlertsAsSeen(newAlerts)
            }
            newAlerts
        }.fold(
            onSuccess = { newAlerts ->
                if (newAlerts.isNotEmpty()) {
                    Log.d(TAG, "Delivered ${newAlerts.size} new alerts via background worker")
                } else {
                    Log.d(TAG, "Background worker ran with no new alerts")
                }
                AlertAlarmScheduler.onWorkFinished(applicationContext)
                // Trigger widget refresh regardless of new alerts so timestamps/UI stay current
                AlertsWidgetProvider.updateFromWorker(applicationContext)
                Result.success()
            },
            onFailure = { exception ->
                Log.w(TAG, "AlertWorker failed", exception)
                AlertAlarmScheduler.onWorkFinished(applicationContext)
                if (runAttemptCount >= MAX_RETRIES) Result.failure() else Result.retry()
            }
        )
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "pokemon_alerts_periodic"
        private const val ONCE_WORK_NAME = "pokemon_alerts_once"
        private const val MAX_RETRIES = 3
        private const val TAG = "AlertWorker"

        @VisibleForTesting
        internal val immediateSyncPolicy = ExistingWorkPolicy.KEEP
        
        // Only use network constraint for periodic work, not for immediate syncs
        private val periodicConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context) {
            try {
                val workRequest = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(periodicConstraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule periodic work", e)
            }
        }

        fun triggerImmediateSync(context: Context) {
            try {
                // No network constraint for immediate sync to avoid TooManyRequestsException
                // The worker will handle network errors gracefully
                val workRequest = OneTimeWorkRequestBuilder<AlertWorker>()
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    ONCE_WORK_NAME,
                    immediateSyncPolicy,
                    workRequest
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to trigger immediate sync", e)
            }
        }
    }
}
