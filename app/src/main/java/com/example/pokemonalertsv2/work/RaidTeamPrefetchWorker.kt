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
import com.example.pokemonalertsv2.raidwatch.RaidTeamPrefetcher
import com.example.pokemonalertsv2.raidwatch.RaidWatchController
import com.example.pokemonalertsv2.raidwatch.RaidWatchStore
import java.util.concurrent.TimeUnit

/**
 * Computes the suggested six for the raid currently being watched, then re-posts the Live
 * Update so the team appears in it.
 *
 * Not run on the arrival service's own scope: `ArrivalTrackingService.handleArrival` stops the
 * service immediately after starting the watch, which cancels that scope mid-request. A worker
 * also brings the retry and network constraint this needs -- arriving at a gym with one bar of
 * signal is the normal case, not the exception.
 */
class RaidTeamPrefetchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val watch = RaidWatchStore(applicationContext).current() ?: return Result.success()
        val snapshot = RaidTeamPrefetcher.compute(applicationContext, watch.alert)
        // A null snapshot is a failed or unresolvable ranking; retry rather than leaving the
        // notification permanently saying the team is on its way.
        if (snapshot == null) return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        RaidWatchController.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "raid_team_prefetch"
        private const val MAX_ATTEMPTS = 3

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<RaidTeamPrefetchWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            // REPLACE, not KEEP: a new arrival means a new boss, and the in-flight job is
            // computing a team for a raid the user has already left.
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
