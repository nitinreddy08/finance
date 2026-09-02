package com.budgetpace.app.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.budgetpace.app.domain.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class GoogleSheetsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionRepository: TransactionRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("GoogleSheetsSyncWorker", "Starting sync to Google Sheets")

        return try {
            // Spec §52: upload PENDING changes across every month, not just the current one —
            // an older month that failed to sync earlier must still get a chance to catch up.
            val pending = transactionRepository.getPending()

            if (pending.isEmpty()) {
                Log.d("GoogleSheetsSyncWorker", "No transactions to sync")
                return Result.success()
            }

            // The real Sheets API call isn't wired up yet: appending a new row when the
            // transaction's UUID isn't already present in the sheet, otherwise updating that row
            // in place (§52), using an authorized Google account (§7/§51). That requires the
            // user's own Google Cloud OAuth client, which this app does not ship credentials for
            // (see AuthRepositoryImpl / BuildConfig.GOOGLE_CLIENT_ID). Until it exists, leave
            // every pending transaction's syncState untouched rather than falsely marking it
            // SYNCED — the Settings "N changes waiting" count (§54) must stay honest.
            Log.w(
                "GoogleSheetsSyncWorker",
                "${pending.size} transaction(s) pending, but the Sheets API call is not yet implemented"
            )
            Result.failure()
        } catch (e: Exception) {
            Log.e("GoogleSheetsSyncWorker", "Sync failed", e)
            Result.retry()
        }
    }
}
