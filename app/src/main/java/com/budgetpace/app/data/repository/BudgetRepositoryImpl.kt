package com.budgetpace.app.data.repository

import com.budgetpace.app.core.model.*
import com.budgetpace.app.data.local.dao.BudgetMonthDao
import com.budgetpace.app.data.local.dao.CarryForwardDao
import com.budgetpace.app.data.local.dao.CategoryDao
import com.budgetpace.app.data.local.dao.TransactionDao
import com.budgetpace.app.data.local.mapper.toDomain
import com.budgetpace.app.domain.budget.BudgetEngine
import com.budgetpace.app.domain.repository.BudgetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate

class BudgetRepositoryImpl(
    private val monthDao: BudgetMonthDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val carryForwardDao: CarryForwardDao,
    /** Emits today, and again after each local midnight, so the summary rolls over on its own. */
    private val dateTicker: Flow<LocalDate>,
) : BudgetRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeActiveMonthSummary(): Flow<MonthSummary?> {
        return monthDao.observeActiveMonth()
            .distinctUntilChanged()
            .flatMapLatest { monthEntity ->
                if (monthEntity == null) flowOf(null) else observeMonthSummary(monthEntity.id)
            }
    }

    override fun observeMonthSummary(monthId: String): Flow<MonthSummary?> {
        return combine(
            monthDao.observeById(monthId),
            categoryDao.observeByMonth(monthId),
            transactionDao.observeByMonth(monthId),
            carryForwardDao.observeByMonth(monthId),
            dateTicker,
        ) { monthEntity, categoryEntities, transactionEntities, carryForwardEntities, today ->
            if (monthEntity == null) return@combine null

            BudgetEngine.calculateMonthSummary(
                month = monthEntity.toDomain(),
                categories = categoryEntities.map { it.toDomain() },
                transactions = transactionEntities.map { it.toDomain() },
                carryForwards = carryForwardEntities.map { it.toDomain() },
                today = today,
            )
        }
            // A sync marking rows one by one invalidates these tables repeatedly; recomputing the
            // whole month on the main thread each time is what dropped frames on Home.
            .conflate()
            .flowOn(Dispatchers.Default)
    }
}
