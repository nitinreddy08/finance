package com.budgetpace.app.domain.budget

import com.budgetpace.app.core.model.*
import com.budgetpace.app.core.time.PeriodCalculator
import java.time.LocalDate
import java.util.UUID

object PaceCalculator {

    /**
     * Calculates the pace ratio and status for a period.
     * @param spentMinor The amount spent so far in the period.
     * @param effectiveBudgetMinor The total effective budget for the period (base + carry forward).
     * @param startDate The start date of the period.
     * @param endDate The end date of the period.
     * @param today The current date for calculating elapsed time.
     */
    fun calculatePace(
        spentMinor: Long,
        effectiveBudgetMinor: Long,
        startDate: LocalDate,
        endDate: LocalDate,
        today: LocalDate = LocalDate.now()
    ): Pair<Double, BudgetStatus> {
        val totalDays = endDate.toEpochDay() - startDate.toEpochDay() + 1
        
        // Future period
        if (today.isBefore(startDate)) {
            return 0.0 to BudgetStatus.GREY
        }
        
        // Completed period
        if (today.isAfter(endDate)) {
            val ratio = if (effectiveBudgetMinor == 0L) {
                if (spentMinor > 0) Double.MAX_VALUE else 0.0
            } else {
                spentMinor.toDouble() / effectiveBudgetMinor.toDouble()
            }
            return ratio to getStatusForRatio(ratio)
        }
        
        // Current in-progress period
        val elapsedDays = today.toEpochDay() - startDate.toEpochDay() + 1
        val elapsedFraction = elapsedDays.toDouble() / totalDays.toDouble()
        val expectedBudgetToDate = effectiveBudgetMinor.toDouble() * elapsedFraction
        
        val paceRatio = if (expectedBudgetToDate <= 0.0) {
             if (spentMinor > 0) Double.MAX_VALUE else 0.0
        } else {
             spentMinor.toDouble() / expectedBudgetToDate
        }
        
        return paceRatio to getStatusForRatio(paceRatio)
    }
    
    private fun getStatusForRatio(ratio: Double): BudgetStatus {
        return when {
            ratio <= 1.00 -> BudgetStatus.GREEN
            ratio <= 1.20 -> BudgetStatus.ORANGE
            else -> BudgetStatus.RED
        }
    }
}
