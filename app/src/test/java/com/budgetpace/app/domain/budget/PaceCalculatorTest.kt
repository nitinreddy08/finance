package com.budgetpace.app.domain.budget

import com.budgetpace.app.core.model.BudgetStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PaceCalculatorTest {

    @Test
    fun testFuturePeriod() {
        // Period is entirely in the future
        val (ratio, status) = PaceCalculator.calculatePace(
            spentMinor = 0L,
            effectiveBudgetMinor = 1000L,
            startDate = LocalDate.of(2026, 9, 10),
            endDate = LocalDate.of(2026, 9, 20),
            today = LocalDate.of(2026, 9, 5) // Today is before start date
        )
        
        assertEquals(0.0, ratio, 0.001)
        assertEquals(BudgetStatus.GREY, status)
    }

    @Test
    fun testCompletedPeriodGreen() {
        val (ratio, status) = PaceCalculator.calculatePace(
            spentMinor = 950L,
            effectiveBudgetMinor = 1000L,
            startDate = LocalDate.of(2026, 8, 1),
            endDate = LocalDate.of(2026, 8, 7),
            today = LocalDate.of(2026, 8, 10) // Completed
        )
        
        assertEquals(0.95, ratio, 0.001)
        assertEquals(BudgetStatus.GREEN, status)
    }

    @Test
    fun testCompletedPeriodOrange() {
        val (ratio, status) = PaceCalculator.calculatePace(
            spentMinor = 1150L,
            effectiveBudgetMinor = 1000L,
            startDate = LocalDate.of(2026, 8, 1),
            endDate = LocalDate.of(2026, 8, 7),
            today = LocalDate.of(2026, 8, 10) // Completed
        )
        
        assertEquals(1.15, ratio, 0.001)
        assertEquals(BudgetStatus.ORANGE, status)
    }

    @Test
    fun testCompletedPeriodRed() {
        val (ratio, status) = PaceCalculator.calculatePace(
            spentMinor = 1250L,
            effectiveBudgetMinor = 1000L,
            startDate = LocalDate.of(2026, 8, 1),
            endDate = LocalDate.of(2026, 8, 7),
            today = LocalDate.of(2026, 8, 10) // Completed
        )
        
        assertEquals(1.25, ratio, 0.001)
        assertEquals(BudgetStatus.RED, status)
    }

    @Test
    fun testCurrentPeriodPaceGreen() {
        // 10 day period, we are on day 5 (50% elapsed)
        // Budget = 1000, Expected to date = 500
        val (ratio, status) = PaceCalculator.calculatePace(
            spentMinor = 400L, // Spent less than 500
            effectiveBudgetMinor = 1000L,
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 10),
            today = LocalDate.of(2026, 9, 5) 
        )
        
        // 400 / 500 = 0.8
        assertEquals(0.8, ratio, 0.001)
        assertEquals(BudgetStatus.GREEN, status)
    }

    @Test
    fun testCurrentPeriodPaceOrange() {
        // 10 day period, we are on day 5 (50% elapsed)
        // Budget = 1000, Expected to date = 500
        val (ratio, status) = PaceCalculator.calculatePace(
            spentMinor = 550L, // Spent slightly more than 500
            effectiveBudgetMinor = 1000L,
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 10),
            today = LocalDate.of(2026, 9, 5) 
        )
        
        // 550 / 500 = 1.1
        assertEquals(1.1, ratio, 0.001)
        assertEquals(BudgetStatus.ORANGE, status)
    }

    @Test
    fun exactlyOnBudgetIsGreenAndExactlyTwentyPercentOverIsOrange() {
        // Spec section 29 puts both boundaries inside the friendlier colour.
        assertEquals(BudgetStatus.GREEN, PaceCalculator.statusFor(1000L, 1000L))
        assertEquals(BudgetStatus.ORANGE, PaceCalculator.statusFor(1200L, 1000L))
        assertEquals(BudgetStatus.RED, PaceCalculator.statusFor(1201L, 1000L))
    }

    @Test
    fun statusComparesAgainstTheExactExpectationNotAFlooredOne() {
        // A 250 rupee, 7-day period on day 2 expects 71.42857 rupees. Spending 85.71 is a ratio of
        // 1.19994, which is ORANGE — but against the floored 71.42 it reads as over 1.20 and would
        // turn the category red a step early.
        assertEquals(
            BudgetStatus.ORANGE,
            PaceCalculator.statusFor(spentMinor = 8571L, budgetMinor = 25000L, elapsedDays = 2L, totalDays = 7L)
        )
        assertEquals(7142L, PaceCalculator.expectedToDateMinor(
            effectiveBudgetMinor = 25000L,
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 7),
            today = LocalDate.of(2026, 9, 2),
        ))
    }

    @Test
    fun spendingAgainstNoBudgetAtAllIsRed() {
        assertEquals(BudgetStatus.RED, PaceCalculator.statusFor(1L, 0L))
        assertEquals(BudgetStatus.GREEN, PaceCalculator.statusFor(0L, 0L))
    }

    @Test
    fun expectedToDateCoversTheWholeBudgetOnceThePeriodIsOver() {
        val start = LocalDate.of(2026, 9, 1)
        val end = LocalDate.of(2026, 9, 8)

        assertEquals(0L, PaceCalculator.expectedToDateMinor(80000L, start, end, LocalDate.of(2026, 8, 31)))
        assertEquals(10000L, PaceCalculator.expectedToDateMinor(80000L, start, end, start))
        assertEquals(80000L, PaceCalculator.expectedToDateMinor(80000L, start, end, end))
        assertEquals(80000L, PaceCalculator.expectedToDateMinor(80000L, start, end, LocalDate.of(2026, 9, 20)))
    }
}
