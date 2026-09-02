package com.budgetpace.app.domain.parser

import com.budgetpace.app.core.model.Bank
import com.budgetpace.app.core.model.ParseConfidence
import com.budgetpace.app.core.model.TransactionDirection
import java.time.Instant
import java.time.LocalDate

/**
 * Raw text input extracted from an Android Notification.
 */
data class NotificationInput(
    val packageName: String,
    val title: String,
    val text: String,
    val receivedAt: Instant
)

/**
 * Normalized parsed transaction result as defined in §14.
 */
data class ParsedTransaction(
    val direction: TransactionDirection,
    val amountMinor: Long,
    val bank: Bank,
    val accountSuffix: String?,
    val transactionDate: LocalDate?,
    val transactionDateTime: Instant?,
    val recipient: String?,
    val sender: String?,
    val referenceNumber: String?,
    val confidence: ParseConfidence
)

/**
 * Normalized parser interface per §13.
 */
interface BankTransactionParser {
    fun canParse(input: NotificationInput): Boolean
    fun parse(input: NotificationInput): ParsedTransaction?
}
