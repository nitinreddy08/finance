package com.budgetpace.app.data.sync

import android.app.PendingIntent
import android.util.Log
import com.budgetpace.app.data.google.auth.GoogleAuthorizationManager
import com.budgetpace.app.data.google.auth.TokenResult
import com.budgetpace.app.data.google.sheets.GoogleSheetsRepository
import com.budgetpace.app.data.local.dao.DeletedTransactionDao
import com.budgetpace.app.data.local.dao.TransactionDao
import com.budgetpace.app.domain.auth.AuthRepository
import com.budgetpace.app.domain.repository.BudgetRepository
import com.budgetpace.app.domain.repository.TransactionRepository
import com.budgetpace.app.domain.sync.SyncPlanner
import com.budgetpace.app.domain.sync.SyncProblem
import com.budgetpace.app.domain.sync.classifySyncFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** What one full pass (token → workbook → plan → summary) produced. */
sealed interface SyncRunResult {
    data class Success(val syncedCount: Int) : SyncRunResult
    data class NeedsConsent(val pendingIntent: PendingIntent) : SyncRunResult
    data object Cancelled : SyncRunResult
    data class Failed(val problem: SyncProblem) : SyncRunResult
}

/**
 * The single place a sync pass actually runs: token → [GoogleSheetsRepository.verifyWorkbook] (or
 * create) → [SyncPlanner.plan] → [GoogleSheetsRepository.applyPlan] → summary tabs → persisted
 * status. Both [GoogleSheetsSyncWorker] (periodic and manual, spec §54/§55) and any other caller
 * go through this one instance, and [mutex] makes a pass single-flight even if a periodic run and
 * a manual "Sync now" happen to land at the same moment — no route to Google's API bypasses it.
 */
@Singleton
class SheetsSyncCoordinator @Inject constructor(
    private val authorizationManager: GoogleAuthorizationManager,
    private val authRepository: AuthRepository,
    private val sheetsRepository: GoogleSheetsRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionDao: TransactionDao,
    private val deletedTransactionDao: DeletedTransactionDao,
    private val budgetRepository: BudgetRepository,
    private val syncStatusStore: SyncStatusStore,
) {
    private val mutex = Mutex()

    /** The normal path: refresh the token, confirm the workbook, upload what's pending. */
    suspend fun sync(): SyncRunResult = mutex.withLock { runSync() }

    /**
     * The confirmed "Start a new sheet" action (spec: never triggered silently). Creates a fresh
     * workbook, reseeds every local transaction as PENDING and drops every tombstone so the next
     * pass uploads the owner's whole history into it, then runs that pass immediately.
     */
    suspend fun startNewSheet(): SyncRunResult = mutex.withLock {
        val email = authRepository.currentSession.value?.email
        when (val tokenResult = authorizationManager.getFreshAccessToken(email)) {
            is TokenResult.Ok -> {
                try {
                    sheetsRepository.createWorkbook(tokenResult.accessToken, email)
                    transactionDao.markAllPending()
                    deletedTransactionDao.getAll().forEach { deletedTransactionDao.deleteById(it.transactionId) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@withLock recordAndReturnFailure(e)
                }
                runSync()
            }
            is TokenResult.NeedsConsent -> SyncRunResult.NeedsConsent(tokenResult.pendingIntent)
            TokenResult.Cancelled -> SyncRunResult.Cancelled
            is TokenResult.Failed -> recordAndReturnFailure(tokenResult.cause)
        }
    }

    private suspend fun runSync(): SyncRunResult {
        syncStatusStore.recordAttemptStarted()
        val email = authRepository.currentSession.value?.email

        val token = when (val tokenResult = authorizationManager.getFreshAccessToken(email)) {
            is TokenResult.Ok -> tokenResult.accessToken
            is TokenResult.NeedsConsent -> return SyncRunResult.NeedsConsent(tokenResult.pendingIntent)
            TokenResult.Cancelled -> return SyncRunResult.Cancelled
            is TokenResult.Failed -> return recordAndReturnFailure(tokenResult.cause)
        }

        return try {
            val cachedId = sheetsRepository.spreadsheetId
            val cachedOwner = sheetsRepository.spreadsheetOwnerEmail

            val workbookId = if (cachedId == null) {
                sheetsRepository.createWorkbook(token, email)
            } else {
                if (!cachedOwner.isNullOrBlank() && !email.isNullOrBlank() && cachedOwner != email) {
                    val problem = SyncProblem.AccountChanged(cachedOwner)
                    syncStatusStore.recordFailure(problem)
                    return SyncRunResult.Failed(problem)
                }
                sheetsRepository.verifyWorkbook(token, cachedId)
                cachedId
            }

            val existingRows = sheetsRepository.fetchExpenseRowIndex(token, workbookId)
            val pending = transactionRepository.getPending()
            val tombstoneIds = deletedTransactionDao.getAll().map { it.transactionId }
            val plan = SyncPlanner.plan(existingRows, pending, tombstoneIds)

            if (!plan.isEmpty) {
                sheetsRepository.applyPlan(token, workbookId, plan)
                for (txn in plan.markSynced) {
                    transactionDao.markSyncedIfUnchanged(txn.id.toString(), txn.updatedAt.toEpochMilli())
                }
                for (tombstoneId in plan.tombstonesDone) {
                    deletedTransactionDao.deleteById(tombstoneId)
                }
            }

            // Spec: a summary-tab failure is a warning, never a reason to report the pass as
            // failed — the rows applyPlan already wrote must not be treated as un-synced.
            try {
                val summary = budgetRepository.observeActiveMonthSummary().first()
                if (summary != null) sheetsRepository.syncSummaryTabs(token, workbookId, summary)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Summary tab refresh failed (non-fatal): ${e.javaClass.simpleName}")
            }

            syncStatusStore.recordSuccess()
            SyncRunResult.Success(plan.updates.size + plan.appends.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordAndReturnFailure(e)
        }
    }

    private fun recordAndReturnFailure(cause: Throwable?): SyncRunResult.Failed {
        val facts = (cause ?: IllegalStateException("Sync failed with no cause")).toFailureFacts()
        val problem = classifySyncFailure(facts)
        syncStatusStore.recordFailure(problem)
        return SyncRunResult.Failed(problem)
    }

    companion object {
        private const val TAG = "SheetsSyncCoordinator"
    }
}
