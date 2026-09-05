package com.budgetpace.app.domain.ingestion

import com.budgetpace.app.core.model.Bank
import com.budgetpace.app.core.model.ParseConfidence
import com.budgetpace.app.core.model.TransactionDirection
import com.budgetpace.app.domain.parser.ParsedTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class IngestionPolicyTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private fun parsed(
        amountMinor: Long = 2700L,
        confidence: ParseConfidence = ParseConfidence.HIGH,
        direction: TransactionDirection = TransactionDirection.DEBIT,
    ): ParsedTransaction = ParsedTransaction(
        direction = direction,
        amountMinor = amountMinor,
        bank = Bank.KOTAK,
        accountSuffix = "X7970",
        transactionDate = LocalDate.of(2026, 8, 6),
        transactionDateTime = null,
        recipient = "Zepto Marketplace",
        sender = null,
        referenceNumber = "621859049153",
        confidence = confidence,
    )

    // ─── isRedacted ───────────────────────────────────────────────────────────

    @Test
    fun testRedactionMarkerDetectedCaseInsensitively() {
        assertTrue(IngestionPolicy.isRedacted("Sensitive notification content hidden"))
        assertTrue(IngestionPolicy.isRedacted("sensitive NOTIFICATION content HIDDEN"))
        assertTrue(IngestionPolicy.isRedacted("Kotak: Sensitive notification content hidden"))
        assertFalse(IngestionPolicy.isRedacted("Sent Rs.27.00 from Kotak Bank AC X7970"))
        assertFalse(IngestionPolicy.isRedacted(null))
    }

    // ─── preInsert ────────────────────────────────────────────────────────────

    @Test
    fun testPreInsertStopsOnMissingText() {
        assertEquals(
            PreInsertDecision.Stop(IngestionOutcome.NO_TEXT),
            IngestionPolicy.preInsert(null) { parsed() },
        )
        assertEquals(
            PreInsertDecision.Stop(IngestionOutcome.NO_TEXT),
            IngestionPolicy.preInsert("   ") { parsed() },
        )
    }

    @Test
    fun testPreInsertNeverParsesRedactedText() {
        var parseCalled = false

        val decision = IngestionPolicy.preInsert("Sensitive notification content hidden") {
            parseCalled = true
            parsed()
        }

        assertEquals(PreInsertDecision.Stop(IngestionOutcome.REDACTED), decision)
        assertFalse(parseCalled)
    }

    @Test
    fun testPreInsertStopsWhenNothingParses() {
        assertEquals(
            PreInsertDecision.Stop(IngestionOutcome.NO_MATCH),
            IngestionPolicy.preInsert("See you at 7") { null },
        )
    }

    @Test
    fun testPreInsertStopsOnLowConfidence() {
        assertEquals(
            PreInsertDecision.Stop(IngestionOutcome.LOW_CONFIDENCE),
            IngestionPolicy.preInsert("bank text") { parsed(confidence = ParseConfidence.MEDIUM) },
        )
        assertEquals(
            PreInsertDecision.Stop(IngestionOutcome.LOW_CONFIDENCE),
            IngestionPolicy.preInsert("bank text") { parsed(confidence = ParseConfidence.LOW) },
        )
    }

    @Test
    fun testPreInsertRejectsNonPositiveAmount() {
        assertEquals(
            PreInsertDecision.Stop(IngestionOutcome.NO_MATCH),
            IngestionPolicy.preInsert("bank text") { parsed(amountMinor = 0L) },
        )
        assertEquals(
            PreInsertDecision.Stop(IngestionOutcome.NO_MATCH),
            IngestionPolicy.preInsert("bank text") { parsed(amountMinor = -100L) },
        )
    }

    @Test
    fun testPreInsertProceedsForAHighConfidenceDebit() {
        val transaction = parsed()

        val decision = IngestionPolicy.preInsert("bank text") { transaction }

        assertEquals(PreInsertDecision.Proceed(transaction), decision)
    }

    // ─── postInsert ───────────────────────────────────────────────────────────

    @Test
    fun testPostInsertTreatsMinusOneAsDuplicateForBothDirections() {
        assertEquals(
            PostInsertDecision(IngestionOutcome.DUPLICATE, showPrompt = false),
            IngestionPolicy.postInsert(-1L, TransactionDirection.DEBIT),
        )
        assertEquals(
            PostInsertDecision(IngestionOutcome.DUPLICATE, showPrompt = false),
            IngestionPolicy.postInsert(-1L, TransactionDirection.CREDIT),
        )
    }

    @Test
    fun testPostInsertPromptsOnlyForDebits() {
        assertEquals(
            PostInsertDecision(IngestionOutcome.RECORDED, showPrompt = true),
            IngestionPolicy.postInsert(42L, TransactionDirection.DEBIT),
        )
        assertEquals(
            PostInsertDecision(IngestionOutcome.RECORDED, showPrompt = false),
            IngestionPolicy.postInsert(42L, TransactionDirection.CREDIT),
        )
    }

    // ─── resolveTransactionDate ───────────────────────────────────────────────

    /** Spec section 17's own example: received 00:02 on 1 Sep, dated 31 Aug, belongs to 31 Aug. */
    @Test
    fun testLateNightSmsKeepsThePreviousDaysDate() {
        val arrival = LocalDate.of(2026, 9, 1).atTime(0, 2).atZone(zone).toInstant()

        val resolved = IngestionPolicy.resolveTransactionDate(
            parsedDate = LocalDate.of(2026, 8, 31),
            receivedAt = arrival,
            zone = zone,
        )

        assertEquals(LocalDate.of(2026, 8, 31), resolved.date)
        assertFalse(resolved.anomaly)
    }

    @Test
    fun testMissingDateFallsBackToArrivalWithoutAnomaly() {
        val arrival = LocalDate.of(2026, 9, 1).atTime(0, 2).atZone(zone).toInstant()

        val resolved = IngestionPolicy.resolveTransactionDate(null, arrival, zone)

        assertEquals(LocalDate.of(2026, 9, 1), resolved.date)
        assertFalse(resolved.anomaly)
    }

    /** No tolerance window: one day ahead is already enough to create a month that cannot exist. */
    @Test
    fun testFutureDateFallsBackToArrivalAndFlagsAnomaly() {
        val arrival = LocalDate.of(2026, 9, 1).atTime(12, 0).atZone(zone).toInstant()

        val resolved = IngestionPolicy.resolveTransactionDate(
            parsedDate = LocalDate.of(2026, 9, 2),
            receivedAt = arrival,
            zone = zone,
        )

        assertEquals(LocalDate.of(2026, 9, 1), resolved.date)
        assertTrue(resolved.anomaly)
    }

    @Test
    fun testSameDayIsNotAnAnomaly() {
        val arrival = LocalDate.of(2026, 9, 1).atTime(12, 0).atZone(zone).toInstant()

        val resolved = IngestionPolicy.resolveTransactionDate(
            parsedDate = LocalDate.of(2026, 9, 1),
            receivedAt = arrival,
            zone = zone,
        )

        assertEquals(LocalDate.of(2026, 9, 1), resolved.date)
        assertFalse(resolved.anomaly)
    }

    // ─── planMonth ────────────────────────────────────────────────────────────

    @Test
    fun testDateInTodaysMonthUsesTheCurrentMonth() {
        val plan = IngestionPolicy.planMonth(
            txDate = LocalDate.of(2026, 9, 1),
            today = LocalDate.of(2026, 9, 4),
        ) { _, _ -> throw AssertionError("existence must not be consulted for the current month") }

        assertEquals(MonthPlan.CurrentMonth, plan)
    }

    /** A future month row would collide with the rollover INSERT and crash every launch. */
    @Test
    fun testFutureMonthIsNeverCreated() {
        val plan = IngestionPolicy.planMonth(
            txDate = LocalDate.of(2026, 10, 1),
            today = LocalDate.of(2026, 9, 4),
        ) { _, _ -> false }

        assertEquals(MonthPlan.CurrentMonth, plan)
    }

    /** The 31-Aug / 1-Sep example: August already exists, so the archived month is reused. */
    @Test
    fun testExistingPastMonthIsReused() {
        val plan = IngestionPolicy.planMonth(
            txDate = LocalDate.of(2026, 8, 31),
            today = LocalDate.of(2026, 9, 1),
        ) { year, month -> year == 2026 && month == 8 }

        assertEquals(MonthPlan.ExistingMonth(2026, 8), plan)
    }

    @Test
    fun testMissingPastMonthIsCreatedArchived() {
        val plan = IngestionPolicy.planMonth(
            txDate = LocalDate.of(2026, 8, 31),
            today = LocalDate.of(2026, 9, 1),
        ) { _, _ -> false }

        assertEquals(MonthPlan.CreateArchivedMonth(2026, 8), plan)
    }

    /** December to January: the year has to move too, not just the month number. */
    @Test
    fun testDecemberToJanuaryBoundary() {
        val existing = IngestionPolicy.planMonth(
            txDate = LocalDate.of(2026, 12, 31),
            today = LocalDate.of(2027, 1, 1),
        ) { year, month -> year == 2026 && month == 12 }
        assertEquals(MonthPlan.ExistingMonth(2026, 12), existing)

        val missing = IngestionPolicy.planMonth(
            txDate = LocalDate.of(2026, 12, 31),
            today = LocalDate.of(2027, 1, 1),
        ) { _, _ -> false }
        assertEquals(MonthPlan.CreateArchivedMonth(2026, 12), missing)

        // January of the new year is the current month, not a month twelve ahead of December.
        val current = IngestionPolicy.planMonth(
            txDate = LocalDate.of(2027, 1, 1),
            today = LocalDate.of(2027, 1, 1),
        ) { _, _ -> false }
        assertEquals(MonthPlan.CurrentMonth, current)
    }

    // ─── sanitizeSender ───────────────────────────────────────────────────────

    @Test
    fun testDltHeaderIsKept() {
        assertEquals("AX-KOTAKB-S", IngestionPolicy.sanitizeSender("AX-KOTAKB-S"))
        assertEquals("JD-SBIUPI", IngestionPolicy.sanitizeSender("JD-SBIUPI"))
        assertEquals("AX-KOTAKB-S", IngestionPolicy.sanitizeSender("  AX-KOTAKB-S "))
    }

    @Test
    fun testPhoneNumberIsNeverStored() {
        assertEquals("number", IngestionPolicy.sanitizeSender("+919876543210"))
        assertEquals("number", IngestionPolicy.sanitizeSender("9876543210"))
        assertEquals("number", IngestionPolicy.sanitizeSender("Amma"))
    }

    @Test
    fun testBlankSenderIsNull() {
        assertNull(IngestionPolicy.sanitizeSender(""))
        assertNull(IngestionPolicy.sanitizeSender("   "))
        assertNull(IngestionPolicy.sanitizeSender(null))
    }
}
