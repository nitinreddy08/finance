package com.budgetpace.app.data.repository

import com.budgetpace.app.core.model.*
import com.budgetpace.app.data.local.dao.TransactionDao
import com.budgetpace.app.data.local.mapper.toDomain
import com.budgetpace.app.data.local.mapper.toEntity
import com.budgetpace.app.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepositoryImpl(
    private val transactionDao: TransactionDao
) : TransactionRepository {

    override fun observeByMonth(monthId: String): Flow<List<Transaction>> {
        return transactionDao.observeByMonth(monthId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun add(transaction: Transaction) {
        transactionDao.insert(transaction.toEntity())
    }

    override suspend fun update(transaction: Transaction) {
        transactionDao.update(transaction.toEntity())
    }

    override suspend fun delete(id: String) {
        transactionDao.deleteById(id)
    }
}
