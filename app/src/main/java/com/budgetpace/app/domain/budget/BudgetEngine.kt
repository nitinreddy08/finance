package com.budgetpace.app.domain.budget

import com.budgetpace.app.core.model.*
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
        // The overall/month-level pace bar always uses a fixed period count, independent of any
        // one category's own choice — it's a "which week of the month" concept, not tied to how
        // finely any particular category happens to divide its own budget.
        val periods = PeriodCalculator.periodsFor(month.year, month.month)

        val categorySummaries = categories.map { category ->
            calculateCategorySummary(category, month, transactions.filter { it.categoryId == category.id }, carryForwards.filter { it.categoryId == category.id }, today)
        }

        val totalBudgetMinor = categories.sumOf { it.monthlyBudgetMinor }
        val totalSpentMinor = transactions.filter { it.direction == TransactionDirection.DEBIT }.sumOf { it.amountMinor }

        // Each category's own periods can use a different period count than the global grid (a
        // 1-period "start of month" category has ONE period spanning the whole month; a 2-period
        // category's boundaries don't line up with a 4-period category's). To fold all of that
        // into the fixed global grid without smearing a lump-sum category's budget across every
        // week (the earlier "Rent inflates later weeks' safe-to-spend" bug), each category period
        // attributes its ENTIRE budget to whichever single global period contains its start date,
        // rather than trying to prorate a fractional overlap across multiple global periods.
        val globalBudgetByIndex = LongArray(periods.size)
        for (cs in categorySummaries) {
            for (categoryPeriod in cs.periods) {
                val globalIndex = PeriodCalculator.periodIndexFor(categoryPeriod.startDate)
                    .coerceIn(0, periods.size - 1)
                globalBudgetByIndex[globalIndex] += categoryPeriod.effectiveBudgetMinor
            }
        }

        val overallPeriods = periods.map { period ->
            val periodSpent = transactions
                .filter { it.direction == TransactionDirection.DEBIT && PeriodCalculator.periodIndexFor(it.transactionDate) == period.periodIndex }
                .sumOf { it.amountMinor }

            val periodBudget = globalBudgetByIndex[period.periodIndex]

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
        val safeToSpendMinor = if (currentPeriodIndex in overallPeriods.indices) {
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
        month: BudgetMonth,
        transactions: List<Transaction>,
        carryForwards: List<BudgetCarryForward>,
        today: LocalDate
    ): CategorySummary {
        // Each category divides the month into its OWN chosen number of periods — a 2-period
        // category's boundaries are not the same dates as a 4-period category's, so its periods
        // are computed independently rather than reusing the month's global (fixed) period grid.
        val periods = PeriodCalculator.periodsFor(month.year, month.month, category.periodCount)

        val totalSpentMinor = transactions.filter { it.direction == TransactionDirection.DEBIT }.sumOf { it.amountMinor }

        // periodCount = 1 ("spend at start of month", e.g. Rent) puts its ENTIRE budget in the
        // one period, not a proportional slice of it — choosing that pacing means the user
        // intends to spend it all upfront, not have it metered across the month.
        val periodBudgets = if (category.periodCount <= 1) {
            longArrayOf(category.monthlyBudgetMinor)
        } else {
            PeriodCalculator.splitBudget(category.monthlyBudgetMinor, category.periodCount)
        }

        val periodSummaries = periods.map { period ->
            val periodSpent = transactions
                .filter { it.direction == TransactionDirection.DEBIT && PeriodCalculator.periodIndexFor(it.transactionDate, category.periodCount) == period.periodIndex }
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
