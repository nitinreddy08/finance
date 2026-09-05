package com.budgetpace.app.domain.categorization

import com.budgetpace.app.core.model.Bank
import com.budgetpace.app.core.model.Category
import com.budgetpace.app.core.model.Transaction
import com.budgetpace.app.core.money.Money

/**
 * One one-tap category button. [dataUri] is what makes its PendingIntent distinct - see
 * [PromptUris].
 */
data class QuickAction(
    val categoryId: String,
    val label: String,
    val dataUri: String
)

/**
 * Everything the categorization notification shows, resolved from the transaction alone so the
 * Android presenter only maps fields onto NotificationCompat.
 *
 * [tag] is the notification tag (`notify(tag, id)`); it is the transaction id, because the old
 * `transactionId.hashCode()` int id collides between two transactions and the loser silently
 * replaces the winner's prompt.
 */
data class PromptContent(
    val tag: String,
    val title: String,
    val text: String,
    val bigText: String,
    val whenEpochMillis: Long,
    val quickActions: List<QuickAction>
)

/**
 * Deep-link URIs used as the `data` of every PendingIntent this prompt creates.
 *
 * PendingIntent identity ignores extras: two intents for the same component and action are "equal"
 * even when their transaction ids differ, so `FLAG_UPDATE_CURRENT` would rewrite the first
 * prompt's action to point at the second transaction. A distinct data URI per action is what keeps
 * them apart.
 */
object PromptUris {

    private const val SCHEME = "budgetpace://"

    fun categorize(transactionId: String, categoryId: String): String =
        "${SCHEME}categorize/$transactionId/$categoryId"

    fun dontRecord(transactionId: String): String = "${SCHEME}dont-record/$transactionId"

    fun transaction(transactionId: String): String = "${SCHEME}transaction/$transactionId"
}

/**
 * Builds the prompt's copy (spec section 21). Pure so every wording and truncation decision is
 * covered by the harness instead of only by a phone.
 */
object PromptContentFactory {

    /**
     * Android renders at most three notification actions and the third slot is permanently
     * "Don't record" (spec section 20 requires it to always be reachable). The previous code took
     * three categories *and* appended "Don't record", so from three categories onwards the
     * platform dropped it and the owner had no way to dismiss a transaction from the prompt.
     */
    const val MAX_QUICK_CATEGORIES: Int = 2

    const val QUESTION: String = "What was this for?"
    const val BIG_TEXT_HINT: String = "Tap to see all categories."
    const val DONT_RECORD_LABEL: String = "Don't record"

    // Written as escapes so the rendered symbol can never be mangled by an editor or a diff.
    private const val SEPARATOR = " \u00B7 "
    private const val ACCOUNT_MASK = "\u2022\u2022\u2022"
    private const val CHECK_MARK = "\u2713"
    private const val ARROW = "\u2192"

    fun build(transaction: Transaction, quickCategories: List<Category>): PromptContent {
        val transactionId = transaction.id.toString()
        val bankLine = bankLine(transaction.bank, transaction.accountSuffix)
        val text = if (bankLine.isEmpty()) QUESTION else "$QUESTION$SEPARATOR$bankLine"
        return PromptContent(
            tag = transactionId,
            title = title(transaction.amountMinor, transaction.recipient),
            text = text,
            bigText = "$text\n$BIG_TEXT_HINT",
            // The bank's own timestamp when we have it: a prompt posted after a delayed delivery
            // must not claim the expense happened just now.
            whenEpochMillis = (transaction.transactionDateTime ?: transaction.notificationReceivedAt)
                .toEpochMilli(),
            quickActions = quickCategories.take(MAX_QUICK_CATEGORIES).map { category ->
                val categoryId = category.id.toString()
                QuickAction(
                    categoryId = categoryId,
                    label = actionLabel(category),
                    dataUri = PromptUris.categorize(transactionId, categoryId)
                )
            }
        )
    }

    fun title(amountMinor: Long, recipient: String?): String {
        val amount = Money.formatRupees(amountMinor)
        return if (recipient.isNullOrBlank()) "$amount spent" else "$amount to $recipient"
    }

    /**
     * Never emoji-only: TalkBack announces a bare emoji by its Unicode name ("red apple"), which
     * tells the owner nothing about which budget the tap charges.
     */
    fun actionLabel(category: Category): String =
        if (isEmojiIcon(category.iconKey)) "${category.iconKey} ${category.name}" else category.name

    /**
     * Explicit map rather than `name.lowercase().replaceFirstChar { }`, which renders SBI as
     * "Sbi". An UNKNOWN bank contributes nothing: "Unknown ...7970" would read as a
     * bank called Unknown.
     */
    fun shortBankName(bank: Bank): String = when (bank) {
        Bank.KOTAK -> "Kotak"
        Bank.SBI -> "SBI"
        Bank.UNKNOWN -> ""
    }

    /** "Kotak \u2022\u2022\u20227970", or either half alone, or "" when neither is known. */
    fun bankLine(bank: Bank, accountSuffix: String?): String {
        val parts = listOfNotNull(
            shortBankName(bank).takeIf { it.isNotEmpty() },
            accountSuffix?.takeIf { it.isNotBlank() }?.let { "$ACCOUNT_MASK$it" }
        )
        return parts.joinToString(" ")
    }

    /**
     * Replaces the prompt in place once the owner has acted (spec section 21). Whole rupees: the
     * paise are noise in a confirmation that disappears after four seconds.
     */
    fun confirmationText(amountMinor: Long, categoryName: String?): String {
        val amount = Money.formatRupeesWhole(amountMinor)
        return if (categoryName == null) {
            "$CHECK_MARK $amount not recorded"
        } else {
            "$CHECK_MARK $amount $ARROW $categoryName"
        }
    }
}
