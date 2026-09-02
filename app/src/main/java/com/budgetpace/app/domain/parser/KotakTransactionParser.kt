package com.budgetpace.app.domain.parser

import com.budgetpace.app.core.model.Bank
import com.budgetpace.app.core.model.ParseConfidence
import com.budgetpace.app.core.model.TransactionDirection
import com.budgetpace.app.core.money.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class KotakTransactionParser : BankTransactionParser {

    // Example Debit: "Sent Rs.27.00 from Kotak Bank AC X7970 to paytm.s2ebzrr@pty on 06-08-26.UPI Ref 621859049153."
    // Example Credit: "Received Rs.6000.00 in your Kotak Bank AC X7970 from nitinreddy@ptyes on 06-08-26.UPI Ref:212542994030."
    
    private val debitRegex = """Sent Rs\.?\s*([\d,]+\.?\d*)\s+from Kotak Bank AC ([A-Za-z0-9]+) to ([\w\.\-@\s]+?) on (\d{2}-\d{2}-\d{2})\.?UPI Ref:?\s*(\d+)""".toRegex(RegexOption.IGNORE_CASE)
    private val creditRegex = """Received Rs\.?\s*([\d,]+\.?\d*)\s+in your Kotak Bank AC ([A-Za-z0-9]+) from ([\w\.\-@\s]+?) on (\d{2}-\d{2}-\d{2})\.?UPI Ref:?\s*(\d+)""".toRegex(RegexOption.IGNORE_CASE)

    override fun canParse(input: NotificationInput): Boolean {
        // Broad check for Kotak mentions to fail fast
        return input.text.contains("Kotak Bank", ignoreCase = true)
    }

    override fun parse(input: NotificationInput): ParsedTransaction? {
        if (!canParse(input)) return null
        
        val text = input.text.replace("\n", " ").trim()
        
        // Try Debit
        debitRegex.find(text)?.let { match ->
            return buildParsedTransaction(
                match = match,
                direction = TransactionDirection.DEBIT
            )
        }
        
        // Try Credit
        creditRegex.find(text)?.let { match ->
            return buildParsedTransaction(
                match = match,
                direction = TransactionDirection.CREDIT
            )
        }
        
        return null
    }
    
    private fun buildParsedTransaction(match: MatchResult, direction: TransactionDirection): ParsedTransaction {
        val amountStr = match.groupValues[1].replace(",", "")
        val accountSuffix = match.groupValues[2]
        val party = match.groupValues[3].trim()
        val dateStr = match.groupValues[4]
        val reference = match.groupValues[5]
        
        val amountMinor = Money.rupeesToPaise(amountStr)
        
        val parsedDate = try {
            // "dd-MM-yy" mapping to 2000s
            LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd-MM-yy"))
        } catch (e: DateTimeParseException) {
            null
        }

        return ParsedTransaction(
            direction = direction,
            amountMinor = amountMinor,
            bank = Bank.KOTAK,
            accountSuffix = accountSuffix,
            transactionDate = parsedDate,
            transactionDateTime = null,
            recipient = if (direction == TransactionDirection.DEBIT) party else null,
            sender = if (direction == TransactionDirection.CREDIT) party else null,
            referenceNumber = reference,
            confidence = ParseConfidence.HIGH
        )
    }
}
