package com.budgetpace.app.domain.budget

import com.budgetpace.app.core.model.*
import com.budgetpace.app.core.time.MonthPeriod
import com.budgetpace.app.core.time.PeriodCalculator
import java.time.LocalDate

object BudgetEngine {

    fun calculateMonthSummary(
        month: BudgetMonth,
        categories: List<Category>,
        transactions: List<Transaction>,
        carryForwards: List<BudgetCarryForward>,
        today: LocalDate = LocalDate.now()
    ): MonthSummary {
        val periods = PeriodCalculator.periodsFor(month.year, month.month)
        
        val categorySummaries = categories.map { category ->
            calculateCategorySummary(category, periods, transactions.filter { it.categoryId == category.id }, carryForwards.filter { it.categoryId == category.id }, today)
        }
        
        val totalBudgetMinor = categories.sumOf { it.monthlyBudgetMinor }
        val totalSpentMinor = transactions.filter { it.direction == TransactionDirection.DEBIT }.sumOf { it.amountMinor }
        
        val overallPeriods = periods.map { period ->
            val periodSpent = transactions
                .filter { it.direction == TransactionDirection.DEBIT && PeriodCalculator.periodIndexFor(it.transactionDate) == period.periodIndex }
                .sumOf { it.amountMinor }
            
            // Overall period budget is sum of all category period budgets
            val periodBudget = categorySummaries.sumOf { it.periods[period.periodIndex].effectiveBudgetMinor }
            
            val (paceRatio, paceStatus) = PaceCalculator.calculatePace(
                spentMinor = periodSpent,
                effectiveBudgetMinor = periodBudget,
                startDate = period.startDate,
                endDate = period.endDate,
                today = today
            )
            
            val periodStatus = when {
                today.isBefore(period.startDate) -> PeriodStatus.UPCOMING
                today.isAfter(period.endDate) -> PeriodStatus.COMPLETED
                else -> PeriodStatus.CURRENT
            }
            
            PeriodSummary(
                periodIndex = period.periodIndex,
                startDate = period.startDate,
                endDate = period.endDate,
                baseBudgetMinor = periodBudget,
                carryForwardMinor = 0L, // Handled per category
                spentMinor = periodSpent,
                periodStatus = periodStatus,
                paceStatus = paceStatus,
                paceRatio = paceRatio,
                isCurrentPeriod = periodStatus == PeriodStatus.CURRENT
            )
        }
        
        // Safe to spend calculation
        val currentPeriodIndex = PeriodCalculator.periodIndexFor(today)
        val safeToSpendMinor = if (currentPeriodIndex in 0..3) {
            val remainingMonthly = (totalBudgetMinor - totalSpentMinor).coerceAtLeast(0)
            
            // Simplified safe to spend: remaining for current period across all categories
            val currentPeriodBudget = overallPeriods[currentPeriodIndex].effectiveBudgetMinor
            val currentPeriodSpent = overallPeriods[currentPeriodIndex].spentMinor
            
            (currentPeriodBudget - currentPeriodSpent).coerceAtLeast(0).coerceAtMost(remainingMonthly)
        } else {
            0L
        }
        
        val overallStatus = PaceCalculator.calculatePace(
            spentMinor = totalSpentMinor,
            effectiveBudgetMinor = totalBudgetMinor,
            startDate = periods.first().startDate,
            endDate = periods.last().endDate,
            today = today
        ).second

        return MonthSummary(
            month = month,
            totalBudgetMinor = totalBudgetMinor,
            totalSpentMinor = totalSpentMinor,
            safeToSpendMinor = safeToSpendMinor,
            overallPeriods = overallPeriods,
            categories = categorySummaries.sortedBy { it.category.sortOrder },
            overallStatus = overallStatus
        )
    }

    private fun calculateCategorySummary(
        category: Category,
        periods: List<MonthPeriod>,
        transactions: List<Transaction>,
        carryForwards: List<BudgetCarryForward>,
        today: LocalDate
    ): CategorySummary {
        val totalSpentMinor = transactions.filter { it.direction == TransactionDirection.DEBIT }.sumOf { it.amountMinor }

        // Per spec §24/§31: a category with weeklyPacingEnabled = false (e.g. Rent) still
        // participates in the overall monthly budget/spending/pace with its fair per-period
        // share — only its OWN four-tile breakdown is hidden in the UI (category.weeklyPacingEnabled
        // is the flag the UI reads for that). Dumping the whole budget into period 0 here would
        // wrongly inflate period 0 and starve periods 1-3 of the overall four-period pace.
        val periodBudgets = PeriodCalculator.splitBudget(category.monthlyBudgetMinor, periods)
        
        val periodSummaries = periods.map { period ->
            val periodSpent = transactions
                .filter { it.direction == TransactionDirection.DEBIT && PeriodCalculator.periodIndexFor(it.transactionDate) == period.periodIndex }
                .sumOf { it.amountMinor }
                
            val baseBudget = periodBudgets[period.periodIndex]
            
            // Calculate effective carry forward (in minus out)
            val cfIn = carryForwards.filter { it.targetPeriod == period.periodIndex }.sumOf { it.amountMinor }
            val cfOut = carryForwards.filter { it.sourcePeriod == period.periodIndex }.sumOf { it.amountMinor }
            val carryForwardMinor = cfIn - cfOut
            
            val effectiveBudget = baseBudget + carryForwardMinor
            
            val (paceRatio, paceStatus) = PaceCalculator.calculatePace(
                spentMinor = periodSpent,
                effectiveBudgetMinor = effectiveBudget,
                startDate = period.startDate,
                endDate = period.endDate,
                today = today
            )
            
            val periodStatus = when {
                today.isBefore(period.startDate) -> PeriodStatus.UPCOMING
                today.isAfter(period.endDate) -> PeriodStatus.COMPLETED
                else -> PeriodStatus.CURRENT
            }
            
            PeriodSummary(
                periodIndex = period.periodIndex,
                startDate = period.startDate,
                endDate = period.endDate,
                baseBudgetMinor = baseBudget,
                carryForwardMinor = carryForwardMinor,
                spentMinor = periodSpent,
                periodStatus = periodStatus,
                paceStatus = paceStatus,
                paceRatio = paceRatio,
                isCurrentPeriod = periodStatus == PeriodStatus.CURRENT
            )
        }
        
        val overallStatus = PaceCalculator.calculatePace(
            spentMinor = totalSpentMinor,
            effectiveBudgetMinor = category.monthlyBudgetMinor,
            startDate = periods.first().startDate,
            endDate = periods.last().endDate,
            today = today
        ).second
        
        return CategorySummary(
            category = category,
            periods = periodSummaries,
            totalSpentMinor = totalSpentMinor,
            overallStatus = overallStatus
        )
    }
}
