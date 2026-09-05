package com.budgetpace.app.domain.repository

import com.budgetpace.app.core.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun observeByMonth(monthId: String): Flow<List<Transaction>>
    fun observeWithCategoryByMonth(monthId: String): Flow<List<com.budgetpace.app.core.model.TransactionWithCategory>>
    fun observeWithCategoryById(id: String): Flow<com.budgetpace.app.core.model.TransactionWithCategory?>
    suspend fun add(transaction: Transaction)
    suspend fun update(transaction: Transaction)
    suspend fun delete(id: String)

    /** Spec §52: transactions across all months still waiting to reach the user's Google Sheet. */
    suspend fun getPending(): List<Transaction>
    suspend fun markSynced(id: String)

    /**
     * Undoes "Don't record" (spec §21), only while the expense is still ignored. Returns whether
     * it applied, so a caller (the detail screen's "Record it") can tell a stale retry from a
     * real change without inspecting the row itself.
     */
    suspend fun recordIfIgnored(id: String): Boolean
}
