package com.budgetpace.app.core.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ─── Enums ────────────────────────────────────────────────────────────────────

enum class TransactionDirection { DEBIT, CREDIT }

enum class Bank { KOTAK, SBI, UNKNOWN }

enum class ParseConfidence { HIGH, MEDIUM, LOW }

enum class RecordDecision { RECORDED, IGNORED }

enum class SyncState { PENDING, SYNCED, FAILED }

enum class MonthStatus { ACTIVE, ARCHIVED }

enum class PeriodStatus {
    /** Future period — no spending yet, budget not started */
    UPCOMING,
    /** Current period in progress */
    CURRENT,
    /** Completed period */
    COMPLETED,
}

// ─── Domain models ────────────────────────────────────────────────────────────

data class BudgetMonth(
    val id: UUID,
    val year: Int,
    val month: Int,          // 1 = January … 12 = December
    val status: MonthStatus,
    val createdAt: Instant,
    val archivedAt: Instant?,
)

data class Category(
    val id: UUID,
    val monthId: UUID,
    val name: String,
    val monthlyBudgetMinor: Long,   // paise
    val weeklyPacingEnabled: Boolean,
    val iconKey: String,
    val sortOrder: Int,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class Transaction(
    val id: UUID,
    val monthId: UUID,
    val amountMinor: Long,          // paise, always positive
    val currency: String,           // "INR" in V1
    val direction: TransactionDirection,
    val categoryId: UUID?,
    val transactionDateTime: Instant?,
    val transactionDate: LocalDate,
    val notificationReceivedAt: Instant,
    val bank: Bank,
    val accountSuffix: String?,
    val recipient: String?,
    val sender: String?,
    val referenceNumber: String?,
    val sourcePackage: String?,
    val sourceSender: String?,
    val sourceMessageHash: String?,
    val duplicateKey: String?,
    val recordDecision: RecordDecision,
    val syncState: SyncState,
    val parserVersion: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class TransactionWithCategory(
    val transaction: Transaction,
    val category: Category?
)

data class BudgetCarryForward(
    val id: UUID,
    val monthId: UUID,
    val categoryId: UUID,
    val sourcePeriod: Int,          // 0-based period index
    val targetPeriod: Int,          // 0-based period index
    val amountMinor: Long,          // paise
    val createdAt: Instant,
)

// ─── Derived / computed models ────────────────────────────────────────────────

data class PeriodSummary(
    val periodIndex: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val baseBudgetMinor: Long,
    val carryForwardMinor: Long,
    val spentMinor: Long,
    val periodStatus: PeriodStatus,
    val paceStatus: BudgetStatus,   // GREEN / ORANGE / RED / GREY
    val paceRatio: Double,          // spent / effectiveBudget (or elapsed fraction)
    val isCurrentPeriod: Boolean,
) {
    val effectiveBudgetMinor: Long get() = baseBudgetMinor + carryForwardMinor
    val remainingMinor: Long get() = (effectiveBudgetMinor - spentMinor).coerceAtLeast(0)
    val overageMinor: Long get() = (spentMinor - effectiveBudgetMinor).coerceAtLeast(0)
    val days: Int get() = endDate.dayOfMonth - startDate.dayOfMonth + 1
}

data class CategorySummary(
    val category: Category,
    val periods: List<PeriodSummary>,
    val totalSpentMinor: Long,
    val overallStatus: BudgetStatus,
) {
    val remainingMinor: Long
        get() = (category.monthlyBudgetMinor - totalSpentMinor).coerceAtLeast(0)
}

data class MonthSummary(
    val month: BudgetMonth,
    val totalBudgetMinor: Long,
    val totalSpentMinor: Long,
    val safeToSpendMinor: Long,
    val overallPeriods: List<PeriodSummary>,
    val categories: List<CategorySummary>,
    val overallStatus: BudgetStatus,
) {
    val remainingMinor: Long get() = (totalBudgetMinor - totalSpentMinor).coerceAtLeast(0)
}

enum class BudgetStatus {
    /** ratio <= 1.00 */
    GREEN,
    /** 1.00 < ratio <= 1.20 */
    ORANGE,
    /** ratio > 1.20 */
    RED,
    /** Future / not started */
    GREY,
    /** Current in-progress period (colour determined by pace) */
    CURRENT,
}
