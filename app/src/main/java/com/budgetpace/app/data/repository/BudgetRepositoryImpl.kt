package com.budgetpace.app.data.repository

import com.budgetpace.app.core.model.*
import com.budgetpace.app.data.local.dao.BudgetMonthDao
import com.budgetpace.app.data.local.dao.CarryForwardDao
import com.budgetpace.app.data.local.dao.CategoryDao
import com.budgetpace.app.data.local.dao.TransactionDao
import com.budgetpace.app.data.local.mapper.toDomain
import com.budgetpace.app.domain.budget.BudgetEngine
import com.budgetpace.app.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class BudgetRepositoryImpl(
    private val monthDao: BudgetMonthDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val carryForwardDao: CarryForwardDao
) : BudgetRepository {

    override fun observeActiveMonthSummary(): Flow<MonthSummary?> {
        // Simplified for Phase 1: observe active month and its relations
        return monthDao.observeActiveMonth().combine(categoryDao.observeByMonth("")) { month, cats -> month }
            .combine(transactionDao.observeByMonth("")) { m, t -> m } // Dummy placeholder for complex flow combination
            // In a real implementation, we would use flatMapLatest to observe relations based on the active month's ID
            // For this skeleton, we'll return a basic flow
    }
    
    override fun observeMonthSummary(monthId: String): Flow<MonthSummary?> {
        return combine(
            monthDao.observeById(monthId),
            categoryDao.observeByMonth(monthId),
            transactionDao.observeByMonth(monthId),
            carryForwardDao.observeByMonth(monthId)
        ) { monthEntity, categoryEntities, transactionEntities, carryForwardEntities ->
            if (monthEntity == null) return@combine null
            
            BudgetEngine.calculateMonthSummary(
                month = monthEntity.toDomain(),
                categories = categoryEntities.map { it.toDomain() },
                transactions = transactionEntities.map { it.toDomain() },
                carryForwards = carryForwardEntities.map { it.toDomain() }
            )
        }
    }
}
