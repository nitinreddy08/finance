package com.budgetpace.app.domain.categorization

/**
 * The seam between "an expense was recorded" and "a notification asks what it was for".
 *
 * Ingestion (SMS receiver, notification listener) and the repositories both need to reach the
 * notification layer, but neither should depend on `android.app.NotificationManager`. Everything
 * here is safe to call from a background receiver: implementations must never throw, because a
 * failure to show or cancel a prompt must not lose the expense that was already written.
 */
interface CategorizationPrompts {

    /**
     * Asks what an expense was for. Implementations do nothing if notification permission is
     * missing or the expense already has a category — the caller does not have to check.
     */
    suspend fun show(transactionId: String)

    /**
     * Takes down the prompt for one expense. Called whenever the expense is categorized, ignored,
     * edited or deleted from anywhere else, so a stale prompt cannot sit in the shade offering
     * buttons that would overwrite what the owner just chose. Safe to call from non-suspend code.
     */
    fun cancel(transactionId: String)
}
