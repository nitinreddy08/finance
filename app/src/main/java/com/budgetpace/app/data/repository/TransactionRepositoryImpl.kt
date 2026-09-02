package com.budgetpace.app.data.repository

import com.budgetpace.app.core.model.*
import com.budgetpace.app.data.local.dao.DeletedTransactionDao
import com.budgetpace.app.data.local.dao.TransactionDao
import com.budgetpace.app.data.local.entity.DeletedTransactionEntity
import com.budgetpace.app.data.local.mapper.toDomain
import com.budgetpace.app.data.local.mapper.toEntity
import com.budgetpace.app.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepositoryImpl(
    private val transactionDao: TransactionDao,
    private val deletedTransactionDao: DeletedTransactionDao,
) : TransactionRepository {

    override fun observeByMonth(monthId: String): Flow<List<Transaction>> {
        return transactionDao.observeByMonth(monthId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeWithCategoryByMonth(monthId: String): Flow<List<TransactionWithCategory>> {
        return transactionDao.observeWithCategoryByMonth(monthId).map { list ->
            list.map { TransactionWithCategory(it.transaction.toDomain(), it.category?.toDomain()) }
        }
    }
    
    override fun observeWithCategoryById(id: String): Flow<TransactionWithCategory?> {
        return transactionDao.observeWithCategoryById(id).map { entity ->
            entity?.let { TransactionWithCategory(it.transaction.toDomain(), it.category?.toDomain()) }
        }
    }

    override suspend fun add(transaction: Transaction) {
        transactionDao.insert(transaction.toEntity())
    }

    override suspend fun update(transaction: Transaction) {
        transactionDao.update(transaction.toEntity())
    }

    override suspend fun delete(id: String) {
        // Tombstone first — once the row itself is gone there's nothing left to look up by UUID
        // to find the corresponding row in the Google Sheet on next sync.
        deletedTransactionDao.insert(DeletedTransactionEntity(transactionId = id, deletedAt = java.time.Instant.now().toEpochMilli()))
        transactionDao.deleteById(id)
    }

    override suspend fun getPending(): List<Transaction> =
        transactionDao.getPending().map { it.toDomain() }

    override suspend fun markSynced(id: String) {
        transactionDao.markSynced(id, java.time.Instant.now().toEpochMilli())
    }
}
