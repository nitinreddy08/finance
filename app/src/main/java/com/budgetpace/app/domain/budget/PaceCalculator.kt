package com.budgetpace.app.domain.budget

import com.budgetpace.app.core.model.BudgetStatus
import java.time.LocalDate

/**
 * "Am I spending too fast?" rather than "have I run out yet?" (spec section 30): spending is judged
 * against what the plan expected by today, not against the whole period's budget.
 */
object PaceCalculator {

    /**
     * What the plan expected to be spent by [today]: nothing before the period starts, the whole
     * budget once it is over, otherwise a share proportional to the days elapsed (inclusive of
     * today). Multiply before dividing so the rounding loss is at most one paisa.
     *
     * This floors, so it is the figure to DISPLAY. Do not derive a status from it — see [statusFor].
     */
    fun expectedToDateMinor(
        effectiveBudgetMinor: Long,
        startDate: LocalDate,
        endDate: LocalDate,
        today: LocalDate,
    ): Long {
        if (today.isBefore(startDate)) return 0L
        if (today.isAfter(endDate)) return effectiveBudgetMinor
        val totalDays = endDate.toEpochDay() - startDate.toEpochDay() + 1
        val elapsedDays = today.toEpochDay() - startDate.toEpochDay() + 1
        return effectiveBudgetMinor * elapsedDays / totalDays
    }

    /**
     * Spec section 29 thresholds against an expectation given as a whole-period budget plus how far
     * through the period today is: GREEN at or under 1.00, ORANGE at or under 1.20, else RED.
     *
     * The comparison cross-multiplies rather than dividing, so it stays in Long arithmetic (spec
     * section 6 forbids floating-point money) AND compares against the exact expectation. Comparing
     * against a floored expectation instead flips a category to RED one step early: a 250 rupee,
     * 7-day period on day 2 expects 71.42857, and 85.71 spent is a ratio of 1.19994 — ORANGE — but
     * against the floored 71.42 it reads as over 1.20.
     */
    fun statusFor(
        spentMinor: Long,
        budgetMinor: Long,
        elapsedDays: Long,
        totalDays: Long,
    ): BudgetStatus {
        val expectedNumerator = budgetMinor * elapsedDays
        if (expectedNumerator <= 0L || totalDays <= 0L) {
            return if (spentMinor > 0L) BudgetStatus.RED else BudgetStatus.GREEN
        }
        return when {
            spentMinor * totalDays <= expectedNumerator -> BudgetStatus.GREEN
            spentMinor * 5 * totalDays <= expectedNumerator * 6 -> BudgetStatus.ORANGE
            else -> BudgetStatus.RED
        }
    }

    /** Status against an expectation that is already a whole amount (a completed period, a lump sum). */
    fun statusFor(spentMinor: Long, expectedMinor: Long): BudgetStatus =
        statusFor(spentMinor, expectedMinor, elapsedDays = 1L, totalDays = 1L)

    /** Ratio for display only; infinite when something was spent against no expectation at all. */
    fun ratioFor(spentMinor: Long, expectedMinor: Long): Double = when {
        expectedMinor > 0L -> spentMinor.toDouble() / expectedMinor.toDouble()
        spentMinor > 0L -> Double.POSITIVE_INFINITY
        else -> 0.0
    }

    /**
     * Pace of one period. Returns the displayable ratio and the status; a period that has not
     * started yet is GREY rather than trivially green.
     */
    fun calculatePace(
        spentMinor: Long,
        effectiveBudgetMinor: Long,
        startDate: LocalDate,
        endDate: LocalDate,
        today: LocalDate,
    ): Pair<Double, BudgetStatus> {
        if (today.isBefore(startDate)) return 0.0 to BudgetStatus.GREY

        val totalDays = endDate.toEpochDay() - startDate.toEpochDay() + 1
        val elapsedDays = if (today.isAfter(endDate)) {
            totalDays
        } else {
            today.toEpochDay() - startDate.toEpochDay() + 1
        }

        val expected = expectedToDateMinor(effectiveBudgetMinor, startDate, endDate, today)
        return ratioFor(spentMinor, expected) to
            statusFor(spentMinor, effectiveBudgetMinor, elapsedDays, totalDays)
    }
}
