package com.budgetpace.app.domain.usecase

import com.budgetpace.app.core.model.BudgetMonth
import com.budgetpace.app.core.model.MonthStatus
import com.budgetpace.app.data.local.dao.BudgetMonthDao
import com.budgetpace.app.data.local.dao.CategoryDao
import com.budgetpace.app.data.local.entity.BudgetMonthEntity
import com.budgetpace.app.data.local.entity.CategoryEntity
import com.budgetpace.app.data.local.mapper.toDomain
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spec §57 (month rollover) + §46/§47: exactly one BudgetMonth is ACTIVE at a time, and the
 * active month always matches the current calendar month. Old months are ARCHIVED, never
 * deleted, and remain locally accessible (§58).
 *
 * This is the single place that creates or rolls over the active month, called both from the
 * notification pipeline (a transaction must always land in a real, current month) and from app
 * startup, so a month change is picked up "at the first suitable app/background execution" as
 * the spec requires — there is no calendar-triggered background job in V1. Those two call sites
 * are completely uncoordinated (a background SMS notification can arrive at the same instant the
 * app is cold-starting), and the read-then-archive-then-create sequence below isn't a single DB
 * transaction, so without a lock two racing calls could both see the same stale "active" month and
 * both roll it over — duplicating the previous month's categories into the new month. `@Singleton`
 * ensures both call sites share the exact same `Mutex` instance, not one each.
 */
@Singleton
class EnsureActiveMonthUseCase @Inject constructor(
    private val budgetMonthDao: BudgetMonthDao,
    private val categoryDao: CategoryDao,
) {
    private val mutex = Mutex()

    suspend operator fun invoke(today: LocalDate = LocalDate.now()): BudgetMonth = mutex.withLock {
        val active = budgetMonthDao.getActiveMonth()

        if (active != null && active.year == today.year && active.month == today.monthValue) {
            return@withLock active.toDomain()
        }

        if (active == null) {
            // First-ever month (should normally be created by onboarding, but this makes the
            // use case safe to call standalone too, e.g. from the notification pipeline).
            return@withLock createMonth(today).toDomain()
        }

        // Calendar month has moved on: archive the old one, carry its category *configuration*
        // (not its transactions, per §47) into a fresh ACTIVE month for the current month.
        val now = Instant.now()
        budgetMonthDao.update(active.copy(status = MonthStatus.ARCHIVED.name, archivedAt = now.toEpochMilli()))

        val newMonth = createMonth(today, now)

        val previousCategories = categoryDao.getByMonth(active.id).filter { it.active }
        if (previousCategories.isNotEmpty()) {
            categoryDao.insertAll(previousCategories.map { it.copyForNewMonth(newMonth.id, now) })
        }

        newMonth.toDomain()
    }

    private suspend fun createMonth(today: LocalDate, now: Instant = Instant.now()): BudgetMonthEntity {
        val month = BudgetMonthEntity(
            id = UUID.randomUUID().toString(),
            year = today.year,
            month = today.monthValue,
            status = MonthStatus.ACTIVE.name,
            createdAt = now.toEpochMilli(),
            archivedAt = null,
        )
        budgetMonthDao.insert(month)
        return month
    }

    private fun CategoryEntity.copyForNewMonth(newMonthId: String, now: Instant) = copy(
        id = UUID.randomUUID().toString(),
        monthId = newMonthId,
        createdAt = now.toEpochMilli(),
        updatedAt = now.toEpochMilli(),
    )
}
