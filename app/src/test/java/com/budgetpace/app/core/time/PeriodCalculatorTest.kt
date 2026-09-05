package com.budgetpace.app.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PeriodCalculatorTest {

    @Test
    fun test28DayMonth() {
        // Feb 2026 (non-leap year) has 28 days
        val periods = PeriodCalculator.periodsFor(2026, 2)
        assertEquals(4, periods.size)
        
        // 28 days -> 7 / 7 / 7 / 7
        assertEquals(7, periods[0].days)
        assertEquals(7, periods[1].days)
        assertEquals(7, periods[2].days)
        assertEquals(7, periods[3].days)
        
        val totalDays = periods.sumOf { it.days }
        assertEquals(28, totalDays)
    }

    @Test
    fun test30DayMonth() {
        // Sep 2026 has 30 days
        val periods = PeriodCalculator.periodsFor(2026, 9)
        assertEquals(4, periods.size)
        
        // 30 days -> 8 / 7 / 8 / 7 per spec §26
        assertEquals(8, periods[0].days)
        assertEquals(7, periods[1].days)
        assertEquals(8, periods[2].days)
        assertEquals(7, periods[3].days)
        
        val totalDays = periods.sumOf { it.days }
        assertEquals(30, totalDays)
    }

    @Test
    fun test31DayMonth() {
        // Aug 2026 has 31 days
        val periods = PeriodCalculator.periodsFor(2026, 8)
        assertEquals(4, periods.size)
        
        // 31 days -> 8 / 8 / 8 / 7
        assertEquals(8, periods[0].days)
        assertEquals(8, periods[1].days)
        assertEquals(8, periods[2].days)
        assertEquals(7, periods[3].days)
        
        val totalDays = periods.sumOf { it.days }
        assertEquals(31, totalDays)
    }

    @Test
    fun testBudgetSplitExactSum() {
        // Budget ₹1000.00 -> 100,000 paise
        val monthlyBudgetMinor = 100_000L

        val splitBudgets = PeriodCalculator.splitBudget(monthlyBudgetMinor, 4)

        val totalSplit = splitBudgets.sum()
        assertEquals(monthlyBudgetMinor, totalSplit)
    }

    @Test
    fun testBudgetSplitIsEqualNotProportionalToDays() {
        // The split is purely by period count, independent of how many days each period spans —
        // a category that says "spread across 4 periods" should see 4 equal amounts, not amounts
        // weighted by each period's day count (an earlier version of this function did the
        // latter, e.g. ₹1500 over Sept 2026's 8/7/8/7-day periods came out ₹400/₹350/₹400/₹350
        // instead of ₹375 each).
        val split = PeriodCalculator.splitBudget(1500_00L, 4)
        assertEquals(4, split.size)
        assertEquals(375_00L, split[0])
        assertEquals(375_00L, split[1])
        assertEquals(375_00L, split[2])
        assertEquals(375_00L, split[3])
    }

    @Test
    fun testBudgetSplitRemainderSpreadAcrossFirstPeriods() {
        // 1000 / 3 = 333 remainder 1 -> the first period absorbs the one extra paisa rather than
        // the last, so no single period is a visible outlier.
        val split = PeriodCalculator.splitBudget(1000L, 3)
        assertEquals(334L, split[0])
        assertEquals(333L, split[1])
        assertEquals(333L, split[2])
        assertEquals(1000L, split.sum())
    }

    @Test
    fun testDynamicPeriodCounts() {
        // A category can choose any period count, not just a fixed 4 — 2 and 3 periods should
        // divide the month's days (and, separately, its budget) just as evenly as 4 does.
        val twoPeriods = PeriodCalculator.periodsFor(2026, 9, periodCount = 2) // 30 days
        assertEquals(2, twoPeriods.size)
        assertEquals(15, twoPeriods[0].days)
        assertEquals(15, twoPeriods[1].days)

        val threePeriods = PeriodCalculator.periodsFor(2026, 9, periodCount = 3) // 30 days
        assertEquals(3, threePeriods.size)
        assertEquals(10, threePeriods[0].days)
        assertEquals(10, threePeriods[1].days)
        assertEquals(10, threePeriods[2].days)

        val onePeriod = PeriodCalculator.periodsFor(2026, 9, periodCount = 1)
        assertEquals(1, onePeriod.size)
        assertEquals(30, onePeriod[0].days)
    }

    @Test
    fun testClampedPeriodIndexInsideTheMonth() {
        val periods = PeriodCalculator.periodsFor(2026, 9) // 1-8 / 9-15 / 16-23 / 24-30

        assertEquals(0, PeriodCalculator.clampedPeriodIndex(periods, LocalDate.of(2026, 9, 1)))
        assertEquals(0, PeriodCalculator.clampedPeriodIndex(periods, LocalDate.of(2026, 9, 8)))
        assertEquals(1, PeriodCalculator.clampedPeriodIndex(periods, LocalDate.of(2026, 9, 9)))
        assertEquals(2, PeriodCalculator.clampedPeriodIndex(periods, LocalDate.of(2026, 9, 16)))
        assertEquals(3, PeriodCalculator.clampedPeriodIndex(periods, LocalDate.of(2026, 9, 30)))
    }

    @Test
    fun testClampedPeriodIndexOutsideTheMonth() {
        // A transaction filed under this month but dated outside it must still land in exactly one
        // period, so the period spends keep adding up to the month's total.
        val periods = PeriodCalculator.periodsFor(2026, 9)

        assertEquals(0, PeriodCalculator.clampedPeriodIndex(periods, LocalDate.of(2026, 8, 31)))
        assertEquals(0, PeriodCalculator.clampedPeriodIndex(periods, LocalDate.of(2025, 1, 1)))
        assertEquals(3, PeriodCalculator.clampedPeriodIndex(periods, LocalDate.of(2026, 10, 1)))
    }

    @Test
    fun testClampedPeriodIndexFollowsTheGridItIsGiven() {
        // The same date sits in different periods depending on how the category splits the month.
        val sixteenth = LocalDate.of(2026, 9, 16)

        assertEquals(2, PeriodCalculator.clampedPeriodIndex(PeriodCalculator.periodsFor(2026, 9), sixteenth))
        assertEquals(1, PeriodCalculator.clampedPeriodIndex(PeriodCalculator.periodsFor(2026, 9, 2), sixteenth))
        assertEquals(1, PeriodCalculator.clampedPeriodIndex(PeriodCalculator.periodsFor(2026, 9, 3), sixteenth))
        assertEquals(0, PeriodCalculator.clampedPeriodIndex(PeriodCalculator.periodsFor(2026, 9, 1), sixteenth))
    }
}
