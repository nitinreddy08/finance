package com.budgetpace.app.core.time

import java.time.LocalDate
import java.time.YearMonth

/**
 * Splits a calendar month into 4 equal-width periods using actual days.
 *
 * Per spec §26:
 *   28 days → 7 / 7 / 7 / 7
 *   30 days → 8 / 7 / 8 / 7
 *   31 days → 8 / 8 / 8 / 7
 *
 * The split is deterministic: base = daysInMonth / 4 (integer division),
 * remainder r = daysInMonth % 4 → first r periods each get one extra day.
 *
 * The sum of all period day counts ALWAYS equals daysInMonth.
 */
data class MonthPeriod(
    val periodIndex: Int,       // 0-based (0..3)
    val startDate: LocalDate,
    val endDate: LocalDate,     // inclusive
) {
    val days: Int get() = endDate.dayOfMonth - startDate.dayOfMonth + 1
    val label: String get() = "PERIOD ${periodIndex + 1}"
}

object PeriodCalculator {

    /**
     * Returns the 4 MonthPeriods for a given [year] and [month] (1-based).
     */
    fun periodsFor(year: Int, month: Int): List<MonthPeriod> {
        val ym = YearMonth.of(year, month)
        val daysInMonth = ym.lengthOfMonth()
        return periodsForDays(year, month, daysInMonth)
    }

    /**
     * Returns the 4 MonthPeriods for a given [date]'s month.
     */
    fun periodsFor(date: LocalDate): List<MonthPeriod> =
        periodsFor(date.year, date.monthValue)

    fun periodsForDays(year: Int, month: Int, daysInMonth: Int): List<MonthPeriod> {
        // Deterministic even split via cumulative ceiling division:
        //   cumulative(i) = ceil(daysInMonth * (i + 1) / 4)
        //   periodDays(i)  = cumulative(i) - cumulative(i - 1), cumulative(-1) = 0
        // This reproduces the spec's §26 worked examples exactly:
        //   28 days -> 7 / 7 / 7 / 7
        //   30 days -> 8 / 7 / 8 / 7
        //   31 days -> 8 / 8 / 8 / 7
        val periods = mutableListOf<MonthPeriod>()
        var dayOffset = 1
        var previousCumulative = 0
        for (i in 0 until 4) {
            val cumulative = ceilDiv(daysInMonth * (i + 1), 4)
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
     * Returns which period index (0-based) the [date] falls into,
     * or -1 if the date is outside the month.
     */
    fun periodIndexFor(date: LocalDate): Int {
        val periods = periodsFor(date)
        return periods.indexOfFirst { date in it }
    }

    /**
     * Calculates period budgets so they sum exactly to [monthlyBudgetMinor].
     *
     * Per spec §27:
     *   periodBudget = M × periodDays / daysInMonth   (integer paise)
     *   Remainder distributed to earlier periods deterministically.
     */
    fun splitBudget(monthlyBudgetMinor: Long, periods: List<MonthPeriod>): LongArray {
        val daysInMonth = periods.sumOf { it.days }
        val result = LongArray(4)
        var allocated = 0L
        for (i in 0 until 3) {
            val share = monthlyBudgetMinor * periods[i].days / daysInMonth
            result[i] = share
            allocated += share
        }
        result[3] = monthlyBudgetMinor - allocated   // last period absorbs any rounding remainder
        return result
    }
}

// Extension for readable range check
private operator fun MonthPeriod.contains(date: LocalDate): Boolean =
    !date.isBefore(startDate) && !date.isAfter(endDate)
