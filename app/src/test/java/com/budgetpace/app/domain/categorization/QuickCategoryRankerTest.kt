package com.budgetpace.app.domain.categorization

import com.budgetpace.app.core.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class QuickCategoryRankerTest {

    private val monthId = UUID.fromString("00000000-0000-4000-8000-0000000000ff")

    private fun category(
        name: String,
        sortOrder: Int,
        periodCount: Int = 4,
        active: Boolean = true,
        id: UUID = UUID.nameUUIDFromBytes(name.toByteArray())
    ): Category = Category(
        id = id,
        monthId = monthId,
        name = name,
        monthlyBudgetMinor = 500_000L,
        periodCount = periodCount,
        iconKey = "default",
        sortOrder = sortOrder,
        active = active,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun usage(category: Category, count: Int) = CategoryUsage(category.id.toString(), count)

    @Test
    fun `falls back to sort order with no usage history`() {
        val misc = category("Misc", sortOrder = 3)
        val fruits = category("Fruits", sortOrder = 1)
        val protein = category("Protein", sortOrder = 2)

        val ranked = QuickCategoryRanker.rank(listOf(misc, fruits, protein), emptyList(), emptyList())

        assertEquals(listOf("Fruits", "Protein", "Misc"), ranked.map { it.name })
    }

    @Test
    fun `payee history outranks month usage and sort order`() {
        val fruits = category("Fruits", sortOrder = 1)
        val protein = category("Protein", sortOrder = 2)
        val misc = category("Misc", sortOrder = 3)

        val ranked = QuickCategoryRanker.rank(
            categories = listOf(fruits, protein, misc),
            byPayee = listOf(usage(misc, 2)),
            byMonth = listOf(usage(protein, 9), usage(fruits, 4))
        )

        assertEquals(listOf("Misc", "Protein", "Fruits"), ranked.map { it.name })
    }

    @Test
    fun `higher payee count wins over lower payee count`() {
        val fruits = category("Fruits", sortOrder = 1)
        val protein = category("Protein", sortOrder = 2)

        val ranked = QuickCategoryRanker.rank(
            categories = listOf(fruits, protein),
            byPayee = listOf(usage(fruits, 1), usage(protein, 6)),
            byMonth = emptyList()
        )

        assertEquals(listOf("Protein", "Fruits"), ranked.map { it.name })
    }

    @Test
    fun `month usage breaks a payee tie`() {
        val fruits = category("Fruits", sortOrder = 1)
        val protein = category("Protein", sortOrder = 2)

        val ranked = QuickCategoryRanker.rank(
            categories = listOf(fruits, protein),
            byPayee = emptyList(),
            byMonth = listOf(usage(protein, 3))
        )

        assertEquals(listOf("Protein", "Fruits"), ranked.map { it.name })
    }

    @Test
    fun `repeated usage rows for one category are summed`() {
        val fruits = category("Fruits", sortOrder = 1)
        val protein = category("Protein", sortOrder = 2)

        val ranked = QuickCategoryRanker.rank(
            categories = listOf(fruits, protein),
            byPayee = emptyList(),
            byMonth = listOf(usage(fruits, 2), usage(fruits, 3), usage(protein, 4))
        )

        assertEquals(listOf("Fruits", "Protein"), ranked.map { it.name })
    }

    @Test
    fun `inactive categories are excluded`() {
        val fruits = category("Fruits", sortOrder = 1)
        val retired = category("Old Gym", sortOrder = 0, active = false)

        val ranked = QuickCategoryRanker.rank(
            categories = listOf(retired, fruits),
            byPayee = listOf(usage(retired, 20)),
            byMonth = listOf(usage(retired, 20))
        )

        assertEquals(listOf("Fruits"), ranked.map { it.name })
    }

    @Test
    fun `lump sum categories are never offered as a quick action`() {
        // Rent is earmarked money: one mis-tap from the lock screen would charge an ad-hoc
        // expense against it, so no amount of usage history may promote it into the prompt.
        val rent = category("Rent", sortOrder = 0, periodCount = 1)
        val fruits = category("Fruits", sortOrder = 5)

        val ranked = QuickCategoryRanker.rank(
            categories = listOf(rent, fruits),
            byPayee = listOf(usage(rent, 50)),
            byMonth = listOf(usage(rent, 50))
        )

        assertEquals(listOf("Fruits"), ranked.map { it.name })
        assertFalse(ranked.any { it.periodCount == 1 })
    }

    @Test
    fun `a corrupt zero period count is treated as lump sum`() {
        val broken = category("Broken", sortOrder = 0, periodCount = 0)

        assertTrue(QuickCategoryRanker.rank(listOf(broken), emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `usage for an unknown category id is ignored`() {
        val fruits = category("Fruits", sortOrder = 1)

        val ranked = QuickCategoryRanker.rank(
            categories = listOf(fruits),
            byPayee = listOf(CategoryUsage(UUID.randomUUID().toString(), 99)),
            byMonth = emptyList()
        )

        assertEquals(listOf("Fruits"), ranked.map { it.name })
    }

    @Test
    fun `no categories yields an empty ranking`() {
        assertTrue(QuickCategoryRanker.rank(emptyList(), emptyList(), emptyList()).isEmpty())
    }
}
