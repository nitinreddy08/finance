package com.budgetpace.app.data.sync

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.budgetpace.app.data.google.auth.GoogleAuthorizationManager
import com.budgetpace.app.data.local.dao.TransactionDao
import com.budgetpace.app.domain.sync.SyncTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * The concrete [SyncTrigger] bound in [SyncModule]. [BudgetPaceApp][com.budgetpace.app.BudgetPaceApp]
 * injects this concrete type (not the seam interface) specifically to call [start] once at
 * startup — the interface deliberately exposes only [requestSyncSoon], so ingestion code never
 * has a way to reach WorkManager or Sheets consent directly.
 */
@Singleton
class SyncTriggerImpl @Inject constructor(
    private val transactionDao: TransactionDao,
    private val workManagerProvider: Provider<WorkManager>,
    private val authorizationManager: GoogleAuthorizationManager,
    private val syncScheduler: SyncScheduler,
) : SyncTrigger {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = AtomicBoolean(false)

    /** Idempotent. Call once, after `WorkManager.initialize()` has run. */
    fun start() {
        if (!started.compareAndSet(false, true)) return

        // Keeps the daily job in step with consent: scheduled the moment consent exists (whether
        // that's at this app-start read of the StateFlow, or a grant later), cancelled the moment
        // it's gone (disconnect, or a forgotten/expired consent).
        scope.launch {
            authorizationManager.hasConsent.collect { hasConsent ->
                if (hasConsent) syncScheduler.ensureDailyScheduled() else syncScheduler.cancelDaily()
            }
        }

        // A rising edge in the pending count is "local data changed"; coalesced into one delayed
        // unique job so a burst of expenses in one SMS batch doesn't enqueue a job per expense.
        scope.launch {
            var previousCount = 0
            transactionDao.observePendingCount().collect { count ->
                if (count > previousCount) requestSyncSoon()
                previousCount = count
            }
        }
    }

    override fun requestSyncSoon() {
        if (!authorizationManager.hasConsent.value) return
        try {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = OneTimeWorkRequestBuilder<GoogleSheetsSyncWorker>()
                .setConstraints(constraints)
                .setInitialDelay(TRIGGER_DELAY_MINUTES, TimeUnit.MINUTES)
                .build()
            workManagerProvider.get().enqueueUniqueWork(TRIGGERED_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        } catch (e: Exception) {
            // Must never throw: a scheduling hiccup is never a reason to fail the write that
            // triggered this call.
        }
    }

    companion object {
        const val TRIGGERED_WORK_NAME = "budget_pace_triggered_sync"
        private const val TRIGGER_DELAY_MINUTES = 15L
    }
}
