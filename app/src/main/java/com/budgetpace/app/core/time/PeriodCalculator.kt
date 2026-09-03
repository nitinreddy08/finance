package com.budgetpace.app.core.time

import java.time.LocalDate
import java.time.YearMonth

/**
 * Splits a calendar month into N equal-width periods using actual days.
 *
 * The split is deterministic: base = daysInMonth / n (integer division),
 * remainder r = daysInMonth % n → first r periods each get one extra day.
 *
 * The sum of all period day counts ALWAYS equals daysInMonth.
 */
data class MonthPeriod(
    val periodIndex: Int,       // 0-based (0 until periodCount)
    val startDate: LocalDate,
    val endDate: LocalDate,     // inclusive
) {
    val days: Int get() = endDate.dayOfMonth - startDate.dayOfMonth + 1
    val label: String get() = "PERIOD ${periodIndex + 1}"
}

object PeriodCalculator {

    /** How many periods the overall month-level pace bar (independent of any one
     * category's own period count) is always divided into. */
    const val DEFAULT_PERIOD_COUNT = 4

    /**
     * Returns the [periodCount] MonthPeriods for a given [year] and [month] (1-based).
     */
    fun periodsFor(year: Int, month: Int, periodCount: Int = DEFAULT_PERIOD_COUNT): List<MonthPeriod> {
        val ym = YearMonth.of(year, month)
        val daysInMonth = ym.lengthOfMonth()
        return periodsForDays(year, month, daysInMonth, periodCount)
    }

    /**
     * Returns the [periodCount] MonthPeriods for a given [date]'s month.
     */
    fun periodsFor(date: LocalDate, periodCount: Int = DEFAULT_PERIOD_COUNT): List<MonthPeriod> =
        periodsFor(date.year, date.monthValue, periodCount)

    fun periodsForDays(year: Int, month: Int, daysInMonth: Int, periodCount: Int = DEFAULT_PERIOD_COUNT): List<MonthPeriod> {
        // Deterministic even split via cumulative ceiling division:
        //   cumulative(i) = ceil(daysInMonth * (i + 1) / periodCount)
        //   periodDays(i)  = cumulative(i) - cumulative(i - 1), cumulative(-1) = 0
        val periods = mutableListOf<MonthPeriod>()
        var dayOffset = 1
        var previousCumulative = 0
        for (i in 0 until periodCount) {
            val cumulative = ceilDiv(daysInMonth * (i + 1), periodCount)
            val periodDays = cumulative - previousCumulative
            previousCumulative = cumulative

            val start = LocalDate.of(year, month, dayOffset)
            val end = LocalDate.of(year, month, dayOffset + periodDays - 1)
            periods += MonthPeriod(periodIndex = i, startDate = start, endDate = end)
            dayOffset += periodDays
        }
        return periods
    }

    private fun ceilDiv(numerator: Int, denominator: Int): Int =
        (numerator + denominator - 1) / denominator

    /**
     * Returns which period index (0-based) the [date] falls into, for a month split into
     * [periodCount] periods, or -1 if the date is outside the month.
     */
    fun periodIndexFor(date: LocalDate, periodCount: Int = DEFAULT_PERIOD_COUNT): Int {
        val periods = periodsFor(date, periodCount)
        return periods.indexOfFirst { date in it }
    }

    /**
     * Divides [monthlyBudgetMinor] equally across [periodCount] periods (paise). Any remainder
     * from integer division is spread one-extra-unit-each across the first few periods, rather
     * than dumped entirely on the last one, so no single period looks like an outlier.
     */
    fun splitBudget(monthlyBudgetMinor: Long, periodCount: Int): LongArray {
        if (periodCount <= 0) return LongArray(0)
        val base = monthlyBudgetMinor / periodCount
        val remainder = (monthlyBudgetMinor % periodCount).toInt()
        return LongArray(periodCount) { i -> base + if (i < remainder) 1 else 0 }
    }
}

// Extension for readable range check
private operator fun MonthPeriod.contains(date: LocalDate): Boolean =
    !date.isBefore(startDate) && !date.isAfter(endDate)
