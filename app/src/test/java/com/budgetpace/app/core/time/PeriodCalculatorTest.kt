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
        val periods = PeriodCalculator.periodsFor(2026, 9) // 30 days
        
        // Budget ₹1000.00 -> 100,000 paise
        val monthlyBudgetMinor = 100_000L
        
        val splitBudgets = PeriodCalculator.splitBudget(monthlyBudgetMinor, periods)
        
        val totalSplit = splitBudgets.sum()
        assertEquals(monthlyBudgetMinor, totalSplit)
    }
}
