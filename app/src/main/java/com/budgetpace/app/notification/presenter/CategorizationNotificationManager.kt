package com.budgetpace.app.notification.presenter

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.budgetpace.app.MainActivity
import com.budgetpace.app.R
import com.budgetpace.app.core.model.RecordDecision
import com.budgetpace.app.core.security.appPrefs
import com.budgetpace.app.data.local.dao.CategoryDao
import com.budgetpace.app.data.local.dao.CategoryUsageRow
import com.budgetpace.app.data.local.dao.TransactionDao
import com.budgetpace.app.data.local.mapper.toDomain
import com.budgetpace.app.domain.categorization.CategorizationPrompts
import com.budgetpace.app.domain.categorization.CategoryUsage
import com.budgetpace.app.domain.categorization.PromptContentFactory
import com.budgetpace.app.domain.categorization.PromptUris
import com.budgetpace.app.domain.categorization.QuickCategoryRanker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spec §21: the categorization notification is "the most important UI" in Budget Pace — it must
 * let the owner categorize (or dismiss) a detected transaction in one tap, without opening the
 * app. Implements the [CategorizationPrompts] seam so ingestion and the repositories never touch
 * `android.app.NotificationManager` directly.
 *
 * Every prompt and its confirmation share one identity — `notify(tag = transactionId, id =
 * ID_PROMPT)` — so a categorize/ignore/undo tap always replaces the same slot instead of leaving
 * a second notification behind. A previous build kept one int id per transaction
 * (`transactionId.hashCode()`), which the [migrateLegacyNotificationIds] one-time cleanup clears
 * out on first run of this scheme so a stale, un-cancelable notification can't sit in the tray
 * forever.
 */
@Singleton
class CategorizationNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
) : CategorizationPrompts {

    // cancel() is called from non-suspend code (repositories, the Categories delete/reassign
    // flow), so the summary refresh it triggers needs a scope of its own rather than a
    // caller-supplied one.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Never allowed to throw: a failed migration must not take the whole app down.
        runCatching { migrateLegacyNotificationIds() }
    }

    companion object {
        const val CHANNEL_ID = "categorization"

        const val ACTION_CATEGORIZE = "com.budgetpace.app.action.CATEGORIZE"
        const val ACTION_DONT_RECORD = "com.budgetpace.app.action.DONT_RECORD"
        const val ACTION_UNDO_DONT_RECORD = "com.budgetpace.app.action.UNDO_DONT_RECORD"
        const val ACTION_PROMPT_DISMISSED = "com.budgetpace.app.action.PROMPT_DISMISSED"

        const val EXTRA_TRANSACTION_ID = "transactionId"
        const val EXTRA_CATEGORY_ID = "categoryId"

        // Every prompt and its confirmation use this fixed id with a per-transaction *tag* — the
        // pair is what notify()/cancel() treat as one slot. Fixed ids (never a hashCode) are also
        // what makes "count active prompts" in refreshSummary() meaningful.
        const val ID_PROMPT = 1001
        const val ID_SUMMARY = 1002

        private const val GROUP_KEY = "com.budgetpace.app.CATEGORIZATION_GROUP"
        private const val CONFIRMATION_TIMEOUT_MS = 4_000L
        private const val UNDO_LABEL = "Undo"

        private const val PREFS_FILE = "notification_migration"
        private const val KEY_LEGACY_IDS_CLEARED = "legacy_hashcode_ids_cleared_v1"
    }

    override suspend fun show(transactionId: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val entity = transactionDao.getById(transactionId) ?: return
        // No-op once the expense already has a category or is no longer awaiting a decision —
        // the caller (TransactionIngestor) does not have to check first.
        if (entity.categoryId != null) return
        if (entity.recordDecision != RecordDecision.RECORDED.name) return

        val transaction = entity.toDomain()
        val monthCategories = categoryDao.getByMonth(entity.monthId).map { it.toDomain() }
        val byPayee = transaction.recipient
            ?.let { transactionDao.categoryUsageForPayee(it) }
            ?.toUsage()
            ?: emptyList()
        val byMonth = transactionDao.categoryUsageThisMonth(entity.monthId).toUsage()
        val ranked = QuickCategoryRanker.rank(monthCategories, byPayee, byMonth)
        val content = PromptContentFactory.build(transaction, ranked)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.bigText))
            .setWhen(content.whenEpochMillis)
            .setShowWhen(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openTransactionIntent(transactionId))
            .setDeleteIntent(dismissedIntent(transactionId))

        content.quickActions.forEach { action ->
            builder.addAction(
                NotificationCompat.Action(
                    R.drawable.ic_stat_notification,
                    action.label,
                    categorizePendingIntent(transactionId, action.categoryId),
                )
            )
        }
        // Exactly three actions total (spec §21 / the bug this track fixes): up to two quick
        // categories plus this, never a fourth slot Android would silently drop.
        builder.addAction(
            NotificationCompat.Action(
                R.drawable.ic_stat_notification,
                PromptContentFactory.DONT_RECORD_LABEL,
                dontRecordPendingIntent(transactionId),
            )
        )

        NotificationManagerCompat.from(context).notify(content.tag, ID_PROMPT, builder.build())
        refreshSummary()
    }

    override fun cancel(transactionId: String) {
        NotificationManagerCompat.from(context).cancel(transactionId, ID_PROMPT)
        scope.launch { refreshSummary() }
    }

    /** Replaces the prompt in place with "$amount -> categoryName" (spec §21). */
    fun showCategorizedConfirmation(transactionId: String, amountMinor: Long, categoryName: String) {
        postConfirmation(
            transactionId = transactionId,
            text = PromptContentFactory.confirmationText(amountMinor, categoryName),
            showUndo = false,
        )
    }

    /** Replaces the prompt in place with "$amount not recorded" plus an Undo action. */
    fun showDontRecordConfirmation(transactionId: String, amountMinor: Long) {
        postConfirmation(
            transactionId = transactionId,
            text = PromptContentFactory.confirmationText(amountMinor, null),
            showUndo = true,
        )
    }

    /**
     * Recomputes the "N expenses need a category" summary from what's actually still posted —
     * never a DB count, which would drift the moment a confirmation is showing but hasn't timed
     * out yet. Public so [CategorizationActionReceiver] can call it after a swipe-dismiss, which
     * carries no category/ignore write of its own.
     */
    fun refreshSummary() {
        val manager = NotificationManagerCompat.from(context)
        val activeCount = manager.activeNotifications.count { it.id == ID_PROMPT }
        if (activeCount == 0) {
            manager.cancel(ID_SUMMARY)
            return
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(summaryTitle(activeCount))
            .setContentText(PromptContentFactory.QUESTION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setContentIntent(openExpensesIntent())
        manager.notify(ID_SUMMARY, builder.build())
    }

    private fun postConfirmation(transactionId: String, text: String, showUndo: Boolean) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(text)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(CONFIRMATION_TIMEOUT_MS)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setContentIntent(openTransactionIntent(transactionId))
        if (showUndo) {
            builder.addAction(
                NotificationCompat.Action(
                    R.drawable.ic_stat_notification,
                    UNDO_LABEL,
                    undoDontRecordPendingIntent(transactionId),
                )
            )
        }
        NotificationManagerCompat.from(context).notify(transactionId, ID_PROMPT, builder.build())
        refreshSummary()
    }

    private fun summaryTitle(count: Int): String =
        if (count == 1) "1 expense needs a category" else "$count expenses need a category"

    private fun openTransactionIntent(transactionId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse(PromptUris.transaction(transactionId))
            putExtra(MainActivity.EXTRA_TRANSACTION_ID, transactionId)
        }
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openExpensesIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_EXPENSES)
        }
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // Each PendingIntent below shares component + action with every other transaction's, so the
    // `data` URI is what keeps FLAG_UPDATE_CURRENT from rewriting the wrong transaction's action
    // (PendingIntent identity ignores extras — see PromptUris's kdoc).

    private fun categorizePendingIntent(transactionId: String, categoryId: String): PendingIntent {
        val intent = Intent(context, CategorizationActionReceiver::class.java).apply {
            action = ACTION_CATEGORIZE
            data = Uri.parse(PromptUris.categorize(transactionId, categoryId))
            putExtra(EXTRA_TRANSACTION_ID, transactionId)
            putExtra(EXTRA_CATEGORY_ID, categoryId)
        }
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun dontRecordPendingIntent(transactionId: String): PendingIntent {
        val intent = Intent(context, CategorizationActionReceiver::class.java).apply {
            action = ACTION_DONT_RECORD
            data = Uri.parse(PromptUris.dontRecord(transactionId))
            putExtra(EXTRA_TRANSACTION_ID, transactionId)
        }
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun undoDontRecordPendingIntent(transactionId: String): PendingIntent {
        val intent = Intent(context, CategorizationActionReceiver::class.java).apply {
            action = ACTION_UNDO_DONT_RECORD
            data = Uri.parse("budgetpace://undo-dont-record/$transactionId")
            putExtra(EXTRA_TRANSACTION_ID, transactionId)
        }
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun dismissedIntent(transactionId: String): PendingIntent {
        val intent = Intent(context, CategorizationActionReceiver::class.java).apply {
            action = ACTION_PROMPT_DISMISSED
            data = Uri.parse("budgetpace://prompt-dismissed/$transactionId")
            putExtra(EXTRA_TRANSACTION_ID, transactionId)
        }
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * The previous build's ids were `transactionId.hashCode()` (and
     * `(transactionId + categoryId).hashCode()` for actions) — unrelated to [ID_PROMPT] /
     * [ID_SUMMARY], so nothing here would ever be able to address and cancel them again. A single
     * `cancelAll()` the first time this build runs clears any such leftovers out of the tray; the
     * flag file guarantees it only happens once.
     */
    private fun migrateLegacyNotificationIds() {
        val prefs = appPrefs(context, PREFS_FILE)
        if (prefs.getBoolean(KEY_LEGACY_IDS_CLEARED, false)) return
        NotificationManagerCompat.from(context).cancelAll()
        prefs.edit().putBoolean(KEY_LEGACY_IDS_CLEARED, true).apply()
    }

    private fun List<CategoryUsageRow>.toUsage(): List<CategoryUsage> =
        map { CategoryUsage(it.categoryId, it.count) }
}
