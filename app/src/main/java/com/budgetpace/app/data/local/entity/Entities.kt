package com.budgetpace.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.budgetpace.app.core.model.MonthStatus
import com.budgetpace.app.core.model.RecordDecision
import com.budgetpace.app.core.model.SyncState
import com.budgetpace.app.core.model.TransactionDirection
import com.budgetpace.app.core.model.Bank
import java.util.UUID

// ─── BudgetMonth ──────────────────────────────────────────────────────────────
@Entity(
    tableName = "budget_months",
    indices = [Index(value = ["year", "month"], unique = true)]
)
data class BudgetMonthEntity(
    @PrimaryKey val id: String,                  // UUID string
    val year: Int,
    val month: Int,
    val status: String,                          // MonthStatus name
    val createdAt: Long,                         // epoch millis
    val archivedAt: Long?,
)

// ─── Category ─────────────────────────────────────────────────────────────────
@Entity(
    tableName = "categories",
    foreignKeys = [ForeignKey(
        entity = BudgetMonthEntity::class,
        parentColumns = ["id"],
        childColumns  = ["monthId"],
        onDelete      = ForeignKey.CASCADE,
    )],
    indices = [Index("monthId")]
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val monthId: String,
    val name: String,
    val monthlyBudgetMinor: Long,
    val periodCount: Int,
    val iconKey: String,
    val sortOrder: Int,
    val active: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

// ─── Transaction ──────────────────────────────────────────────────────────────
@Entity(
    tableName = "transactions",
    foreignKeys = [ForeignKey(
        entity = BudgetMonthEntity::class,
        parentColumns = ["id"],
        childColumns  = ["monthId"],
        onDelete      = ForeignKey.CASCADE,
    )],
    indices = [
        Index("monthId"),
        Index("categoryId"),
        Index("transactionDate"),
        Index("syncState"),
        Index(value = ["bank", "referenceNumber"], unique = true, name = "idx_bank_ref"),
        // Fallback duplicate detection per spec §19: SQLite treats each NULL as distinct,
        // so rows without a fingerprint (e.g. reference-number duplicates already caught
        // above) never collide here, while two rows sharing the same non-null fallback
        // fingerprint are rejected as duplicates.
        Index(value = ["duplicateKey"], unique = true, name = "idx_duplicate_key"),
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val monthId: String,
    val amountMinor: Long,
    val currency: String,
    val direction: String,                       // TransactionDirection name
    val categoryId: String?,
    val transactionDateTime: Long?,              // epoch millis, nullable
    val transactionDate: String,                 // ISO-8601 date: yyyy-MM-dd
    val notificationReceivedAt: Long,
    val bank: String,                            // Bank name
    val accountSuffix: String?,
    val recipient: String?,
    val sender: String?,
    val referenceNumber: String?,
    val sourcePackage: String?,
    val sourceSender: String?,
    val sourceMessageHash: String?,
    val duplicateKey: String?,
    val recordDecision: String,                  // RecordDecision name
    val syncState: String,                       // SyncState name
    val parserVersion: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

// ─── BudgetCarryForward ───────────────────────────────────────────────────────
@Entity(
    tableName = "carry_forwards",
    foreignKeys = [
        ForeignKey(
            entity = BudgetMonthEntity::class,
            parentColumns = ["id"],
            childColumns  = ["monthId"],
            onDelete      = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns  = ["categoryId"],
            onDelete      = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("monthId"), Index("categoryId")]
)
data class CarryForwardEntity(
    @PrimaryKey val id: String,
    val monthId: String,
    val categoryId: String,
    val sourcePeriod: Int,
    val targetPeriod: Int,
    val amountMinor: Long,
    val createdAt: Long,
)

// ─── DeletedTransaction (sync tombstone) ───────────────────────────────────────
// A local hard-delete (TransactionRepositoryImpl.delete) removes the transaction row itself, so
// there's nothing left to look up by UUID once it's gone — this tombstone is what lets the next
// Sheets sync find and clear the now-orphaned row in the backup spreadsheet, then delete itself.
@Entity(tableName = "deleted_transactions")
data class DeletedTransactionEntity(
    @PrimaryKey val transactionId: String,
    val deletedAt: Long,
)
