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
import com.example.pokemonalertsv2.data.checklists.ChecklistRepository
import com.example.pokemonalertsv2.data.database.AppDatabase
import com.example.pokemonalertsv2.data.database.isGoDexAccount
import com.example.pokemonalertsv2.data.godex.GoDexLivewireClient
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.data.godex.GoDexWriteResult
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Writes marks back to goDex: first the Hundo collection's (`godex_pending_updates`), then those
 * of the account's other collections (`checklist_pending`). One request at a time, with a gap.
 */
class GoDexWriteWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    private val client = GoDexLivewireClient()

    override suspend fun doWork(): Result {
        val repository = GoDexRepository.getInstance(applicationContext)
        writeHundo(repository)?.let { return it }
        return writeChecklists(repository) ?: Result.success()
    }

    /** Null when done; otherwise the result to stop with. */
    private suspend fun writeHundo(repository: GoDexRepository): Result? {
        val dao = AppDatabase.getDatabase(applicationContext).goDexEntryDao()
        if (dao.getNextPendingUpdate() == null) return null
        // Pending desired states survive sign-out and session expiry.
        val config = repository.currentConfig()
        if (!config.isConnected || !config.hasSession) return null

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
            when (val outcome = handle(repository, result)) {
                Outcome.DONE -> {
                    // A newer local action uses a newer revision and is not deleted here.
                    dao.deletePendingUpdateIfRevision(update.entryKey, update.revision)
                    // A bulk mark sends one request per entry: keep a gap so godex.site is not flooded.
                    if (dao.getNextPendingUpdate() != null) delay(WRITE_GAP_MILLIS)
                }
                Outcome.DROPPED -> {
                    dao.recordPendingFailure(update.entryKey, update.revision, result.message())
                    dao.deletePendingUpdateIfRevision(update.entryKey, update.revision)
                }
                else -> {
                    dao.recordPendingFailure(update.entryKey, update.revision, result.message())
                    android.util.Log.e("GoDexWriteWorker", "Hundo mark not written: ${update.entryKey}", (result as? GoDexWriteResult.RetryableFailure)?.cause)
                    return outcome.toResult()
                }
            }
        }
        GoDexSyncWorker.enqueueImmediate(applicationContext)
        return null
    }

    private suspend fun writeChecklists(repository: GoDexRepository): Result? {
        val database = AppDatabase.getDatabase(applicationContext)
        val pending = database.checklistPendingDao()
        val checklists = database.checklistDao()
        val written = LinkedHashSet<String>()
        while (true) {
            val update = pending.next() ?: break
            val config = repository.currentConfig()
            if (!config.hasSession) return null
            val url = checklists.checklist(update.checklistId)?.takeIf { it.isGoDexAccount }?.url
            if (url == null) {
                // The checklist was removed, or is no longer the account's own.
                pending.clear(update.checklistId)
                continue
            }
            val result = client.setCaught(url, config.sessionCookies, update.entryKey, update.caught)
            when (val outcome = handle(repository, result)) {
                Outcome.DONE -> {
                    pending.deleteIfRevision(update.checklistId, update.entryKey, update.revision)
                    written += update.checklistId
                    if (pending.next() != null) delay(WRITE_GAP_MILLIS)
                }
                Outcome.DROPPED -> {
                    pending.recordFailure(update.checklistId, update.entryKey, update.revision, result.message())
                    pending.deleteIfRevision(update.checklistId, update.entryKey, update.revision)
                }
                else -> {
                    pending.recordFailure(update.checklistId, update.entryKey, update.revision, result.message())
                    return outcome.toResult()
                }
            }
        }
        // Read back what goDex now holds, as the Hundo collection does after its writes.
        val repositoryOfChecklists = ChecklistRepository.getInstance(applicationContext)
        written.forEach { runCatching { repositoryOfChecklists.refresh(it) } }
        return null
    }

    private enum class Outcome { DONE, DROPPED, REAUTH, RETRY }

    private suspend fun handle(repository: GoDexRepository, result: GoDexWriteResult): Outcome {
        repository.persistRefreshedCookies(result.refreshedCookies)
        return when (result) {
            is GoDexWriteResult.Applied, is GoDexWriteResult.AlreadyApplied -> {
                repository.recordWriteSuccess()
                Outcome.DONE
            }
            is GoDexWriteResult.ReauthenticationRequired -> {
                repository.markReauthenticationRequired(result.message)
                Outcome.REAUTH
            }
            is GoDexWriteResult.RetryableFailure -> {
                repository.recordWriteError(result.message)
                Outcome.RETRY
            }
            is GoDexWriteResult.PermanentFailure -> {
                repository.recordWriteError(result.message)
                Outcome.DROPPED
            }
        }
    }

    private fun Outcome.toResult(): Result = when {
        this == Outcome.REAUTH -> Result.success()
        runAttemptCount < MAX_RETRIES -> Result.retry()
        else -> Result.failure()
    }

    private fun GoDexWriteResult.message(): String = when (this) {
        is GoDexWriteResult.ReauthenticationRequired -> message
        is GoDexWriteResult.RetryableFailure -> message
        is GoDexWriteResult.PermanentFailure -> message
        else -> ""
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "godex_write_sync"
        private const val MAX_RETRIES = 5
        private const val WRITE_GAP_MILLIS = 300L

        /** Longer than the editor's Undo snackbar, so an undone mark is never sent. */
        const val UNDO_WINDOW_SECONDS = 12L
        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueue(context: Context, afterUndoWindow: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<GoDexWriteWorker>()
                .setConstraints(constraints)
                .apply { if (afterUndoWindow) setInitialDelay(UNDO_WINDOW_SECONDS, TimeUnit.SECONDS) }
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
