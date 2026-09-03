package com.budgetpace.app.domain.parser

import com.budgetpace.app.core.model.Bank
import com.budgetpace.app.core.model.ParseConfidence
import com.budgetpace.app.core.model.TransactionDirection
import com.budgetpace.app.core.money.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.regex.Pattern

class KotakTransactionParser : BankTransactionParser {
    
    // Kotak's own SMS template has used both "AC" and "A/c" for the account label (observed live:
    // "Sent Rs.31.00 from Kotak Bank A/c X7970 to Mangi lal on 03-09-26. UPI Ref 624608313331." —
    // note the "A/c" spelling AND the space before "UPI" that the original pattern didn't allow
    // for, which together silently failed to parse and dropped the transaction entirely).
    // Sent Rs.27.00 from Kotak Bank AC X7970 to paytm.s2ebzrr@pty on 06-08-26.UPI Ref 621859049153.
    private val debitPattern = Pattern.compile(
        "Sent\\s+(?:Rs\\.?|INR)\\s*([\\d,]+\\.?\\d*)\\s+from\\s+Kotak\\s+Bank\\s+A/?[Cc]\\s+([A-Z0-9]+)\\s+to\\s+(.+?)\\s+on\\s+(\\d{2}-\\d{2}-\\d{2})\\.?\\s*UPI\\s+Ref:?\\s*(\\d+)",
        Pattern.CASE_INSENSITIVE
    )

    // Received Rs.6000.00 in your Kotak Bank AC X7970 from nitinreddy@ptyes on 06-08-26.UPI Ref:212542994030.
    private val creditPattern = Pattern.compile(
        "Received\\s+(?:Rs\\.?|INR)\\s*([\\d,]+\\.?\\d*)\\s+in\\s+your\\s+Kotak\\s+Bank\\s+A/?[Cc]\\s+([A-Z0-9]+)\\s+from\\s+(.+?)\\s+on\\s+(\\d{2}-\\d{2}-\\d{2})\\.?\\s*UPI\\s+Ref:?\\s*(\\d+)",
        Pattern.CASE_INSENSITIVE
    )

    override fun canParse(input: NotificationInput): Boolean {
        if (input.packageName != "com.google.android.apps.messaging") return false
        val text = input.text ?: return false
        return text.contains("Kotak Bank", ignoreCase = true)
    }

    override fun parse(input: NotificationInput): ParsedTransaction? {
        val text = input.text ?: return null
        
        val debitMatcher = debitPattern.matcher(text)
        if (debitMatcher.find()) {
            return parseMatch(debitMatcher, TransactionDirection.DEBIT)
        }
        
        val creditMatcher = creditPattern.matcher(text)
        if (creditMatcher.find()) {
            return parseMatch(creditMatcher, TransactionDirection.CREDIT)
        }
        
        return null
    }

    private fun parseMatch(matcher: java.util.regex.Matcher, direction: TransactionDirection): ParsedTransaction? {
        try {
            val amountStr = matcher.group(1)?.replace(",", "") ?: return null
            val amountMinor = Money.rupeesToPaise(amountStr)
            val accountSuffix = matcher.group(2)
            val party = matcher.group(3)
            val dateStr = matcher.group(4)
            val refNumber = matcher.group(5)

            val formatter = DateTimeFormatter.ofPattern("dd-MM-yy", Locale.ENGLISH)
            val date = try {
                LocalDate.parse(dateStr, formatter)
            } catch (e: DateTimeParseException) {
                null
            }

            return ParsedTransaction(
                direction = direction,
                amountMinor = amountMinor,
                bank = Bank.KOTAK,
                accountSuffix = accountSuffix,
                transactionDate = date,
                transactionDateTime = null,
                recipient = if (direction == TransactionDirection.DEBIT) party else null,
                sender = if (direction == TransactionDirection.CREDIT) party else null,
                referenceNumber = refNumber,
                confidence = ParseConfidence.HIGH
            )
        } catch (e: Exception) {
            return null
        }
    }
}
