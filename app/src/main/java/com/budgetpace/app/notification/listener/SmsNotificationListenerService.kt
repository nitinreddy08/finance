package com.budgetpace.app.notification.listener

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.budgetpace.app.core.model.ParseConfidence
import com.budgetpace.app.core.model.RecordDecision
import com.budgetpace.app.core.model.SyncState
import com.budgetpace.app.core.model.Transaction
import com.budgetpace.app.core.model.TransactionDirection
import com.budgetpace.app.data.local.dao.CategoryDao
import com.budgetpace.app.data.local.dao.TransactionDao
import com.budgetpace.app.data.local.mapper.toDomain
import com.budgetpace.app.data.local.mapper.toEntity
import com.budgetpace.app.domain.duplicate.DuplicateDetector
import com.budgetpace.app.domain.parser.NotificationInput
import com.budgetpace.app.domain.parser.ParserCoordinator
import com.budgetpace.app.domain.usecase.EnsureActiveMonthUseCase
import com.budgetpace.app.notification.presenter.CategorizationNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/**
 * Spec §11: the single dedicated NotificationListenerService for the whole app. Receives a
 * posted Google Messages notification, parses it, de-duplicates it, saves it, and — for a debit
 * awaiting categorization — shows the categorization notification (spec §21).
 */
@AndroidEntryPoint
class SmsNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var parserCoordinator: ParserCoordinator
    @Inject lateinit var transactionDao: TransactionDao
    @Inject lateinit var categoryDao: CategoryDao
    @Inject lateinit var ensureActiveMonth: EnsureActiveMonthUseCase
    @Inject lateinit var categorizationNotificationManager: CategorizationNotificationManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val googleMessagesPackage = "com.google.android.apps.messaging"

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null || sbn.packageName != googleMessagesPackage) return

        val extras = sbn.notification.extras
        val text = extras.getCharSequence("android.bigText")?.toString()
            ?: extras.getCharSequence("android.text")?.toString()
        if (text.isNullOrBlank()) return

        val input = NotificationInput(
            packageName = sbn.packageName,
            title = extras.getString("android.title"),
            text = text,
            receivedAt = Instant.ofEpochMilli(sbn.postTime)
        )

        // Spec §14: only high-confidence parses become transactions/prompts.
        val parsed = parserCoordinator.parse(input) ?: return
        if (parsed.confidence != ParseConfidence.HIGH) return

        serviceScope.launch {
            try {
                val duplicateKey = DuplicateDetector.getDuplicateKey(parsed)

                // Spec §19: bank+reference is authoritative when present, otherwise fall back to
                // the fingerprint. Either way, check BEFORE inserting so a redelivered
                // notification never creates a second transaction or a second prompt.
                val existing = parsed.referenceNumber?.let {
                    transactionDao.findByBankRef(parsed.bank.name, it)
                } ?: transactionDao.findByDuplicateKey(duplicateKey)
                if (existing != null) return@launch

                val activeMonth = ensureActiveMonth()
                val now = Instant.now()

                // Spec §9: credits are not spending and don't need categorization — record them
                // for the ledger but never prompt.
                val isCredit = parsed.direction == TransactionDirection.CREDIT

                val transaction = Transaction(
                    id = UUID.randomUUID(),
                    monthId = activeMonth.id,
                    amountMinor = parsed.amountMinor,
                    currency = "INR",
                    direction = parsed.direction,
                    categoryId = null,
                    transactionDateTime = parsed.transactionDateTime,
                    transactionDate = parsed.transactionDate
                        ?: input.receivedAt.atZone(ZoneId.systemDefault()).toLocalDate(),
                    notificationReceivedAt = input.receivedAt,
                    bank = parsed.bank,
                    accountSuffix = parsed.accountSuffix,
                    recipient = parsed.recipient,
                    sender = parsed.sender,
                    referenceNumber = parsed.referenceNumber,
                    sourcePackage = sbn.packageName,
                    sourceSender = input.title,
                    sourceMessageHash = text.hashCode().toString(),
                    duplicateKey = duplicateKey,
                    recordDecision = RecordDecision.RECORDED,
                    syncState = SyncState.PENDING,
                    parserVersion = "1.0",
                    createdAt = now,
                    updatedAt = now,
                )

                transactionDao.insert(transaction.toEntity())

                if (!isCredit) {
                    val quickCategories = categoryDao.getByMonth(activeMonth.id.toString())
                        .filter { it.active }
                        .map { it.toDomain() }
                    categorizationNotificationManager.showPrompt(transaction, quickCategories)
                }
            } catch (e: Exception) {
                Log.e("SmsListener", "Failed to process notification", e)
            }
        }
    }
}
