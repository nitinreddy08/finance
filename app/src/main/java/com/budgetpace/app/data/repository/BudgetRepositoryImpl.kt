package com.budgetpace.app.data.repository

import android.util.Log
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi

class BudgetRepositoryImpl(
    private val monthDao: BudgetMonthDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val carryForwardDao: CarryForwardDao
) : BudgetRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeActiveMonthSummary(): Flow<MonthSummary?> {
        return monthDao.observeActiveMonth().flatMapLatest { monthEntity ->
            if (monthEntity == null) flowOf(null)
            else observeMonthSummary(monthEntity.id)
        }
    }
    
    override fun observeMonthSummary(monthId: String): Flow<MonthSummary?> {
        return combine(
            monthDao.observeById(monthId),
            categoryDao.observeByMonth(monthId),
            transactionDao.observeByMonth(monthId),
            carryForwardDao.observeByMonth(monthId)
        ) { monthEntity, categoryEntities, transactionEntities, carryForwardEntities ->
            if (monthEntity == null) return@combine null

            val summary = BudgetEngine.calculateMonthSummary(
                month = monthEntity.toDomain(),
                categories = categoryEntities.map { it.toDomain() },
                transactions = transactionEntities.map { it.toDomain() },
                carryForwards = carryForwardEntities.map { it.toDomain() }
            )

            // Temporary diagnostic: if the Home screen's Spent/Budget/% used figures ever look
            // inconsistent with each other again, this is the one logcat line that shows exactly
            // what BudgetEngine computed and from how many raw rows, instead of guessing from a
            // screenshot.
            Log.d(
                "BudgetRepository",
                "monthId=$monthId totalBudgetMinor=${summary.totalBudgetMinor} " +
                    "totalSpentMinor=${summary.totalSpentMinor} " +
                    "categoryCount=${categoryEntities.size} transactionCount=${transactionEntities.size} " +
                    "categories=" + categoryEntities.joinToString { "${it.name}(id=${it.id.take(8)})=${it.monthlyBudgetMinor}" }
            )

            summary
        }
    }
}
