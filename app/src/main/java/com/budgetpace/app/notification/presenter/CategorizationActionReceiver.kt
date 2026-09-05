package com.budgetpace.app.notification.presenter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.budgetpace.app.BuildConfig
import com.budgetpace.app.data.local.dao.CategoryDao
import com.budgetpace.app.data.local.dao.TransactionDao
import com.budgetpace.app.domain.sync.SyncTrigger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.Instant
import javax.inject.Inject

/**
 * Handles taps on the categorization notification's action buttons and its confirmation's Undo
 * (spec §21). Runs every write via `goAsync()` since a BroadcastReceiver's `onReceive` must return
 * quickly, guarded by `withTimeout` so a stuck write can never hold the system's wake lock past
 * its budget.
 *
 * Every write is a conditional DAO update that reports the row count — the only reliable way to
 * know whether this broadcast is still live or a stale/duplicate delivery lost a race with the
 * in-app chooser (spec §19/§20 requires exactly this idempotency). The confirmation notification
 * is only ever shown once the write actually applied.
 *
 * [BroadcastReceiver.onReceive] is abstract on the base class — Hilt's bytecode transform still
 * injects this `@AndroidEntryPoint` receiver, but there is no `super.onReceive(...)` to call.
 */
@AndroidEntryPoint
class CategorizationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var transactionDao: TransactionDao
    @Inject lateinit var categoryDao: CategoryDao
    @Inject lateinit var notificationManager: CategorizationNotificationManager
    @Inject lateinit var syncTrigger: SyncTrigger

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getStringExtra(CategorizationNotificationManager.EXTRA_TRANSACTION_ID)
            ?: return
        val action = intent.action ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(TIMEOUT_MS) {
                    applyAction(action, transactionId, intent)
                }
            } catch (error: Throwable) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Categorization action $action failed", error)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun applyAction(action: String, transactionId: String, intent: Intent) {
        val now = Instant.now().toEpochMilli()
        when (action) {
            CategorizationNotificationManager.ACTION_CATEGORIZE ->
                handleCategorize(transactionId, intent, now)
            CategorizationNotificationManager.ACTION_DONT_RECORD ->
                handleDontRecord(transactionId, now)
            CategorizationNotificationManager.ACTION_UNDO_DONT_RECORD ->
                handleUndo(transactionId, now)
            CategorizationNotificationManager.ACTION_PROMPT_DISMISSED ->
                // No DB write — the owner just swiped the prompt away — but the summary's count
                // of active prompts is now stale.
                notificationManager.refreshSummary()
        }
    }

    private suspend fun handleCategorize(transactionId: String, intent: Intent, now: Long) {
        val categoryId = intent.getStringExtra(CategorizationNotificationManager.EXTRA_CATEGORY_ID)
            ?: return
        val applied = transactionDao.assignCategoryIfUnset(transactionId, categoryId, now) > 0
        if (!applied) return
        val transaction = transactionDao.getById(transactionId) ?: return
        val categoryName = categoryDao.getById(categoryId)?.name ?: return
        notificationManager.showCategorizedConfirmation(transactionId, transaction.amountMinor, categoryName)
        syncTrigger.requestSyncSoon()
    }

    private suspend fun handleDontRecord(transactionId: String, now: Long) {
        val applied = transactionDao.markIgnoredIfRecorded(transactionId, now) > 0
        if (!applied) return
        val transaction = transactionDao.getById(transactionId) ?: return
        notificationManager.showDontRecordConfirmation(transactionId, transaction.amountMinor)
        syncTrigger.requestSyncSoon()
    }

    private suspend fun handleUndo(transactionId: String, now: Long) {
        val applied = transactionDao.markRecordedIfIgnored(transactionId, now) > 0
        if (!applied) return
        // Back to RECORDED with no category — the expense needs the same prompt it would have
        // gotten the first time.
        notificationManager.show(transactionId)
        syncTrigger.requestSyncSoon()
    }

    private companion object {
        const val TAG = "CategorizeAction"
        const val TIMEOUT_MS = 8_000L
    }
}
