package com.budgetpace.app.domain.parser

import com.budgetpace.app.core.model.Bank
import com.budgetpace.app.core.model.ParseConfidence
import com.budgetpace.app.core.model.TransactionDirection
import com.budgetpace.app.core.money.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.util.Locale

class SbiTransactionParser : BankTransactionParser {

    // Example: "Dear UPI user A/C X5326 debited by 272.00 on date 26Jul26 trf to Zepto Marketplace Refno 211674921516 If not u? call-1800111109 for other services-18001234-SBI"
    
    private val debitRegex = """A/C\s+([A-Za-z0-9]+)\s+debited by\s+([\d,]+\.?\d*)\s+on date\s+(\d{2}[A-Za-z]{3}\d{2})\s+trf to\s+(.*?)\s+Refno\s+(\d+)""".toRegex(RegexOption.IGNORE_CASE)

    override fun canParse(input: NotificationInput): Boolean {
        // Fast fail check for SBI characteristics
        return input.text.contains("SBI", ignoreCase = true) || input.text.contains("debited by", ignoreCase = true)
    }

    override fun parse(input: NotificationInput): ParsedTransaction? {
        if (!canParse(input)) return null
        
        val text = input.text.replace("\n", " ").trim()
        
        debitRegex.find(text)?.let { match ->
            val accountSuffix = match.groupValues[1]
            val amountStr = match.groupValues[2].replace(",", "")
            val dateStr = match.groupValues[3]
            val recipient = match.groupValues[4].trim()
            val reference = match.groupValues[5]
            
            val amountMinor = Money.rupeesToPaise(amountStr)
            
            // SBI format: 26Jul26
            val formatter = DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("ddMMMyy")
                .toFormatter(Locale.ENGLISH)
                
            val parsedDate = try {
                LocalDate.parse(dateStr, formatter)
            } catch (e: DateTimeParseException) {
                null
            }

            return ParsedTransaction(
                direction = TransactionDirection.DEBIT,
                amountMinor = amountMinor,
                bank = Bank.SBI,
                accountSuffix = accountSuffix,
                transactionDate = parsedDate,
                transactionDateTime = null,
                recipient = recipient,
                sender = null,
                referenceNumber = reference,
                confidence = ParseConfidence.HIGH
            )
        }
        
        return null
    }
}
