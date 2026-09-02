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
        
        // No weekly pacing means entire budget goes to period 0
        assertEquals(9000_00L, catSummary.periods[0].baseBudgetMinor)
        assertEquals(0L, catSummary.periods[1].baseBudgetMinor)
        
        // Spent should be registered
        assertEquals(9000_00L, catSummary.totalSpentMinor)
        assertEquals(0L, catSummary.remainingMinor)
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
}
