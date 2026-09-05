package com.budgetpace.app.domain.budget

import com.budgetpace.app.core.model.*
import com.budgetpace.app.core.time.MonthPeriod
import com.budgetpace.app.core.time.PeriodCalculator
import java.time.LocalDate
import java.time.YearMonth

object BudgetEngine {

    /** Where today sits relative to the month being summarised. */
    private enum class MonthPhase { BEFORE, IN, AFTER }

    fun calculateMonthSummary(
        month: BudgetMonth,
        categories: List<Category>,
        transactions: List<Transaction>,
        carryForwards: List<BudgetCarryForward>,
        today: LocalDate,
    ): MonthSummary {
        val yearMonth = YearMonth.of(month.year, month.month)
        val phase = when {
            today.isBefore(yearMonth.atDay(1)) -> MonthPhase.BEFORE
            today.isAfter(yearMonth.atEndOfMonth()) -> MonthPhase.AFTER
            else -> MonthPhase.IN
        }

        val debits = transactions.filter { it.direction == TransactionDirection.DEBIT }
        val categorySummaries = categories.map { category ->
            calculateCategorySummary(
                category = category,
                month = month,
                debits = debits.filter { it.categoryId == category.id },
                carryForwards = carryForwards.filter { it.categoryId == category.id },
                today = today,
                phase = phase,
            )
        }

        val totalBudgetMinor = categories.sumOf { it.monthlyBudgetMinor }
        val totalSpentMinor = debits.sumOf { it.amountMinor }

        // The month-level bar is always four cells regardless of what any category chose.
        val grid = PeriodCalculator.periodsFor(month.year, month.month)
        val overallPeriods = grid.map { period ->
            val status = periodStatus(period.startDate, period.endDate, today)
            OverallPeriod(
                periodIndex = period.periodIndex,
                startDate = period.startDate,
                endDate = period.endDate,
                spentMinor = debits
                    .filter { PeriodCalculator.clampedPeriodIndex(grid, it.transactionDate) == period.periodIndex }
                    .sumOf { it.amountMinor },
                periodStatus = status,
                isCurrentPeriod = status == PeriodStatus.CURRENT,
            )
        }

        val expectedToDateMinor = categorySummaries.sumOf { it.expectedToDateMinor }
        val overallStatus = if (phase == MonthPhase.BEFORE) {
            BudgetStatus.GREY
        } else {
            PaceCalculator.statusFor(totalSpentMinor, expectedToDateMinor)
        }

        val pool = if (phase == MonthPhase.IN) {
            safeToSpendPool(categorySummaries, debits, categories, grid, today)
        } else {
            0L
        }
        val remainingMonthly = (totalBudgetMinor - totalSpentMinor).coerceAtLeast(0L)

        return MonthSummary(
            month = month,
            totalBudgetMinor = totalBudgetMinor,
            totalSpentMinor = totalSpentMinor,
            safeToSpendMinor = pool.coerceIn(0L, remainingMonthly),
            overPaceMinor = (-pool).coerceAtLeast(0L),
            expectedToDateMinor = expectedToDateMinor,
            overallPeriods = overallPeriods,
            categories = categorySummaries.sortedBy { it.category.sortOrder },
            overallStatus = overallStatus,
        )
    }

    /**
     * What is genuinely still available to spend right now.
     *
     * Money left in each paced category's current period, minus any debt that category ran up in
     * its earlier periods, minus anything overspent on a lump sum or spent with no category at all
     * in this same period. A lump sum's *remaining* budget is never included: choosing "spend at
     * start of month" means that money is earmarked (rent), not spare.
     *
     * The earlier-period debt is carried forward and absorbed ONCE, rather than re-subtracted in
     * every remaining period — repaying it repeatedly showed 0 available for the rest of the month
     * when most of the budget was in fact untouched. Unused budget still never flows forward on its
     * own; moving it is what the explicit carry-forward is for.
     *
     * Spending that already happened in an earlier period is not deducted again here either: it has
     * already reduced the month's remaining budget, which caps this figure.
     */
    private fun safeToSpendPool(
        categorySummaries: List<CategorySummary>,
        debits: List<Transaction>,
        categories: List<Category>,
        grid: List<MonthPeriod>,
        today: LocalDate,
    ): Long {
        val pacedHeadroom = categorySummaries.sumOf { summary ->
            if (summary.category.periodCount <= 1) 0L else pacedContribution(summary)
        }

        val currentGridIndex = PeriodCalculator.clampedPeriodIndex(grid, today)
        fun startedBeforeNow(transaction: Transaction): Boolean =
            PeriodCalculator.clampedPeriodIndex(grid, transaction.transactionDate) < currentGridIndex

        // No category owns these, so every paisa of them is overspend.
        val knownCategoryIds = categories.map { it.id }.toSet()
        val uncategorizedNow = debits
            .filter { it.categoryId == null || it.categoryId !in knownCategoryIds }
            .filterNot { startedBeforeNow(it) }
            .sumOf { it.amountMinor }

        // Only how much a lump sum's overspend GREW during this period, so a debt run up in an
        // earlier period is charged once and not again every period after it.
        val lumpSumOverspendNow = categorySummaries
            .filter { it.category.periodCount <= 1 }
            .sumOf { summary ->
                val budget = summary.category.monthlyBudgetMinor
                val spentBefore = debits
                    .filter { it.categoryId == summary.category.id && startedBeforeNow(it) }
                    .sumOf { it.amountMinor }
                val overspendTotal = (summary.totalSpentMinor - budget).coerceAtLeast(0L)
                val overspendBefore = (spentBefore - budget).coerceAtLeast(0L)
                overspendTotal - overspendBefore
            }

        return pacedHeadroom - uncategorizedNow - lumpSumOverspendNow
    }

    /** One paced category's share of the pool: this period's headroom less its own earlier debt. */
    private fun pacedContribution(summary: CategorySummary): Long {
        val currentIndex = summary.currentPeriodIndex ?: return 0L
        var debt = 0L
        for (period in summary.periods.take(currentIndex)) {
            debt = (debt + period.spentMinor - period.effectiveBudgetMinor).coerceAtLeast(0L)
        }
        val current = summary.periods[currentIndex]
        return current.effectiveBudgetMinor - current.spentMinor - debt
    }

    private fun periodStatus(start: LocalDate, end: LocalDate, today: LocalDate): PeriodStatus = when {
        today.isBefore(start) -> PeriodStatus.UPCOMING
        today.isAfter(end) -> PeriodStatus.COMPLETED
        else -> PeriodStatus.CURRENT
    }

    private fun calculateCategorySummary(
        category: Category,
        month: BudgetMonth,
        debits: List<Transaction>,
        carryForwards: List<BudgetCarryForward>,
        today: LocalDate,
        phase: MonthPhase,
    ): CategorySummary {
        // Each category divides the month its own way, so a 2-period category's boundaries are not
        // a 4-period category's and neither matches the month-level grid.
        val periods = PeriodCalculator.periodsFor(month.year, month.month, category.periodCount)

        // periodCount = 1 ("spend at start of month") holds the whole budget in its single period
        // rather than a proportional slice of it.
        val base: LongArray = if (category.periodCount <= 1) {
            longArrayOf(category.monthlyBudgetMinor)
        } else {
            PeriodCalculator.splitBudget(category.monthlyBudgetMinor, category.periodCount)
        }
        val (carriedIn, carriedOut) = applyCarryForwards(carryForwards, base)

        val totalSpentMinor = debits.sumOf { it.amountMinor }

        val periodSummaries = periods.map { period ->
            val index = period.periodIndex
            val spent = debits
                .filter { PeriodCalculator.clampedPeriodIndex(periods, it.transactionDate) == index }
                .sumOf { it.amountMinor }
            val effective = base[index] + carriedIn[index] - carriedOut[index]
            val (ratio, paceStatus) = PaceCalculator.calculatePace(
                spentMinor = spent,
                effectiveBudgetMinor = effective,
                startDate = period.startDate,
                endDate = period.endDate,
                today = today,
            )
            val status = periodStatus(period.startDate, period.endDate, today)
            PeriodSummary(
                periodIndex = index,
                startDate = period.startDate,
                endDate = period.endDate,
                baseBudgetMinor = base[index],
                carryForwardMinor = carriedIn[index] - carriedOut[index],
                spentMinor = spent,
                periodStatus = status,
                paceStatus = paceStatus,
                paceRatio = ratio,
                isCurrentPeriod = status == PeriodStatus.CURRENT,
            )
        }

        // A lump sum is expected in full from day 1 — the user chose to spend it upfront, so
        // metering it across the month would report paying rent on the 1st as being far over pace.
        val expectedToDateMinor = when {
            phase == MonthPhase.BEFORE -> 0L
            category.periodCount <= 1 -> category.monthlyBudgetMinor
            else -> periodSummaries.sumOf { period ->
                PaceCalculator.expectedToDateMinor(
                    effectiveBudgetMinor = period.effectiveBudgetMinor,
                    startDate = period.startDate,
                    endDate = period.endDate,
                    today = today,
                )
            }
        }

        val overallStatus = when {
            phase == MonthPhase.BEFORE -> BudgetStatus.GREY
            category.periodCount <= 1 ->
                PaceCalculator.statusFor(totalSpentMinor, category.monthlyBudgetMinor)
            else -> PaceCalculator.statusFor(totalSpentMinor, expectedToDateMinor)
        }

        return CategorySummary(
            category = category,
            periods = periodSummaries,
            totalSpentMinor = totalSpentMinor,
            overallStatus = overallStatus,
            expectedToDateMinor = expectedToDateMinor,
            currentPeriodIndex = periodSummaries.firstOrNull { it.isCurrentPeriod }?.periodIndex,
        )
    }

    /**
     * Applies carry-forward records in creation order, returning the amounts moved into and out of
     * each period.
     *
     * A record that no longer makes sense — a non-positive amount, or an index outside the
     * category's current period list after its period count was edited — is ignored ENTIRELY, both
     * legs together, so budget can never quietly disappear from the month.
     *
     * Each move is capped at what is still left of the source period at that moment (its base plus
     * anything already carried in, less anything already carried out), which keeps every effective
     * budget at or above zero and keeps the period budgets summing to exactly the monthly budget.
     */
    internal fun applyCarryForwards(
        records: List<BudgetCarryForward>,
        base: LongArray,
    ): Pair<LongArray, LongArray> {
        val carriedIn = LongArray(base.size)
        val carriedOut = LongArray(base.size)

        records
            .sortedWith(compareBy({ it.createdAt }, { it.id.toString() }))
            .forEach { record ->
                val valid = record.amountMinor > 0 &&
                    record.sourcePeriod in base.indices &&
                    record.targetPeriod in base.indices &&
                    record.sourcePeriod < record.targetPeriod
                if (!valid) return@forEach

                val source = record.sourcePeriod
                val available = base[source] + carriedIn[source] - carriedOut[source]
                val applied = minOf(record.amountMinor, available)
                if (applied <= 0L) return@forEach

                carriedOut[source] += applied
                carriedIn[record.targetPeriod] += applied
            }

        return carriedIn to carriedOut
    }
}
