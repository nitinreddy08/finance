package com.budgetpace.app.domain.duplicate

import com.budgetpace.app.core.model.Bank
import com.budgetpace.app.core.model.ParseConfidence
import com.budgetpace.app.core.model.TransactionDirection
import com.budgetpace.app.domain.parser.ParsedTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

class DuplicateDetectorTest {

    private fun txn(
        amountMinor: Long = 27_00L,
        recipient: String? = "paytm.s2ebzrr@pty",
        ref: String? = "621859049153",
        date: LocalDate? = LocalDate.of(2026, 8, 6),
        accountSuffix: String? = "X7970",
    ) = ParsedTransaction(
        direction = TransactionDirection.DEBIT,
        amountMinor = amountMinor,
        bank = Bank.KOTAK,
        accountSuffix = accountSuffix,
        transactionDate = date,
        transactionDateTime = null,
        recipient = recipient,
        sender = null,
        referenceNumber = ref,
        confidence = ParseConfidence.HIGH,
    )

    @Test
    fun sameReferenceProducesSameKey() {
        val a = DuplicateDetector.getDuplicateKey(txn())
        val b = DuplicateDetector.getDuplicateKey(txn())
        assertEquals(a, b)
        assertEquals("KOTAK:621859049153", a)
    }

    @Test
    fun missingReferenceFallsBackToStableFingerprint() {
        val a = DuplicateDetector.getDuplicateKey(txn(ref = null))
        val b = DuplicateDetector.getDuplicateKey(txn(ref = null))
        assertEquals(a, b)
    }

    @Test
    fun fallbackFingerprintDiffersOnAmount() {
        val a = DuplicateDetector.getDuplicateKey(txn(ref = null, amountMinor = 27_00L))
        val b = DuplicateDetector.getDuplicateKey(txn(ref = null, amountMinor = 28_00L))
        assertNotEquals(a, b)
    }

    @Test
    fun fallbackFingerprintDiffersOnDate() {
        val a = DuplicateDetector.getDuplicateKey(txn(ref = null, date = LocalDate.of(2026, 8, 6)))
        val b = DuplicateDetector.getDuplicateKey(txn(ref = null, date = LocalDate.of(2026, 8, 7)))
        assertNotEquals(a, b)
    }

    @Test
    fun fallbackFingerprintNormalizesRecipientPunctuationAndCase() {
        val a = DuplicateDetector.getDuplicateKey(txn(ref = null, recipient = "Paytm.S2ebzrr@pty"))
        val b = DuplicateDetector.getDuplicateKey(txn(ref = null, recipient = "paytm s2ebzrr pty"))
        assertEquals(a, b)
    }
}
