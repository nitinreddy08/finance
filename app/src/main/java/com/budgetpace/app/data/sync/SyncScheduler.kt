package com.budgetpace.app.data.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Every WorkManager touch point for Sheets backup, in one place: the daily periodic job (spec
 * §54), the manual "Sync now" one-off (spec §55), and the observation both the switch and the
 * spinner in [com.budgetpace.app.feature.settings.GoogleBackupScreen] bind to — so "Daily backup:
 * On" reflects whether the job is really scheduled, not a value remembered independently of it.
 */
@Singleton
class SyncScheduler @Inject constructor(
    private val workManagerProvider: Provider<WorkManager>,
) {
    fun ensureDailyScheduled() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<GoogleSheetsSyncWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        workManagerProvider.get().enqueueUniquePeriodicWork(DAILY_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelDaily() {
        workManagerProvider.get().cancelUniqueWork(DAILY_WORK_NAME)
    }

    /**
     * "Sync now": expedited so it starts immediately, and unique+KEEP so leaving the screen (which
     * would cancel a job tied to a lifecycle scope) cannot interrupt a pass already running.
     */
    fun enqueueManualSyncNow() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<GoogleSheetsSyncWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManagerProvider.get().enqueueUniqueWork(MANUAL_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun observeDailyBackupEnabled(): Flow<Boolean> =
        workManagerProvider.get().getWorkInfosForUniqueWorkFlow(DAILY_WORK_NAME).map { infos ->
            infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        }

    fun observeManualSyncRunning(): Flow<Boolean> =
        workManagerProvider.get().getWorkInfosForUniqueWorkFlow(MANUAL_WORK_NAME).map { infos ->
            infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        }

    companion object {
        const val DAILY_WORK_NAME = "budget_pace_daily_sync"
        const val MANUAL_WORK_NAME = "budget_pace_manual_sync"
    }
}
