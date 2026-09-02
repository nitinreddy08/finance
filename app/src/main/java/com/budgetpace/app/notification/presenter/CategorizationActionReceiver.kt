package com.budgetpace.app.notification.presenter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.budgetpace.app.core.model.RecordDecision
import com.budgetpace.app.data.local.dao.TransactionDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Handles the tap on a categorization notification's action buttons (spec §21). Runs the DB
 * write via goAsync() since a BroadcastReceiver's onReceive must return quickly, and guards
 * every write against the transaction's current state so a stale/duplicate broadcast (spec
 * §19/§20: "user categorization actions must also be idempotent") is a safe no-op.
 */
@AndroidEntryPoint
class CategorizationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var transactionDao: TransactionDao

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getStringExtra(CategorizationNotificationManager.EXTRA_TRANSACTION_ID)
            ?: return
        val action = intent.action ?: return
        val notificationId = CategorizationNotificationManager.notificationIdFor(transactionId)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                applyAction(action, transactionId, intent)
            } finally {
                NotificationManagerCompat.from(context).cancel(notificationId)
                pendingResult.finish()
            }
        }
    }

    private suspend fun applyAction(action: String, transactionId: String, intent: Intent) {
        val existing = transactionDao.getById(transactionId) ?: return
        // Only act on a transaction still awaiting the user's decision; a second delivery of the
        // same broadcast (or a tap on a notification whose transaction was already resolved) is
        // a no-op rather than re-prompting or clobbering a later edit.
        if (existing.categoryId != null || existing.recordDecision != RecordDecision.RECORDED.name) return

        val now = Instant.now().toEpochMilli()
        when (action) {
            CategorizationNotificationManager.ACTION_CATEGORIZE -> {
                val categoryId = intent.getStringExtra(CategorizationNotificationManager.EXTRA_CATEGORY_ID)
                    ?: return
                transactionDao.updateCategory(transactionId, categoryId, now)
            }
            CategorizationNotificationManager.ACTION_DONT_RECORD -> {
                transactionDao.updateRecordDecision(transactionId, RecordDecision.IGNORED.name, now)
            }
        }
    }
}
