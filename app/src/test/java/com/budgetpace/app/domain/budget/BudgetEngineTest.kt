package com.budgetpace.app.domain.budget

import com.budgetpace.app.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * September 2026 has 30 days. The month-level grid is 1-8 / 9-15 / 16-23 / 24-30; a 2-period
 * category splits 1-15 / 16-30 and a 3-period one 1-10 / 11-20 / 21-30.
 */
class BudgetEngineTest {

    private val monthId = UUID.randomUUID()
    private val month = BudgetMonth(monthId, 2026, 9, MonthStatus.ACTIVE, Instant.now(), null)

    private fun category(
        name: String,
        budgetMinor: Long,
        periodCount: Int,
        id: UUID = UUID.randomUUID(),
        sortOrder: Int = 0,
    ) = Category(id, monthId, name, budgetMinor, periodCount, "icon", sortOrder, true, Instant.now(), Instant.now())

    private fun debit(categoryId: UUID?, amountMinor: Long, day: Int, monthValue: Int = 9) = Transaction(
        id = UUID.randomUUID(), monthId = monthId, amountMinor = amountMinor,
        currency = "INR", direction = TransactionDirection.DEBIT, categoryId = categoryId,
        transactionDateTime = null, transactionDate = LocalDate.of(2026, monthValue, day),
        notificationReceivedAt = Instant.now(), bank = Bank.SBI, accountSuffix = null,
        recipient = null, sender = null, referenceNumber = null, sourcePackage = null,
        sourceSender = null, sourceMessageHash = null, duplicateKey = null,
        recordDecision = RecordDecision.RECORDED, syncState = SyncState.PENDING,
        parserVersion = null, createdAt = Instant.now(), updatedAt = Instant.now()
    )

    private fun summarize(
        categories: List<Category>,
        transactions: List<Transaction> = emptyList(),
        carryForwards: List<BudgetCarryForward> = emptyList(),
        day: Int,
        monthValue: Int = 9,
        year: Int = 2026,
    ) = BudgetEngine.calculateMonthSummary(
        month, categories, transactions, carryForwards, LocalDate.of(year, monthValue, day)
    )

    // ── Safe to spend across every period ────────────────────────────────────────

    @Test
    fun twoPeriodCategoryHasHeadroomInEveryGlobalPeriod() {
        // The old model attributed a category period's whole budget to the global period holding
        // its start date, so global periods 1 and 3 got nothing and Home showed 0 for whole weeks.
        val utilities = category("Utilities", 2000_00L, 2)

        listOf(1, 10, 16, 25).forEach { day ->
            val summary = summarize(listOf(utilities), day = day)
            assertEquals("day $day", 1000_00L, summary.safeToSpendMinor)
            assertEquals("day $day", 0L, summary.overPaceMinor)
        }
    }

    @Test
    fun threePeriodCategoryHasHeadroomInEveryGlobalPeriod() {
        val groceries = category("Groceries", 3000_00L, 3)

        listOf(1, 10, 12, 20, 25, 30).forEach { day ->
            val summary = summarize(listOf(groceries), day = day)
            assertEquals("day $day", 1000_00L, summary.safeToSpendMinor)
        }
    }

    @Test
    fun earlierOverspendIsRepaidOnceNotEveryPeriod() {
        // 1000 over 4 periods = 250 each. Spending 500 in period 0 leaves 500 for the month.
        val fruits = category("Fruits", 1000_00L, 4)
        val spend = listOf(debit(fruits.id, 500_00L, 2))

        assertEquals(0L, summarize(listOf(fruits), spend, day = 2).safeToSpendMinor)
        // Period 1 absorbs the 250 debt, so its own 250 is gone but period 2 onwards is clear.
        assertEquals(0L, summarize(listOf(fruits), spend, day = 10).safeToSpendMinor)
        assertEquals(250_00L, summarize(listOf(fruits), spend, day = 20).safeToSpendMinor)
        assertEquals(250_00L, summarize(listOf(fruits), spend, day = 28).safeToSpendMinor)
    }

    @Test
    fun smallerEarlierOverspendIsAbsorbedByTheNextPeriodAlone() {
        val fruits = category("Fruits", 1000_00L, 4)
        val spend = listOf(debit(fruits.id, 400_00L, 2))

        // 150 of debt carried into period 1, which has 250 of its own.
        assertEquals(100_00L, summarize(listOf(fruits), spend, day = 10).safeToSpendMinor)
        assertEquals(250_00L, summarize(listOf(fruits), spend, day = 20).safeToSpendMinor)
    }

    @Test
    fun safeToSpendNeverExceedsWhatIsLeftForTheMonth() {
        val fruits = category("Fruits", 1000_00L, 4)
        val spend = listOf(debit(fruits.id, 900_00L, 2))

        listOf(1, 8, 15, 22, 29).forEach { day ->
            val summary = summarize(listOf(fruits), spend, day = day)
            assertTrue(
                "day $day: safe ${summary.safeToSpendMinor} > remaining ${summary.remainingMinor}",
                summary.safeToSpendMinor <= summary.remainingMinor
            )
        }
    }

    @Test
    fun overPaceAndSafeToSpendAreNeverBothSet() {
        val fruits = category("Fruits", 1000_00L, 4)
        val summary = summarize(listOf(fruits), listOf(debit(fruits.id, 600_00L, 2)), day = 3)

        assertEquals(0L, summary.safeToSpendMinor)
        assertEquals(350_00L, summary.overPaceMinor)
    }

    // ── Lump sums are earmarked, not spare ───────────────────────────────────────

    @Test
    fun lumpSumBudgetIsNeverOfferedAsSafeToSpend() {
        // Rent's money is committed the moment the user chooses "spend at start of month".
        val rent = category("Rent", 9000_00L, 1)
        val summary = summarize(listOf(rent), day = 1)

        assertEquals(0L, summary.safeToSpendMinor)
        assertEquals(0L, summary.overPaceMinor)
        assertEquals(BudgetStatus.GREEN, summary.categories.first().overallStatus)
    }

    @Test
    fun lumpSumDoesNotInflateLaterPeriods() {
        val rent = category("Rent", 9000_00L, 1, sortOrder = 0)
        val fruits = category("Fruits", 1000_00L, 4, sortOrder = 1)

        listOf(2, 10, 20, 28).forEach { day ->
            val summary = summarize(listOf(rent, fruits), day = day)
            assertEquals("day $day", 250_00L, summary.safeToSpendMinor)
        }
    }

    @Test
    fun payingRentOnDayOneIsOnTrack() {
        // The owner's own setup: rent 9,000 of a 14,500 total, paid on the 1st.
        val rent = category("Rent", 9000_00L, 1, sortOrder = 0)
        val food = category("Food", 5500_00L, 4, sortOrder = 1)
        val summary = summarize(listOf(rent, food), listOf(debit(rent.id, 9000_00L, 1)), day = 2)

        assertEquals(BudgetStatus.GREEN, summary.overallStatus)
        assertEquals(BudgetStatus.GREEN, summary.categories.first { it.category.id == rent.id }.overallStatus)
        assertEquals(9000_00L, summary.categories.first { it.category.id == rent.id }.expectedToDateMinor)
    }

    @Test
    fun lumpSumOverspendIsChargedOnceThenLeftToTheMonthlyCap() {
        val rent = category("Rent", 9000_00L, 1, sortOrder = 0)
        val fruits = category("Fruits", 1000_00L, 4, sortOrder = 1)
        val spend = listOf(debit(rent.id, 9100_00L, 2))

        // The 100 overspend lands in period 0 and is deducted there.
        assertEquals(150_00L, summarize(listOf(rent, fruits), spend, day = 3).safeToSpendMinor)
        // Later periods do not pay for it again; the monthly cap already reflects it.
        assertEquals(250_00L, summarize(listOf(rent, fruits), spend, day = 20).safeToSpendMinor)
    }

    // ── Pace status over time ────────────────────────────────────────────────────

    @Test
    fun blowingTheBudgetInWeekOneGoesRedThenEasesBack() {
        val fruits = category("Fruits", 1000_00L, 4)
        val spend = listOf(debit(fruits.id, 1000_00L, 2))

        fun statusOn(day: Int) = summarize(listOf(fruits), spend, day = day).categories.first().overallStatus

        assertEquals(BudgetStatus.RED, statusOn(2))
        assertEquals(BudgetStatus.RED, statusOn(15))
        // By the last day the whole budget is expected to have been spent, so it reads on track.
        assertEquals(BudgetStatus.GREEN, statusOn(30))
    }

    @Test
    fun uncategorizedSpendCountsAgainstThePlan() {
        val fruits = category("Fruits", 1000_00L, 4)
        val spend = listOf(debit(null, 100_00L, 2))

        // No category owns it, so it is pure overspend while its period is current...
        assertEquals(150_00L, summarize(listOf(fruits), spend, day = 3).safeToSpendMinor)
        // ...and afterwards only the monthly cap carries it.
        assertEquals(250_00L, summarize(listOf(fruits), spend, day = 10).safeToSpendMinor)
        assertEquals(100_00L, summarize(listOf(fruits), spend, day = 10).totalSpentMinor)
    }

    // ── Months other than the one being viewed ───────────────────────────────────

    @Test
    fun archivedMonthOffersNothingToSpend() {
        val fruits = category("Fruits", 1000_00L, 4)
        val summary = summarize(listOf(fruits), day = 4, monthValue = 10)

        assertEquals(0L, summary.safeToSpendMinor)
        assertEquals(0L, summary.overPaceMinor)
        assertTrue(summary.overallPeriods.all { it.periodStatus == PeriodStatus.COMPLETED })
        assertNull(summary.categories.first().currentPeriodIndex)
    }

    @Test
    fun futureMonthIsGreyAndOffersNothing() {
        val fruits = category("Fruits", 1000_00L, 4)
        val summary = summarize(listOf(fruits), day = 20, monthValue = 8)

        assertEquals(0L, summary.safeToSpendMinor)
        assertEquals(BudgetStatus.GREY, summary.overallStatus)
        assertEquals(BudgetStatus.GREY, summary.categories.first().overallStatus)
        assertTrue(summary.overallPeriods.all { it.periodStatus == PeriodStatus.UPCOMING })
    }

    @Test
    fun monthEndsAndTheHeadlineCollapses() {
        val fruits = category("Fruits", 1000_00L, 4)
        // Sep 30 still has a current period; Oct 1 does not.
        assertEquals(250_00L, summarize(listOf(fruits), day = 30).safeToSpendMinor)
        assertEquals(0L, summarize(listOf(fruits), day = 1, monthValue = 10).safeToSpendMinor)
    }

    @Test
    fun transactionDatedInAnotherMonthStillLandsInThisMonthsFirstPeriod() {
        // A 31 August SMS delivered after midnight can be filed under September; it must not
        // vanish from the period breakdown, or the period spends stop adding up to the total.
        val fruits = category("Fruits", 1000_00L, 4)
        val summary = summarize(listOf(fruits), listOf(debit(fruits.id, 100_00L, 31, monthValue = 8)), day = 10)

        assertEquals(100_00L, summary.totalSpentMinor)
        assertEquals(100_00L, summary.overallPeriods.sumOf { it.spentMinor })
        assertEquals(100_00L, summary.overallPeriods[0].spentMinor)
        assertEquals(100_00L, summary.categories.first().periods[0].spentMinor)
    }

    // ── Carry-forward ────────────────────────────────────────────────────────────

    @Test
    fun carryForwardMovesBudgetBetweenPeriods() {
        val fruits = category("Fruits", 1000_00L, 4)
        val carry = listOf(BudgetCarryForward(UUID.randomUUID(), monthId, fruits.id, 0, 1, 100_00L, Instant.now()))
        val periods = summarize(listOf(fruits), carryForwards = carry, day = 1).categories.first().periods

        assertEquals(150_00L, periods[0].effectiveBudgetMinor)
        assertEquals(350_00L, periods[1].effectiveBudgetMinor)
        assertEquals(1000_00L, periods.sumOf { it.effectiveBudgetMinor })
    }

    @Test
    fun carryForwardCanSkipPeriods() {
        val fruits = category("Fruits", 1000_00L, 4)
        val carry = listOf(BudgetCarryForward(UUID.randomUUID(), monthId, fruits.id, 0, 3, 100_00L, Instant.now()))
        val periods = summarize(listOf(fruits), carryForwards = carry, day = 1).categories.first().periods

        assertEquals(150_00L, periods[0].effectiveBudgetMinor)
        assertEquals(250_00L, periods[1].effectiveBudgetMinor)
        assertEquals(250_00L, periods[2].effectiveBudgetMinor)
        assertEquals(350_00L, periods[3].effectiveBudgetMinor)
    }

    @Test
    fun carryForwardIsCappedAtWhatTheSourcePeriodActuallyHas() {
        // Chained moves: the second one may draw on what the first one brought in.
        val fruits = category("Fruits", 1000_00L, 4)
        val first = Instant.parse("2026-09-01T10:00:00Z")
        val carry = listOf(
            BudgetCarryForward(UUID.randomUUID(), monthId, fruits.id, 0, 1, 200_00L, first),
            BudgetCarryForward(UUID.randomUUID(), monthId, fruits.id, 1, 2, 400_00L, first.plusSeconds(60)),
        )
        val periods = summarize(listOf(fruits), carryForwards = carry, day = 1).categories.first().periods

        assertEquals(50_00L, periods[0].effectiveBudgetMinor)
        assertEquals(50_00L, periods[1].effectiveBudgetMinor)
        assertEquals(650_00L, periods[2].effectiveBudgetMinor)
        assertEquals(250_00L, periods[3].effectiveBudgetMinor)
        assertEquals(1000_00L, periods.sumOf { it.effectiveBudgetMinor })
    }

    @Test
    fun carryForwardPointingOutsideThePeriodListIsIgnoredWhole() {
        // The category was edited from 4 periods down to 2; the stored record's target no longer
        // exists. Dropping only the incoming leg would make budget disappear from the month.
        val utilities = category("Utilities", 1000_00L, 2)
        val carry = listOf(BudgetCarryForward(UUID.randomUUID(), monthId, utilities.id, 0, 3, 100_00L, Instant.now()))
        val periods = summarize(listOf(utilities), carryForwards = carry, day = 1).categories.first().periods

        assertEquals(500_00L, periods[0].effectiveBudgetMinor)
        assertEquals(500_00L, periods[1].effectiveBudgetMinor)
        assertEquals(1000_00L, periods.sumOf { it.effectiveBudgetMinor })
    }

    @Test
    fun effectiveBudgetsAlwaysSumToTheMonthlyBudget() {
        val fruits = category("Fruits", 1000_00L, 4)
        val carry = listOf(
            BudgetCarryForward(UUID.randomUUID(), monthId, fruits.id, 0, 1, 900_00L, Instant.now()),
            BudgetCarryForward(UUID.randomUUID(), monthId, fruits.id, 2, 1, 900_00L, Instant.now()),
        )
        val periods = summarize(listOf(fruits), carryForwards = carry, day = 1).categories.first().periods

        assertEquals(1000_00L, periods.sumOf { it.effectiveBudgetMinor })
        assertTrue(periods.all { it.effectiveBudgetMinor >= 0L })
    }

    // ── Structure that other layers depend on ────────────────────────────────────

    @Test
    fun lumpSumHasOnePeriodHoldingTheWholeBudget() {
        val rent = category("Rent", 9000_00L, 1)
        val summary = summarize(listOf(rent), listOf(debit(rent.id, 9000_00L, 1)), day = 2)
        val catSummary = summary.categories.first()

        assertEquals(1, catSummary.periods.size)
        assertEquals(9000_00L, catSummary.periods[0].baseBudgetMinor)
        assertEquals(9000_00L, catSummary.totalSpentMinor)
        assertEquals(0L, catSummary.remainingMinor)
    }

    @Test
    fun categoryUsesItsOwnPeriodGridNotTheGlobalOne() {
        val utilities = category("Utilities", 2000_00L, 2)
        val catSummary = summarize(listOf(utilities), day = 1).categories.first()

        assertEquals(2, catSummary.periods.size)
        assertEquals(1000_00L, catSummary.periods[0].baseBudgetMinor)
        assertEquals(1000_00L, catSummary.periods[1].baseBudgetMinor)
        assertEquals(LocalDate.of(2026, 9, 1), catSummary.periods[0].startDate)
        assertEquals(LocalDate.of(2026, 9, 16), catSummary.periods[1].startDate)
        assertEquals(0, catSummary.currentPeriodIndex)
    }

    @Test
    fun categoryCurrentPeriodFollowsItsOwnGrid() {
        val utilities = category("Utilities", 2000_00L, 2)
        // Day 20 is global period 2 but the category's own period 1.
        assertEquals(1, summarize(listOf(utilities), day = 20).categories.first().currentPeriodIndex)
    }

    @Test
    fun overallPeriodsCarryOnlySpending() {
        val fruits = category("Fruits", 1000_00L, 4)
        val summary = summarize(listOf(fruits), listOf(debit(fruits.id, 100_00L, 10)), day = 10)

        assertEquals(4, summary.overallPeriods.size)
        assertEquals(0L, summary.overallPeriods[0].spentMinor)
        assertEquals(100_00L, summary.overallPeriods[1].spentMinor)
        assertTrue(summary.overallPeriods[1].isCurrentPeriod)
        assertEquals(PeriodStatus.COMPLETED, summary.overallPeriods[0].periodStatus)
        assertEquals(PeriodStatus.UPCOMING, summary.overallPeriods[3].periodStatus)
    }

    @Test
    fun editingABudgetRecalculatesTheSplit() {
        val original = category("Groceries", 3000_00L, 4)
        val before = summarize(listOf(original), day = 1)
        assertEquals(3000_00L, before.totalBudgetMinor)

        val edited = original.copy(monthlyBudgetMinor = 6000_00L)
        val after = summarize(listOf(edited), day = 1)
        assertEquals(6000_00L, after.totalBudgetMinor)
        assertEquals(
            before.categories.first().periods[0].baseBudgetMinor * 2,
            after.categories.first().periods[0].baseBudgetMinor
        )
    }

    @Test
    fun reassigningATransactionMovesSpendingBetweenCategories() {
        val groceries = category("Groceries", 5000_00L, 4, sortOrder = 0)
        val dining = category("Dining", 5000_00L, 4, sortOrder = 1)

        val before = summarize(listOf(groceries, dining), listOf(debit(groceries.id, 1000_00L, 5)), day = 6)
        assertEquals(1000_00L, before.categories.first { it.category.id == groceries.id }.totalSpentMinor)
        assertEquals(0L, before.categories.first { it.category.id == dining.id }.totalSpentMinor)

        val after = summarize(listOf(groceries, dining), listOf(debit(dining.id, 1000_00L, 5)), day = 6)
        assertEquals(0L, after.categories.first { it.category.id == groceries.id }.totalSpentMinor)
        assertEquals(1000_00L, after.categories.first { it.category.id == dining.id }.totalSpentMinor)
    }
}
