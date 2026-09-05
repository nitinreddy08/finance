package com.budgetpace.app.domain.parser

import com.budgetpace.app.core.model.Bank
import com.budgetpace.app.core.model.ParseConfidence
import com.budgetpace.app.core.model.TransactionDirection
import com.budgetpace.app.core.money.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.regex.Pattern

class SbiTransactionParser : BankTransactionParser {
    
    // Dear UPI user A/C X5326 debited by 272.00 on date 26Jul26 trf to Zepto Marketplace Refno 211674921516
    // Tolerant tokens: "A/C" or "AC", an optional "Rs."/"INR" before the amount, and "Refno",
    // "Ref No" or "Ref:" — SBI's own template has shipped all of these spellings.
    private val debitPattern = Pattern.compile(
        "A/?C\\s+([A-Z0-9]+)\\s+debited\\s+by\\s+(?:Rs\\.?|INR)?\\s*([\\d,]+\\.?\\d*)\\s+on\\s+date\\s+(\\d{2}[A-Za-z]{3}\\d{2})\\s+trf\\s+to\\s+(.+?)\\s+Ref\\s*(?:no)?:?\\s*(\\d+)",
        Pattern.CASE_INSENSITIVE
    )

    // Format: 26Jul26. Locale.ENGLISH is pinned so month-name parsing doesn't depend on the device
    // locale, and parseCaseInsensitive() is required because the pattern above matches the message
    // case-insensitively — an all-caps "26JUL26" would otherwise drop the date.
    private val dateFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("ddMMMyy")
        .toFormatter(Locale.ENGLISH)

    override fun canParse(input: NotificationInput): Boolean {
        // The sender header alone is never sufficient: an SMS pseudo-package carries whatever the
        // network put in the header, so the bank always has to come from the message text.
        if (!MessageSources.isSupported(input.packageName)) return false
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
                val amountMinor = Money.rupeesToPaise(amountStr)
                // Money.rupeesToPaise returns 0 for anything it cannot read; reporting that as a
                // HIGH-confidence parse would post a zero-rupee categorization prompt.
                if (amountMinor <= 0L) return null
                val dateStr = debitMatcher.group(3)
                val recipient = debitMatcher.group(4)?.trim()
                val refNumber = debitMatcher.group(5)

                val date = try {
                    LocalDate.parse(dateStr, dateFormatter)
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
