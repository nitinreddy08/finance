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
}
