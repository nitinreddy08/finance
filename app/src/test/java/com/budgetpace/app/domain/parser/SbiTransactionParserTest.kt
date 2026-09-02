package com.budgetpace.app.domain.parser

import com.budgetpace.app.core.model.Bank
import com.budgetpace.app.core.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SbiTransactionParserTest {

    private val parser = SbiTransactionParser()
    private val now = Instant.now()

    @Test
    fun testSbiDebit() {
        val input = NotificationInput(
            "com.google.android.apps.messaging",
            "SBI",
            "Dear UPI user A/C X5326 debited by 272.00 on date 26Jul26 trf to Zepto Marketplace Refno 211674921516 If not u? call-1800111109 for other services-18001234-SBI",
            now
        )
        
        val result = parser.parse(input)
        
        requireNotNull(result)
        assertEquals(TransactionDirection.DEBIT, result.direction)
        assertEquals(27200L, result.amountMinor)
        assertEquals(Bank.SBI, result.bank)
        assertEquals("X5326", result.accountSuffix)
        assertEquals(LocalDate.of(2026, 7, 26), result.transactionDate)
        assertEquals("Zepto Marketplace", result.recipient)
        assertEquals("211674921516", result.referenceNumber)
    }

    @Test
    fun testSbiDebitAnotherFormat() {
        val input = NotificationInput(
            "com.google.android.apps.messaging",
            "SBI",
            "Dear UPI user A/C X5326 debited by 30.00 on date 24Aug26 trf to DHRMPAL Refno 623624303161 If not u? call-1800111109 for other services-18001234-SBI",
            now
        )
        
        val result = parser.parse(input)
        
        requireNotNull(result)
        assertEquals(TransactionDirection.DEBIT, result.direction)
        assertEquals(3000L, result.amountMinor)
        assertEquals(Bank.SBI, result.bank)
        assertEquals("X5326", result.accountSuffix)
        assertEquals(LocalDate.of(2026, 8, 24), result.transactionDate)
        assertEquals("DHRMPAL", result.recipient)
        assertEquals("623624303161", result.referenceNumber)
    }
}
