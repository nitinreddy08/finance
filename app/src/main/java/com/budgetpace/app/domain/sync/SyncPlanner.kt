package com.budgetpace.app.domain.sync

import com.budgetpace.app.core.model.RecordDecision
import com.budgetpace.app.core.model.Transaction

/**
 * The complete set of writes one sync pass owes the backup sheet, batched by kind so the
 * coordinator can issue one `values().batchUpdate`, one `append` and one `batchClear` instead of a
 * request per row (spec section 51's sheet, Google's 60 writes/minute quota).
 *
 * [markSynced] and [tombstonesDone] are the local bookkeeping that must follow a successful pass;
 * they are deliberately part of the plan so nothing can be marked synced that the plan did not
 * actually account for.
 */
data class SyncPlan(
    /** Sheet row number (1-based, header is row 1) to the transaction that overwrites it. */
    val updates: List<Pair<Int, Transaction>>,
    val appends: List<Transaction>,
    /** Distinct, ascending — clearing the same row twice would waste a request in the batch. */
    val clearRows: List<Int>,
    val markSynced: List<Transaction>,
    val tombstonesDone: List<String>,
) {
    val isEmpty: Boolean
        get() = updates.isEmpty() && appends.isEmpty() && clearRows.isEmpty() &&
            markSynced.isEmpty() && tombstonesDone.isEmpty()
}

/**
 * Decides what a sync pass does with the pending transactions and the tombstones left by local
 * deletes. Pure so the rule that keeps "Don't record" expenses out of the owner's sheet is
 * testable without Google in the loop.
 */
object SyncPlanner {

    /**
     * @param existingRowByTxnId transaction id (UUID as text, column A of the Expenses tab) to the
     *   sheet row it already occupies.
     * @param pending local transactions in [com.budgetpace.app.core.model.SyncState.PENDING].
     * @param tombstoneIds ids of transactions deleted locally, whose rows are now orphaned.
     */
    fun plan(
        existingRowByTxnId: Map<String, Int>,
        pending: List<Transaction>,
        tombstoneIds: List<String>,
    ): SyncPlan {
        val updates = mutableListOf<Pair<Int, Transaction>>()
        val appends = mutableListOf<Transaction>()
        val clearRows = mutableSetOf<Int>()

        for (txn in pending) {
            val existingRow: Int? = existingRowByTxnId[txn.id.toString()]
            when (txn.recordDecision) {
                RecordDecision.RECORDED ->
                    if (existingRow != null) updates += existingRow to txn else appends += txn

                // An ignored expense is not spending, so it is never a row in the sheet: one that
                // was uploaded before the owner tapped "Don't record" is cleared like a tombstone.
                // It is still marked synced below, or every later pass would re-examine it forever.
                RecordDecision.IGNORED ->
                    if (existingRow != null) clearRows += existingRow
            }
        }

        for (id in tombstoneIds) {
            val row: Int? = existingRowByTxnId[id]
            if (row != null) clearRows += row
        }

        return SyncPlan(
            updates = updates,
            appends = appends,
            clearRows = clearRows.sorted(),
            markSynced = pending.toList(),
            // A transaction deleted before it ever synced has no row to clear, and its tombstone is
            // just as done as one that cleared a row — otherwise it would never be dropped.
            tombstonesDone = tombstoneIds.toList(),
        )
    }
}
