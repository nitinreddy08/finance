package com.budgetpace.app.domain.parser

import com.budgetpace.app.core.model.Bank
import com.budgetpace.app.core.model.ParseConfidence
import com.budgetpace.app.core.model.TransactionDirection
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.regex.Pattern

class SbiTransactionParser : BankTransactionParser {
    
    // Dear UPI user A/C X5326 debited by 272.00 on date 26Jul26 trf to Zepto Marketplace Refno 211674921516
    private val debitPattern = Pattern.compile(
        "A/C\\s+([A-Z0-9]+)\\s+debited\\s+by\\s+([\\d,]+\\.?\\d*)\\s+on\\s+date\\s+(\\d{2}[A-Za-z]{3}\\d{2})\\s+trf\\s+to\\s+(.+?)\\s+Refno\\s+(\\d+)",
        Pattern.CASE_INSENSITIVE
    )

    override fun canParse(input: NotificationInput): Boolean {
        if (input.packageName != "com.google.android.apps.messaging") return false
        val text = input.text ?: return false
        return text.contains("SBI", ignoreCase = true) || text.contains("State Bank", ignoreCase = true)
    }

    override fun parse(input: NotificationInput): ParsedTransaction? {
        val text = input.text ?: return null
        
        val debitMatcher = debitPattern.matcher(text)
        if (debitMatcher.find()) {
            try {
                val accountSuffix = debitMatcher.group(1)
                val amountStr = debitMatcher.group(2)?.replace(",", "") ?: return null
                val amountMinor = (amountStr.toDouble() * 100).toLong()
                val dateStr = debitMatcher.group(3)
                val recipient = debitMatcher.group(4)?.trim()
                val refNumber = debitMatcher.group(5)
                
                // Format: 26Jul26
                val formatter = DateTimeFormatter.ofPattern("ddMMMyy")
                val date = try {
                    LocalDate.parse(dateStr, formatter)
                } catch (e: DateTimeParseException) {
                    null
                }

                return ParsedTransaction(
                    direction = TransactionDirection.DEBIT,
                    amountMinor = amountMinor,
                    bank = Bank.SBI,
                    accountSuffix = accountSuffix,
                    transactionDate = date,
                    transactionDateTime = null,
                    recipient = recipient,
                    sender = null,
                    referenceNumber = refNumber,
                    confidence = ParseConfidence.HIGH
                )
            } catch (e: Exception) {
                return null
            }
        }
        
        return null
    }
}
