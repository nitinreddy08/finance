package com.budgetpace.app.notification.listener

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.budgetpace.app.domain.parser.NotificationInput
import com.budgetpace.app.domain.parser.ParserCoordinator
import com.budgetpace.app.core.model.*
import com.budgetpace.app.domain.repository.TransactionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class SmsNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var parserCoordinator: ParserCoordinator

    @Inject
    lateinit var transactionRepository: TransactionRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title")
        val text = extras.getCharSequence("android.text")?.toString()
        val bigText = extras.getCharSequence("android.bigText")?.toString()
        
        val contentToParse = bigText ?: text
        
        val input = NotificationInput(
            packageName = packageName,
            title = title,
            text = contentToParse,
            receivedAt = Instant.ofEpochMilli(sbn.postTime)
        )

        val parsed = parserCoordinator.parse(input)
        
        if (parsed != null && parsed.confidence == ParseConfidence.HIGH) {
            // Save to database
            val transaction = Transaction(
                id = UUID.randomUUID(),
                monthId = UUID.randomUUID(), // Would normally map to correct month
                amountMinor = parsed.amountMinor,
                currency = "INR",
                direction = parsed.direction,
                categoryId = null, // Needs user categorization later
                transactionDateTime = parsed.transactionDateTime,
                transactionDate = parsed.transactionDate ?: Instant.now().atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                notificationReceivedAt = input.receivedAt,
                bank = parsed.bank,
                accountSuffix = parsed.accountSuffix,
                recipient = parsed.recipient,
                sender = parsed.sender,
                referenceNumber = parsed.referenceNumber,
                sourcePackage = packageName,
                sourceSender = title,
                sourceMessageHash = contentToParse?.hashCode()?.toString(),
                duplicateKey = parsed.referenceNumber ?: "${parsed.bank.name}_${parsed.amountMinor}_${parsed.transactionDate}",
                recordDecision = RecordDecision.RECORDED,
                syncState = SyncState.PENDING,
                parserVersion = "1.0",
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            
            serviceScope.launch {
                try {
                    transactionRepository.add(transaction)
                    // Future enhancement: Fire a local notification here asking user to categorize this transaction!
                } catch (e: Exception) {
                    Log.e("SmsListener", "Duplicate or error saving transaction: ${e.message}")
                }
            }
        }
    }
}
