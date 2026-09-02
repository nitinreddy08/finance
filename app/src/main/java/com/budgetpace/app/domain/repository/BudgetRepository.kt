package com.budgetpace.app.data.repository

import com.budgetpace.app.core.model.BudgetMonth
import com.budgetpace.app.core.model.MonthSummary
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun observeMonthSummary(monthId: String): Flow<MonthSummary?>
    fun observeActiveMonthSummary(): Flow<MonthSummary?>
}
