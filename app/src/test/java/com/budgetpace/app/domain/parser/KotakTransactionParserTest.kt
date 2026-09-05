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

    /**
     * Regression: a real live message used "A/c" instead of "AC", and had a space between the
     * date's trailing period and "UPI" — the original pattern required an exact "AC" substring
     * and disallowed any space before "UPI", so this entire message silently failed to parse and
     * the transaction was dropped with no notification at all.
     */
    @Test
    fun testKotakDebitWithSlashAcAndSpaceBeforeUpi() {
        val input = NotificationInput(
            "com.google.android.apps.messaging",
            "Kotak",
            "Sent Rs.31.00 from Kotak Bank A/c X7970 to Mangi lal on 03-09-26. UPI Ref 624608313331. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud",
            now
        )

        val result = parser.parse(input)

        requireNotNull(result)
        assertEquals(TransactionDirection.DEBIT, result.direction)
        assertEquals(3100L, result.amountMinor)
        assertEquals(Bank.KOTAK, result.bank)
        assertEquals("X7970", result.accountSuffix)
        assertEquals("Mangi lal", result.recipient)
        assertEquals(LocalDate.of(2026, 9, 3), result.transactionDate)
        assertEquals("624608313331", result.referenceNumber)
    }

    /** The SMS channel is primary now; the same message must parse identically from it. */
    @Test
    fun testKotakDebitOverSmsChannel() {
        val body = "Sent Rs.27.00 from Kotak Bank AC X7970 to paytm.s2ebzrr@pty on 06-08-26." +
            "UPI Ref 621859049153. Not you, https://kotak.com/KBANKT/Fraud"
        val sms = NotificationInput("sms:AX-KOTAKB-S", "AX-KOTAKB-S", body, now)
        val listener = NotificationInput("com.google.android.apps.messaging", "Kotak", body, now)

        assertTrue(parser.canParse(sms))
        assertEquals(parser.parse(listener), parser.parse(sms))
    }

    @Test
    fun testCanParseRejectsUnsupportedSource() {
        val input = NotificationInput(
            "com.whatsapp",
            "AX-KOTAKB-S",
            "Sent Rs.27.00 from Kotak Bank AC X7970 to paytm.s2ebzrr@pty on 06-08-26.UPI Ref 621859049153.",
            now
        )

        assertFalse(parser.canParse(input))
    }

    /** A sender header alone is never enough — the bank must come from the message text. */
    @Test
    fun testCanParseRejectsNonBankTextFromABankHeader() {
        val input = NotificationInput("sms:AX-KOTAKB-S", "AX-KOTAKB-S", "See you at 7", now)

        assertFalse(parser.canParse(input))
    }

    /** A malformed amount used to yield a HIGH-confidence zero-rupee prompt. */
    @Test
    fun testZeroAmountIsRejected() {
        val input = NotificationInput(
            "sms:AX-KOTAKB-S",
            "AX-KOTAKB-S",
            "Sent Rs.0.00 from Kotak Bank AC X7970 to paytm.s2ebzrr@pty on 06-08-26.UPI Ref 621859049153.",
            now
        )

        assertNull(parser.parse(input))
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
