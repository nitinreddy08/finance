package com.budgetpace.app.domain.duplicate

import com.budgetpace.app.core.model.Bank
import com.budgetpace.app.core.model.TransactionDirection
import com.budgetpace.app.domain.parser.ParsedTransaction
import java.security.MessageDigest
import java.time.LocalDate

object DuplicateDetector {

    /**
     * Generates a fallback fingerprint for transactions lacking a reference number per §19.
     */
    fun generateFallbackFingerprint(
        bank: Bank,
        accountSuffix: String?,
        amountMinor: Long,
        direction: TransactionDirection,
        transactionDate: LocalDate?,
        party: String? // sender or recipient
    ): String {
        val raw = buildString {
            append(bank.name)
            append("|")
            append(accountSuffix ?: "")
            append("|")
            append(amountMinor)
            append("|")
            append(direction.name)
            append("|")
            append(transactionDate?.toString() ?: "")
            append("|")
            // Normalize party name: lowercase, strip punctuation and extra spaces
            append(party?.lowercase()?.replace(Regex("[^a-z0-9]"), "") ?: "")
        }
        
        return hashString(raw)
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    fun getDuplicateKey(parsedTxn: ParsedTransaction): String {
        if (parsedTxn.referenceNumber != null) {
            return "${parsedTxn.bank.name}:${parsedTxn.referenceNumber}"
        }
        
        val party = if (parsedTxn.direction == TransactionDirection.DEBIT) 
            parsedTxn.recipient else parsedTxn.sender
            
        return generateFallbackFingerprint(
            parsedTxn.bank, 
            parsedTxn.accountSuffix, 
            parsedTxn.amountMinor, 
            parsedTxn.direction, 
            parsedTxn.transactionDate, 
            party
        )
    }
}
