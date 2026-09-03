package com.budgetpace.app.data.local.mapper

import com.budgetpace.app.core.model.*
import com.budgetpace.app.data.local.entity.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ─── BudgetMonth ──────────────────────────────────────────────────────────────
fun BudgetMonthEntity.toDomain() = BudgetMonth(
    id         = UUID.fromString(id),
    year       = year,
    month      = month,
    status     = MonthStatus.valueOf(status),
    createdAt  = Instant.ofEpochMilli(createdAt),
    archivedAt = archivedAt?.let { Instant.ofEpochMilli(it) },
)

fun BudgetMonth.toEntity() = BudgetMonthEntity(
    id         = id.toString(),
    year       = year,
    month      = month,
    status     = status.name,
    createdAt  = createdAt.toEpochMilli(),
    archivedAt = archivedAt?.toEpochMilli(),
)

// ─── Category ─────────────────────────────────────────────────────────────────
fun CategoryEntity.toDomain() = Category(
    id                   = UUID.fromString(id),
    monthId              = UUID.fromString(monthId),
    name                 = name,
    monthlyBudgetMinor   = monthlyBudgetMinor,
    periodCount          = periodCount,
    iconKey              = iconKey,
    sortOrder            = sortOrder,
    active               = active,
    createdAt            = Instant.ofEpochMilli(createdAt),
    updatedAt            = Instant.ofEpochMilli(updatedAt),
)

fun Category.toEntity() = CategoryEntity(
    id                   = id.toString(),
    monthId              = monthId.toString(),
    name                 = name,
    monthlyBudgetMinor   = monthlyBudgetMinor,
    periodCount          = periodCount,
    iconKey              = iconKey,
    sortOrder            = sortOrder,
    active               = active,
    createdAt            = createdAt.toEpochMilli(),
    updatedAt            = updatedAt.toEpochMilli(),
)

// ─── Transaction ──────────────────────────────────────────────────────────────
fun TransactionEntity.toDomain() = Transaction(
    id                      = UUID.fromString(id),
    monthId                 = UUID.fromString(monthId),
    amountMinor             = amountMinor,
    currency                = currency,
    direction               = TransactionDirection.valueOf(direction),
    categoryId              = categoryId?.let { UUID.fromString(it) },
    transactionDateTime     = transactionDateTime?.let { Instant.ofEpochMilli(it) },
    transactionDate         = LocalDate.parse(transactionDate),
    notificationReceivedAt  = Instant.ofEpochMilli(notificationReceivedAt),
    bank                    = Bank.valueOf(bank),
    accountSuffix           = accountSuffix,
    recipient               = recipient,
    sender                  = sender,
    referenceNumber         = referenceNumber,
    sourcePackage           = sourcePackage,
    sourceSender            = sourceSender,
    sourceMessageHash       = sourceMessageHash,
    duplicateKey            = duplicateKey,
    recordDecision          = RecordDecision.valueOf(recordDecision),
    syncState               = SyncState.valueOf(syncState),
    parserVersion           = parserVersion,
    createdAt               = Instant.ofEpochMilli(createdAt),
    updatedAt               = Instant.ofEpochMilli(updatedAt),
)

fun Transaction.toEntity() = TransactionEntity(
    id                      = id.toString(),
    monthId                 = monthId.toString(),
    amountMinor             = amountMinor,
    currency                = currency,
    direction               = direction.name,
    categoryId              = categoryId?.toString(),
    transactionDateTime     = transactionDateTime?.toEpochMilli(),
    transactionDate         = transactionDate.toString(),
    notificationReceivedAt  = notificationReceivedAt.toEpochMilli(),
    bank                    = bank.name,
    accountSuffix           = accountSuffix,
    recipient               = recipient,
    sender                  = sender,
    referenceNumber         = referenceNumber,
    sourcePackage           = sourcePackage,
    sourceSender            = sourceSender,
    sourceMessageHash       = sourceMessageHash,
    duplicateKey            = duplicateKey,
    recordDecision          = recordDecision.name,
    syncState               = syncState.name,
    parserVersion           = parserVersion,
    createdAt               = createdAt.toEpochMilli(),
    updatedAt               = updatedAt.toEpochMilli(),
)

// ─── CarryForward ─────────────────────────────────────────────────────────────
fun CarryForwardEntity.toDomain() = BudgetCarryForward(
    id           = UUID.fromString(id),
    monthId      = UUID.fromString(monthId),
    categoryId   = UUID.fromString(categoryId),
    sourcePeriod = sourcePeriod,
    targetPeriod = targetPeriod,
    amountMinor  = amountMinor,
    createdAt    = Instant.ofEpochMilli(createdAt),
)

fun BudgetCarryForward.toEntity() = CarryForwardEntity(
    id           = id.toString(),
    monthId      = monthId.toString(),
    categoryId   = categoryId.toString(),
    sourcePeriod = sourcePeriod,
    targetPeriod = targetPeriod,
    amountMinor  = amountMinor,
    createdAt    = createdAt.toEpochMilli(),
)
