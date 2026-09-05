package com.budgetpace.app.domain.categorization

import com.budgetpace.app.core.model.Category

/**
 * How often one category was chosen, as counted by a DAO query. [categoryId] is the category's
 * UUID rendered with `toString()` so the pure ranker never has to know about UUIDs.
 */
data class CategoryUsage(val categoryId: String, val count: Int)

/**
 * Picks which categories get the notification's one-tap buttons (spec section 21).
 *
 * Order: what the owner chose for this same payee before, then what they use most this month,
 * then their own sort order. The first key is what makes the prompt feel like it already knows
 * the answer - a second Zepto charge offers the category the first one got.
 */
object QuickCategoryRanker {

    /**
     * @param categories all of the month's categories, in any order.
     * @param byPayee how often each category was used for *this* transaction's payee.
     * @param byMonth how often each category was used anywhere this month.
     */
    fun rank(
        categories: List<Category>,
        byPayee: List<CategoryUsage>,
        byMonth: List<CategoryUsage>
    ): List<Category> {
        val payeeCounts = countsById(byPayee)
        val monthCounts = countsById(byMonth)
        return categories
            .filter { it.active && !isLumpSum(it) }
            .sortedWith(
                compareByDescending<Category> { payeeCounts[it.id.toString()] ?: 0 }
                    .thenByDescending { monthCounts[it.id.toString()] ?: 0 }
                    .thenBy { it.sortOrder }
            )
    }

    /**
     * A lump-sum category (Rent, school fees) holds money the owner has already committed, so it
     * must never sit under a thumb on the lock screen: one mis-tap would charge an ad-hoc expense
     * against that money, and the safe-to-spend model depends on it staying committed. Such
     * categories stay available in the in-app chooser, where the choice is deliberate.
     *
     * A periodCount below 1 is corrupt data rather than a paced category, so it is excluded too.
     */
    private fun isLumpSum(category: Category): Boolean = category.periodCount <= 1

    // Summed, not last-wins: a usage query may return one row per payee variant for the same
    // category, and silently keeping only the last row would understate a frequent choice.
    private fun countsById(usage: List<CategoryUsage>): Map<String, Int> =
        usage.groupingBy { it.categoryId }.fold(0) { total, row -> total + row.count }
}
