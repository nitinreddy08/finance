package com.budgetpace.app.domain.usecase

import androidx.room.withTransaction
import com.budgetpace.app.core.model.BudgetMonth
import com.budgetpace.app.core.model.MonthStatus
import com.budgetpace.app.data.local.dao.BudgetMonthDao
import com.budgetpace.app.data.local.dao.CategoryDao
import com.budgetpace.app.data.local.db.BudgetDatabase
import com.budgetpace.app.data.local.entity.BudgetMonthEntity
import com.budgetpace.app.data.local.entity.CategoryEntity
import com.budgetpace.app.data.local.mapper.toDomain
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spec §57 (month rollover) + §46/§47: exactly one BudgetMonth is ACTIVE at a time, and the
 * active month always matches the current calendar month. Old months are ARCHIVED, never
 * deleted, and remain locally accessible (§58).
 *
 * This is the single place that creates or rolls over the active month, called both from the
 * ingestion pipeline (a transaction must always land in a real month, possibly a past one — spec
 * §17) and from app startup/foreground, so a month change is picked up "at the first suitable
 * app/background execution" as the spec requires. Those call sites are completely uncoordinated
 * (a background SMS receiver can run at the same instant the app is cold-starting), so every
 * public entry point takes the same [mutex] — `@Singleton` ensures they all share one instance.
 */
@Singleton
class EnsureActiveMonthUseCase @Inject constructor(
    private val database: BudgetDatabase,
    private val budgetMonthDao: BudgetMonthDao,
    private val categoryDao: CategoryDao,
) {
    private val mutex = Mutex()

    /**
     * Rolls the active month forward to [today]'s calendar month if it has not already, then
     * returns whichever month is active. Never rolls backward: if the active month is somehow
     * ahead of [today] (a clock that jumped back, or a month created for a transaction dated
     * later than the device's current idea of "today"), it is left alone rather than archived,
     * because archiving it would make it invisible while it is still meant to be current.
     */
    suspend operator fun invoke(today: LocalDate = LocalDate.now()): BudgetMonth =
        mutex.withLock { ensureActiveLocked(today) }

    /**
     * Spec §17: attributes a transaction to the month its own date belongs to, not necessarily
     * today's. A date in today's calendar month goes to the (now-guaranteed-active) current
     * month. A date in a past month reuses that month's row if one exists — even if it is
     * ARCHIVED — or creates it ARCHIVED, seeded from the nearest earlier month's categories, so a
     * late-arriving SMS for a month that already closed still lands somewhere sensible instead of
     * being folded into the current month's numbers.
     *
     * Never creates a future month: [resolveDate] on the caller's side already clamps a
     * message's claimed date to the arrival date when it is in the future, and here too any
     * month at or beyond `today`'s is treated as the current month — an ARCHIVED row for a month
     * that has not started yet would collide with that month's own rollover INSERT the moment it
     * actually arrives (`UNIQUE(year, month)`), crashing the app on every later launch.
     */
    suspend fun forTransactionDate(date: LocalDate, today: LocalDate): BudgetMonth =
        mutex.withLock {
            val current = ensureActiveLocked(today)
            val target = YearMonth.from(date)
            if (!target.isBefore(YearMonth.from(today))) return@withLock current

            budgetMonthDao.getByYearMonth(target.year, target.monthValue)?.toDomain()
                ?: createArchivedMonth(target)
        }

    private suspend fun ensureActiveLocked(today: LocalDate): BudgetMonth {
        val active = budgetMonthDao.getActiveMonth()

        if (active != null) {
            if (isSameMonth(active, today) || isAfterToday(active, today)) {
                return active.toDomain()
            }
            return rollOver(active, today)
        }

        // No ACTIVE row at all: either the very first launch (onboarding creates the first month
        // itself, but this stays safe to call standalone too), or a previous rollover was killed
        // between archiving the old month and inserting the new one. Either way, reusing an
        // existing row for today's month if one is somehow already there — and otherwise seeding
        // the new one from the most recently ARCHIVED month — is what prevents a silent loss of
        // the owner's whole category configuration.
        val target = YearMonth.from(today)
        val existing = budgetMonthDao.getByYearMonth(target.year, target.monthValue)
        if (existing != null) {
            return reactivate(existing)
        }
        return database.withTransaction {
            val seedFrom = budgetMonthDao.getAll().firstOrNull()
            createMonthWithCategories(target, MonthStatus.ACTIVE, seedFrom?.id)
        }
    }

    private suspend fun rollOver(active: BudgetMonthEntity, today: LocalDate): BudgetMonth {
        val target = YearMonth.from(today)
        return database.withTransaction {
            val now = Instant.now()
            budgetMonthDao.update(
                active.copy(status = MonthStatus.ARCHIVED.name, archivedAt = now.toEpochMilli())
            )
            // A row for today's month may already exist (an earlier crash mid-rollover, or a
            // transaction dated into the new month arrived before the app itself rolled over) —
            // reactivating it avoids a UNIQUE(year, month) violation on a fresh INSERT.
            val existing = budgetMonthDao.getByYearMonth(target.year, target.monthValue)
            if (existing != null) {
                budgetMonthDao.update(existing.copy(status = MonthStatus.ACTIVE.name, archivedAt = null))
                return@withTransaction existing.toDomain()
            }
            createMonthWithCategories(target, MonthStatus.ACTIVE, active.id, now).toDomain()
        }
    }

    private suspend fun reactivate(month: BudgetMonthEntity): BudgetMonth {
        budgetMonthDao.update(month.copy(status = MonthStatus.ACTIVE.name, archivedAt = null))
        return month.toDomain()
    }

    private suspend fun createArchivedMonth(target: YearMonth): BudgetMonth =
        database.withTransaction {
            val seedFrom = budgetMonthDao.getAll()
                .filter { !YearMonth.of(it.year, it.month).isAfter(target) }
                .maxByOrNull { YearMonth.of(it.year, it.month) }
            createMonthWithCategories(target, MonthStatus.ARCHIVED, seedFrom?.id).toDomain()
        }

    /** Must run inside the caller's `withTransaction { }` — creating a month and copying its
     * category configuration is one atomic step, or a kill in between loses the configuration
     * silently. */
    private suspend fun createMonthWithCategories(
        target: YearMonth,
        status: MonthStatus,
        seedFromMonthId: String?,
        now: Instant = Instant.now(),
    ): BudgetMonthEntity {
        val month = BudgetMonthEntity(
            id = UUID.randomUUID().toString(),
            year = target.year,
            month = target.monthValue,
            status = status.name,
            createdAt = now.toEpochMilli(),
            archivedAt = if (status == MonthStatus.ARCHIVED) now.toEpochMilli() else null,
        )
        budgetMonthDao.insert(month)

        if (seedFromMonthId != null) {
            val previousCategories = categoryDao.getByMonth(seedFromMonthId).filter { it.active }
            if (previousCategories.isNotEmpty()) {
                categoryDao.insertAll(previousCategories.map { it.copyForNewMonth(month.id, now) })
            }
        }
        return month
    }

    private fun isSameMonth(month: BudgetMonthEntity, date: LocalDate) =
        month.year == date.year && month.month == date.monthValue

    private fun isAfterToday(month: BudgetMonthEntity, today: LocalDate) =
        YearMonth.of(month.year, month.month).isAfter(YearMonth.from(today))

    private fun CategoryEntity.copyForNewMonth(newMonthId: String, now: Instant) = copy(
        id = UUID.randomUUID().toString(),
        monthId = newMonthId,
        createdAt = now.toEpochMilli(),
        updatedAt = now.toEpochMilli(),
    )
}
