package com.budgetpace.app.domain.sync

import com.budgetpace.app.core.model.Bank
import com.budgetpace.app.core.model.RecordDecision
import com.budgetpace.app.core.model.SyncState
import com.budgetpace.app.core.model.Transaction
import com.budgetpace.app.core.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class SyncPlannerTest {

    private val monthId: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000ff")
    private val now: Instant = Instant.parse("2026-08-06T10:15:30Z")

    private fun txn(
        id: UUID,
        decision: RecordDecision = RecordDecision.RECORDED,
        amountMinor: Long = 2700L,
    ): Transaction = Transaction(
        id = id,
        monthId = monthId,
        amountMinor = amountMinor,
        currency = "INR",
        direction = TransactionDirection.DEBIT,
        categoryId = null,
        transactionDateTime = null,
        transactionDate = LocalDate.of(2026, 8, 6),
        notificationReceivedAt = now,
        bank = Bank.KOTAK,
        accountSuffix = "X7970",
        recipient = "Zepto Marketplace",
        sender = null,
        referenceNumber = "621859049153",
        sourcePackage = "sms:AX-KOTAKB-S",
        sourceSender = "AX-KOTAKB-S",
        sourceMessageHash = null,
        duplicateKey = null,
        recordDecision = decision,
        syncState = SyncState.PENDING,
        parserVersion = "kotak-1",
        createdAt = now,
        updatedAt = now,
    )

    private fun id(last: Int): UUID =
        UUID.fromString("00000000-0000-4000-8000-%012d".format(last))

    // ─── Recorded transactions ────────────────────────────────────────────────

    @Test
    fun testRecordedWithExistingRowIsUpdatedInPlace() {
        val a = txn(id(1))
        val plan = SyncPlanner.plan(
            existingRowByTxnId = mapOf(a.id.toString() to 7),
            pending = listOf(a),
            tombstoneIds = emptyList(),
        )

        assertEquals(listOf(7 to a), plan.updates)
        assertEquals(emptyList<Transaction>(), plan.appends)
        assertEquals(emptyList<Int>(), plan.clearRows)
        assertEquals(listOf(a), plan.markSynced)
        assertEquals(emptyList<String>(), plan.tombstonesDone)
    }

    @Test
    fun testRecordedWithoutRowIsAppended() {
        val a = txn(id(1))
        val plan = SyncPlanner.plan(emptyMap(), listOf(a), emptyList())

        assertEquals(emptyList<Pair<Int, Transaction>>(), plan.updates)
        assertEquals(listOf(a), plan.appends)
        assertEquals(emptyList<Int>(), plan.clearRows)
        assertEquals(listOf(a), plan.markSynced)
    }

    // ─── Ignored transactions never live in the sheet ─────────────────────────

    @Test
    fun testIgnoredWithExistingRowIsClearedAndNeverUpdatedOrAppended() {
        val a = txn(id(2), decision = RecordDecision.IGNORED)
        val plan = SyncPlanner.plan(mapOf(a.id.toString() to 4), listOf(a), emptyList())

        assertEquals(emptyList<Pair<Int, Transaction>>(), plan.updates)
        assertEquals(emptyList<Transaction>(), plan.appends)
        assertEquals(listOf(4), plan.clearRows)
        assertEquals(listOf(a), plan.markSynced)
    }

    @Test
    fun testIgnoredWithoutRowIsStillMarkedSynced() {
        val a = txn(id(2), decision = RecordDecision.IGNORED)
        val plan = SyncPlanner.plan(emptyMap(), listOf(a), emptyList())

        assertEquals(emptyList<Pair<Int, Transaction>>(), plan.updates)
        assertEquals(emptyList<Transaction>(), plan.appends)
        assertEquals(emptyList<Int>(), plan.clearRows)
        // Left PENDING it would be re-examined by every sync pass, forever.
        assertEquals(listOf(a), plan.markSynced)
    }

    // ─── Tombstones ───────────────────────────────────────────────────────────

    @Test
    fun testTombstoneWithRowClearsIt() {
        val gone = id(3).toString()
        val plan = SyncPlanner.plan(mapOf(gone to 12), emptyList(), listOf(gone))

        assertEquals(listOf(12), plan.clearRows)
        assertEquals(listOf(gone), plan.tombstonesDone)
    }

    @Test
    fun testTombstoneNeverSyncedIsDoneWithNothingToClear() {
        val gone = id(4).toString()
        val plan = SyncPlanner.plan(emptyMap(), emptyList(), listOf(gone))

        assertEquals(emptyList<Int>(), plan.clearRows)
        assertEquals(listOf(gone), plan.tombstonesDone)
    }

    // ─── Clear rows are batch-ready ───────────────────────────────────────────

    @Test
    fun testClearRowsAreDistinctAndSortedAscending() {
        val ignored = txn(id(5), decision = RecordDecision.IGNORED)
        val goneA = id(6).toString()
        val goneB = id(7).toString()
        val rows = mapOf(
            ignored.id.toString() to 9,
            goneA to 3,
            goneB to 5,
        )
        // goneA repeated: a duplicate tombstone must not send the same clear twice.
        val plan = SyncPlanner.plan(rows, listOf(ignored), listOf(goneA, goneB, goneA))

        assertEquals(listOf(3, 5, 9), plan.clearRows)
        assertEquals(listOf(goneA, goneB, goneA), plan.tombstonesDone)
    }

    @Test
    fun testIgnoredRowAlsoTombstonedIsClearedOnce() {
        val ignored = txn(id(8), decision = RecordDecision.IGNORED)
        val plan = SyncPlanner.plan(
            mapOf(ignored.id.toString() to 6),
            listOf(ignored),
            listOf(ignored.id.toString()),
        )

        assertEquals(listOf(6), plan.clearRows)
    }

    // ─── Mixed pass ───────────────────────────────────────────────────────────

    @Test
    fun testMixedPassKeepsInputOrderAndMarksEveryPendingSynced() {
        val update = txn(id(11))
        val append = txn(id(12))
        val ignoredWithRow = txn(id(13), decision = RecordDecision.IGNORED)
        val ignoredNoRow = txn(id(14), decision = RecordDecision.IGNORED)
        val appendTwo = txn(id(15))
        val gone = id(16).toString()

        val plan = SyncPlanner.plan(
            existingRowByTxnId = mapOf(
                update.id.toString() to 2,
                ignoredWithRow.id.toString() to 8,
                gone to 3,
            ),
            pending = listOf(update, append, ignoredWithRow, ignoredNoRow, appendTwo),
            tombstoneIds = listOf(gone),
        )

        assertEquals(listOf(2 to update), plan.updates)
        assertEquals(listOf(append, appendTwo), plan.appends)
        assertEquals(listOf(3, 8), plan.clearRows)
        assertEquals(
            listOf(update, append, ignoredWithRow, ignoredNoRow, appendTwo),
            plan.markSynced,
        )
        assertEquals(listOf(gone), plan.tombstonesDone)
        assertFalse(plan.isEmpty)
    }

    @Test
    fun testNothingToDoIsAnEmptyPlan() {
        val plan = SyncPlanner.plan(mapOf(id(1).toString() to 2), emptyList(), emptyList())

        assertTrue(plan.isEmpty)
        assertEquals(emptyList<Pair<Int, Transaction>>(), plan.updates)
        assertEquals(emptyList<Transaction>(), plan.appends)
        assertEquals(emptyList<Int>(), plan.clearRows)
        assertEquals(emptyList<Transaction>(), plan.markSynced)
        assertEquals(emptyList<String>(), plan.tombstonesDone)
    }
}
