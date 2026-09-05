package com.budgetpace.app.domain.parser

import com.budgetpace.app.core.model.Bank
import com.budgetpace.app.core.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /** The SMS channel is primary now; the same message must parse identically from it. */
    @Test
    fun testSbiDebitOverSmsChannel() {
        val body = "Dear UPI user A/C X5326 debited by 272.00 on date 26Jul26 trf to " +
            "Zepto Marketplace Refno 211674921516 If not u? call-1800111109 -SBI"
        val sms = NotificationInput("sms:AX-SBIUPI-S", "AX-SBIUPI-S", body, now)
        val listener = NotificationInput("com.google.android.apps.messaging", "SBI", body, now)

        assertTrue(parser.canParse(sms))
        assertEquals(parser.parse(listener), parser.parse(sms))
    }

    @Test
    fun testCanParseRejectsUnsupportedSource() {
        val input = NotificationInput(
            "com.whatsapp",
            "AX-SBIUPI-S",
            "Dear UPI user A/C X5326 debited by 272.00 on date 26Jul26 trf to Zepto Marketplace Refno 211674921516 -SBI",
            now
        )

        assertFalse(parser.canParse(input))
    }

    /** The pattern matches case-insensitively, so the date formatter has to as well. */
    @Test
    fun testUppercaseMonthName() {
        val input = NotificationInput(
            "sms:AX-SBIUPI-S",
            "AX-SBIUPI-S",
            "DEAR UPI USER A/C X5326 DEBITED BY 272.00 ON DATE 26JUL26 TRF TO ZEPTO REFNO 211674921516 -SBI",
            now
        )

        val result = parser.parse(input)

        requireNotNull(result)
        assertEquals(LocalDate.of(2026, 7, 26), result.transactionDate)
    }

    /** Tolerant tokens: "AC" without the slash, "Rs." before the amount, "Ref No:" spelled out. */
    @Test
    fun testTolerantTokens() {
        val input = NotificationInput(
            "sms:AX-SBIUPI-S",
            "AX-SBIUPI-S",
            "Dear UPI user AC X5326 debited by Rs.272.00 on date 26Jul26 trf to Zepto Marketplace Ref No: 211674921516 -SBI",
            now
        )

        val result = parser.parse(input)

        requireNotNull(result)
        assertEquals(27200L, result.amountMinor)
        assertEquals("X5326", result.accountSuffix)
        assertEquals("Zepto Marketplace", result.recipient)
        assertEquals("211674921516", result.referenceNumber)
    }

    /** A malformed amount used to yield a HIGH-confidence zero-rupee prompt. */
    @Test
    fun testZeroAmountIsRejected() {
        val input = NotificationInput(
            "sms:AX-SBIUPI-S",
            "AX-SBIUPI-S",
            "Dear UPI user A/C X5326 debited by 0.00 on date 26Jul26 trf to Zepto Marketplace Refno 211674921516 -SBI",
            now
        )

        assertNull(parser.parse(input))
    }
}
