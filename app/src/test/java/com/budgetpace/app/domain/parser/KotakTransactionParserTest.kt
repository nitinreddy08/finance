package com.budgetpace.app.domain.parser

import com.budgetpace.app.core.model.Bank
import com.budgetpace.app.core.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class KotakTransactionParserTest {

    private val parser = KotakTransactionParser()
    private val now = Instant.now()

    @Test
    fun testKotakDebit() {
        val input = NotificationInput(
            "com.google.android.apps.messaging",
            "Kotak",
            "Sent Rs.27.00 from Kotak Bank AC X7970 to paytm.s2ebzrr@pty on 06-08-26.UPI Ref 621859049153. Not you, https://kotak.com/KBANKT/Fraud",
            now
        )
        
        val result = parser.parse(input)
        
        requireNotNull(result)
        assertEquals(TransactionDirection.DEBIT, result.direction)
        assertEquals(2700L, result.amountMinor)
        assertEquals(Bank.KOTAK, result.bank)
        assertEquals("X7970", result.accountSuffix)
        assertEquals("paytm.s2ebzrr@pty", result.recipient)
        assertEquals(LocalDate.of(2026, 8, 6), result.transactionDate)
        assertEquals("621859049153", result.referenceNumber)
    }

    @Test
    fun testKotakCredit() {
        val input = NotificationInput(
            "com.google.android.apps.messaging",
            "Kotak",
            "Received Rs.6000.00 in your Kotak Bank AC X7970 from nitinreddy@ptyes on 06-08-26.UPI Ref:212542994030.",
            now
        )
        
        val result = parser.parse(input)
        
        requireNotNull(result)
        assertEquals(TransactionDirection.CREDIT, result.direction)
        assertEquals(600000L, result.amountMinor)
        assertEquals(Bank.KOTAK, result.bank)
        assertEquals("X7970", result.accountSuffix)
        assertEquals("nitinreddy@ptyes", result.sender)
        assertEquals(LocalDate.of(2026, 8, 6), result.transactionDate)
        assertEquals("212542994030", result.referenceNumber)
    }

    @Test
    fun testUnrelatedMessage() {
        val input = NotificationInput(
            "com.google.android.apps.messaging",
            "Kotak",
            "Your Kotak account statement is ready.",
            now
        )
        
        val result = parser.parse(input)
        assertNull(result)
    }
}
