package com.budgetpace.app.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.budgetpace.app.data.google.auth.AuthorizationOutcome
import com.budgetpace.app.data.google.auth.GoogleAuthorizationManager
import com.budgetpace.app.data.google.sheets.GoogleSheetsRepository
import com.budgetpace.app.domain.auth.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Spec §54: network-aware (via WorkManager's own NetworkType.CONNECTED constraint, set where
 * this is enqueued), retryable, idempotent, and must not retry forever when authorization is
 * missing — Result.failure() (not retry()) is used for that case.
 */
@HiltWorker
class GoogleSheetsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val authorizationManager: GoogleAuthorizationManager,
    private val sheetsRepository: GoogleSheetsRepository,
    private val authRepository: AuthRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // requestAuthorization() also serves as a silent token refresh when consent was already
        // granted; only a real NeedsConsent/Failed outcome here means the user must act. Tied to
        // the signed-in account so a background refresh can't drift onto a different one.
        val signedInEmail = authRepository.currentSession.value?.email
        when (val outcome = authorizationManager.requestAuthorization(signedInEmail)) {
            is AuthorizationOutcome.Authorized -> Unit
            is AuthorizationOutcome.NeedsConsent -> {
                Log.w("GoogleSheetsSyncWorker", "Google Sheets needs re-authorization; open Settings")
                return Result.failure()
            }
            is AuthorizationOutcome.Failed -> {
                Log.e("GoogleSheetsSyncWorker", "Authorization failed: ${outcome.message}")
                return Result.failure()
            }
        }

        val workbookResult = sheetsRepository.ensureWorkbook()
        if (workbookResult.isFailure) {
            Log.e("GoogleSheetsSyncWorker", "Workbook creation failed", workbookResult.exceptionOrNull())
            return Result.retry()
        }

        val syncResult = sheetsRepository.syncPendingTransactions()
        return syncResult.fold(
            onSuccess = { count ->
                Log.d("GoogleSheetsSyncWorker", "Synced $count transaction(s) to Google Sheets")
                Result.success()
            },
            onFailure = { e ->
                Log.e("GoogleSheetsSyncWorker", "Sync failed", e)
                Result.retry()
            }
        )
    }

    companion object {
        const val WORK_NAME = "budget_pace_daily_sync"
    }
}
