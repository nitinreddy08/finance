package com.budgetpace.app.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs one [SheetsSyncCoordinator] pass under WorkManager — used both for the daily periodic job
 * and the manual "Sync now" one-off (spec §54/§55); [SyncScheduler] is what tells them apart.
 *
 * Network-aware via the `NetworkType.CONNECTED` constraint set where each is enqueued, retryable,
 * idempotent (a re-run just re-plans from current state), and must not retry forever when the
 * problem needs the owner — [com.budgetpace.app.domain.sync.SyncProblem.isRetryableInBackground]
 * is what [SheetsSyncCoordinator] already checked in classifying it, so this only has to read it.
 */
@HiltWorker
class GoogleSheetsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val coordinator: SheetsSyncCoordinator,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = when (val outcome = coordinator.sync()) {
        is SyncRunResult.Success -> {
            Log.d(TAG, "Synced ${outcome.syncedCount} expense(s) to Google Sheets")
            Result.success()
        }
        is SyncRunResult.NeedsConsent -> {
            // A worker has no Activity to launch the consent PendingIntent from; the owner
            // reconnects from the Google backup screen instead.
            Log.w(TAG, "Google Sheets needs to be reconnected")
            Result.failure()
        }
        is SyncRunResult.Cancelled -> Result.success()
        is SyncRunResult.Failed -> {
            Log.w(TAG, "Sync failed: ${outcome.problem.code}")
            if (outcome.problem.isRetryableInBackground) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "GoogleSheetsSyncWorker"
    }
}
