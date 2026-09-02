package com.budgetpace.app.domain.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import android.util.Log

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: CloudSyncRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("BudgetPace", "Starting WorkManager SyncWorker...")
        
        // Spec §54: Work should be network-aware, retryable, idempotent.
        // It must sync pending changes without blocking the UI.
        
        val workbookResult = syncRepository.ensureWorkbook()
        if (workbookResult.isFailure) {
            // Spec §54: Do not retry forever when authorization is missing.
            // If the failure is Auth, we might return Result.failure() instead of retry().
            Log.e("BudgetPace", "Workbook failed: ${workbookResult.exceptionOrNull()?.message}")
            return Result.retry() 
        }
        
        val syncResult = syncRepository.syncPendingChanges()
        return if (syncResult.isSuccess) {
            Log.d("BudgetPace", "Sync worker completed successfully.")
            Result.success()
        } else {
            Log.e("BudgetPace", "Sync failed: ${syncResult.exceptionOrNull()?.message}")
            Result.retry()
        }
    }
    
    companion object {
        const val WORK_NAME = "budget_pace_daily_sync"
    }
}
