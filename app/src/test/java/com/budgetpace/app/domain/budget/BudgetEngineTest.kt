package com.budgetpace.app.domain.budget

import com.budgetpace.app.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class BudgetEngineTest {

    @Test
    fun testCategorySummaryNoWeeklyPacing() {
        val monthId = UUID.randomUUID()
        val catId = UUID.randomUUID()

        val month = BudgetMonth(monthId, 2026, 9, MonthStatus.ACTIVE, Instant.now(), null)
        val category = Category(catId, monthId, "Rent", 9000_00L, false, "icon", 0, true, Instant.now(), Instant.now())

        val transactions = listOf(
            Transaction(
                id = UUID.randomUUID(), monthId = monthId, amountMinor = 9000_00L,
                currency = "INR", direction = TransactionDirection.DEBIT, categoryId = catId,
                transactionDateTime = null, transactionDate = LocalDate.of(2026, 9, 1),
                notificationReceivedAt = Instant.now(), bank = Bank.SBI, accountSuffix = null,
                recipient = null, sender = null, referenceNumber = null, sourcePackage = null,
                sourceSender = null, sourceMessageHash = null, duplicateKey = null,
                recordDecision = RecordDecision.RECORDED, syncState = SyncState.PENDING,
                parserVersion = null, createdAt = Instant.now(), updatedAt = Instant.now()
            )
        )

        val summary = BudgetEngine.calculateMonthSummary(month, listOf(category), transactions, emptyList(), LocalDate.of(2026, 9, 2))

        val catSummary = summary.categories.first()

        // Per spec §24/§31: weeklyPacingEnabled=false only hides the category's OWN four-tile
        // breakdown in the UI — it must still participate fairly in the overall four-period pace,
        // so its period budgets are still split proportionally (Sep 2026 = 30 days -> 8/7/8/7).
        assertEquals(240000L, catSummary.periods[0].baseBudgetMinor)
        assertEquals(210000L, catSummary.periods[1].baseBudgetMinor)
        assertEquals(240000L, catSummary.periods[2].baseBudgetMinor)
        assertEquals(210000L, catSummary.periods[3].baseBudgetMinor)
        assertEquals(9000_00L, catSummary.periods.sumOf { it.baseBudgetMinor })

        // Spent should be registered
        assertEquals(9000_00L, catSummary.totalSpentMinor)
        assertEquals(0L, catSummary.remainingMinor)
    }

    @Test
    fun testOverallPaceIncludesNonPacingCategoryProportionally() {
        // A category with weeklyPacingEnabled = false (e.g. Rent) must still contribute its fair
        // share to every overall period's budget, not just period 0 (spec §26/§31).
        val monthId = UUID.randomUUID()
        val rentId = UUID.randomUUID()
        val fruitsId = UUID.randomUUID()

        val month = BudgetMonth(monthId, 2026, 9, MonthStatus.ACTIVE, Instant.now(), null) // 30 days
        val rent = Category(rentId, monthId, "Rent", 9000_00L, false, "icon", 0, true, Instant.now(), Instant.now())
        val fruits = Category(fruitsId, monthId, "Fruits", 1000_00L, true, "icon", 1, true, Instant.now(), Instant.now())

        val summary = BudgetEngine.calculateMonthSummary(
            month, listOf(rent, fruits), emptyList(), emptyList(), LocalDate.of(2026, 9, 2)
        )

        // Overall period 1 budget = Rent's period-1 share (210000) + Fruits' period-1 share.
        val fruitsPeriod1 = summary.categories.first { it.category.id == fruitsId }.periods[1].baseBudgetMinor
        assertEquals(210000L + fruitsPeriod1, summary.overallPeriods[1].baseBudgetMinor)

        // Total across all four overall periods must equal the combined monthly budget.
        assertEquals(10000_00L, summary.overallPeriods.sumOf { it.baseBudgetMinor })
    }

    @Test
    fun testCarryForwardCalculation() {
        val monthId = UUID.randomUUID()
        val catId = UUID.randomUUID()
        
        val month = BudgetMonth(monthId, 2026, 9, MonthStatus.ACTIVE, Instant.now(), null)
        val category = Category(catId, monthId, "Fruits", 1000_00L, true, "icon", 0, true, Instant.now(), Instant.now())
        
        // Let's add a carry forward of 500 from period 0 to period 1
        val carryForwards = listOf(
            BudgetCarryForward(UUID.randomUUID(), monthId, catId, 0, 1, 500_00L, Instant.now())
        )
        
        val summary = BudgetEngine.calculateMonthSummary(month, listOf(category), emptyList(), carryForwards, LocalDate.of(2026, 9, 1))
        
        val catSummary = summary.categories.first()
        
        // base budget for period 0 should be 266_66L or similar
        // effective budget should be base - 500_00L (cfOut)
        val p0Base = catSummary.periods[0].baseBudgetMinor
        assertEquals(p0Base - 500_00L, catSummary.periods[0].effectiveBudgetMinor)
        
        val p1Base = catSummary.periods[1].baseBudgetMinor
        assertEquals(p1Base + 500_00L, catSummary.periods[1].effectiveBudgetMinor)
    }

    @Test
    fun testCarryForwardToNonAdjacentPeriod() {
        // Carry-forward isn't hardcoded to "the next period" — this moves budget from period 0
        // all the way to period 3, skipping 1 and 2 entirely, which must be left untouched.
        val monthId = UUID.randomUUID()
        val catId = UUID.randomUUID()

        val month = BudgetMonth(monthId, 2026, 9, MonthStatus.ACTIVE, Instant.now(), null)
        val category = Category(catId, monthId, "Fruits", 1000_00L, true, "icon", 0, true, Instant.now(), Instant.now())

        val carryForwards = listOf(
            BudgetCarryForward(UUID.randomUUID(), monthId, catId, 0, 3, 500_00L, Instant.now())
        )

        val summary = BudgetEngine.calculateMonthSummary(month, listOf(category), emptyList(), carryForwards, LocalDate.of(2026, 9, 1))
        val catSummary = summary.categories.first()

        val p0Base = catSummary.periods[0].baseBudgetMinor
        assertEquals(p0Base - 500_00L, catSummary.periods[0].effectiveBudgetMinor)

        assertEquals(catSummary.periods[1].baseBudgetMinor, catSummary.periods[1].effectiveBudgetMinor)
        assertEquals(catSummary.periods[2].baseBudgetMinor, catSummary.periods[2].effectiveBudgetMinor)

        val p3Base = catSummary.periods[3].baseBudgetMinor
        assertEquals(p3Base + 500_00L, catSummary.periods[3].effectiveBudgetMinor)
    }

    @Test
    fun testCategoryRecalculationOnBudgetChange() {
        // Editing a category (here: doubling its budget) and recomputing must be reflected in
        // both the month total and that category's own period split — the exact recalculation
        // path CategoriesViewModel.updateCategory relies on for Home/Category Detail to update.
        val monthId = UUID.randomUUID()
        val catId = UUID.randomUUID()
        val month = BudgetMonth(monthId, 2026, 9, MonthStatus.ACTIVE, Instant.now(), null)

        // A budget that's a multiple of the day count (30) splits evenly across 8/7/8/7 with no
        // rounding remainder, so doubling it exactly doubles every period's share too.
        val original = Category(catId, monthId, "Groceries", 3000_00L, true, "icon", 0, true, Instant.now(), Instant.now())
        val before = BudgetEngine.calculateMonthSummary(month, listOf(original), emptyList(), emptyList(), LocalDate.of(2026, 9, 1))
        assertEquals(3000_00L, before.totalBudgetMinor)

        val edited = original.copy(monthlyBudgetMinor = 6000_00L)
        val after = BudgetEngine.calculateMonthSummary(month, listOf(edited), emptyList(), emptyList(), LocalDate.of(2026, 9, 1))
        assertEquals(6000_00L, after.totalBudgetMinor)

        // The period split scales with it too, not just the raw total.
        val beforePeriod0 = before.categories.first().periods[0].baseBudgetMinor
        val afterPeriod0 = after.categories.first().periods[0].baseBudgetMinor
        assertEquals(beforePeriod0 * 2, afterPeriod0)
    }

    @Test
    fun testSafeToSpendWithNoSpending() {
        // Sep 2026 (30 days) splits 8/7/8/7 — period 0 gets 8/30 of the budget. Confirmed by
        // testCategorySummaryNoWeeklyPacing's 900000 -> 240000 split; reused here.
        val monthId = UUID.randomUUID()
        val catId = UUID.randomUUID()
        val month = BudgetMonth(monthId, 2026, 9, MonthStatus.ACTIVE, Instant.now(), null)
        val category = Category(catId, monthId, "Rent", 9000_00L, false, "icon", 0, true, Instant.now(), Instant.now())

        val summary = BudgetEngine.calculateMonthSummary(month, listOf(category), emptyList(), emptyList(), LocalDate.of(2026, 9, 1))

        // Day 1 is within period 0 (days 1-8); nothing spent yet, so safe-to-spend is exactly
        // period 0's budget share.
        assertEquals(240000L, summary.safeToSpendMinor)
    }

    @Test
    fun testSafeToSpendIsZeroWhenCurrentPeriodOverspent() {
        val monthId = UUID.randomUUID()
        val catId = UUID.randomUUID()
        val month = BudgetMonth(monthId, 2026, 9, MonthStatus.ACTIVE, Instant.now(), null)
        val category = Category(catId, monthId, "Rent", 9000_00L, false, "icon", 0, true, Instant.now(), Instant.now())

        // Period 0 (days 1-8) has a 240000 share; spend more than that within period 0.
        val transactions = listOf(
            Transaction(
                id = UUID.randomUUID(), monthId = monthId, amountMinor = 300000L,
                currency = "INR", direction = TransactionDirection.DEBIT, categoryId = catId,
                transactionDateTime = null, transactionDate = LocalDate.of(2026, 9, 2),
                notificationReceivedAt = Instant.now(), bank = Bank.SBI, accountSuffix = null,
                recipient = null, sender = null, referenceNumber = null, sourcePackage = null,
                sourceSender = null, sourceMessageHash = null, duplicateKey = null,
                recordDecision = RecordDecision.RECORDED, syncState = SyncState.PENDING,
                parserVersion = null, createdAt = Instant.now(), updatedAt = Instant.now()
            )
        )

        val summary = BudgetEngine.calculateMonthSummary(month, listOf(category), transactions, emptyList(), LocalDate.of(2026, 9, 3))

        // Overspent the current period -> safe-to-spend floors at 0, never negative.
        assertEquals(0L, summary.safeToSpendMinor)
    }

    @Test
    fun testReassigningTransactionMovesSpendingBetweenCategories() {
        // The exact invariant CategoriesViewModel's move-transactions flow depends on: a
        // transaction's spend contributes only to whichever category it's currently assigned to.
        val monthId = UUID.randomUUID()
        val categoryAId = UUID.randomUUID()
        val categoryBId = UUID.randomUUID()
        val month = BudgetMonth(monthId, 2026, 9, MonthStatus.ACTIVE, Instant.now(), null)
        val categoryA = Category(categoryAId, monthId, "Groceries", 500000L, true, "icon", 0, true, Instant.now(), Instant.now())
        val categoryB = Category(categoryBId, monthId, "Dining", 500000L, true, "icon", 1, true, Instant.now(), Instant.now())

        fun transactionIn(categoryId: java.util.UUID) = Transaction(
            id = UUID.randomUUID(), monthId = monthId, amountMinor = 100000L,
            currency = "INR", direction = TransactionDirection.DEBIT, categoryId = categoryId,
            transactionDateTime = null, transactionDate = LocalDate.of(2026, 9, 5),
            notificationReceivedAt = Instant.now(), bank = Bank.SBI, accountSuffix = null,
            recipient = null, sender = null, referenceNumber = null, sourcePackage = null,
            sourceSender = null, sourceMessageHash = null, duplicateKey = null,
            recordDecision = RecordDecision.RECORDED, syncState = SyncState.PENDING,
            parserVersion = null, createdAt = Instant.now(), updatedAt = Instant.now()
        )

        val beforeMove = BudgetEngine.calculateMonthSummary(
            month, listOf(categoryA, categoryB), listOf(transactionIn(categoryAId)), emptyList(), LocalDate.of(2026, 9, 6)
        )
        assertEquals(100000L, beforeMove.categories.first { it.category.id == categoryAId }.totalSpentMinor)
        assertEquals(0L, beforeMove.categories.first { it.category.id == categoryBId }.totalSpentMinor)

        // Same transaction, reassigned to category B (as a "move transactions" action would do).
        val afterMove = BudgetEngine.calculateMonthSummary(
            month, listOf(categoryA, categoryB), listOf(transactionIn(categoryBId)), emptyList(), LocalDate.of(2026, 9, 6)
        )
        assertEquals(0L, afterMove.categories.first { it.category.id == categoryAId }.totalSpentMinor)
        assertEquals(100000L, afterMove.categories.first { it.category.id == categoryBId }.totalSpentMinor)
    }
}
