package com.budgetpace.app.domain.repository

import com.budgetpace.app.core.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun observeByMonth(monthId: String): Flow<List<Transaction>>
    suspend fun add(transaction: Transaction)
    suspend fun update(transaction: Transaction)
    suspend fun delete(id: String)
}
