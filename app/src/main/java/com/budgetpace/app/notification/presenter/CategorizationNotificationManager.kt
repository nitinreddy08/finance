package com.budgetpace.app.notification.presenter

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.budgetpace.app.MainActivity
import com.budgetpace.app.R
import com.budgetpace.app.core.model.Category
import com.budgetpace.app.core.model.Transaction
import com.budgetpace.app.core.money.Money
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spec §21: the categorization notification is "the most important UI" in Budget Pace — it must
 * let the user categorize (or dismiss) a detected transaction in one tap, without opening the app.
 */
@Singleton
class CategorizationNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID = "categorization"
        const val ACTION_CATEGORIZE = "com.budgetpace.app.action.CATEGORIZE"
        const val ACTION_DONT_RECORD = "com.budgetpace.app.action.DONT_RECORD"
        const val EXTRA_TRANSACTION_ID = "transactionId"
        const val EXTRA_CATEGORY_ID = "categoryId"

        // Most launchers show ~3 action buttons well; the rest of the user's categories are
        // reached by tapping the notification body, which opens the transaction's full picker.
        private const val MAX_QUICK_CATEGORIES = 3

        fun notificationIdFor(transactionId: String): Int = transactionId.hashCode()
    }

    fun showPrompt(transaction: Transaction, quickCategories: List<Category>) {
        val transactionId = transaction.id.toString()
        val notificationId = notificationIdFor(transactionId)

        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_TRANSACTION_ID, transactionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle("${Money.formatRupees(transaction.amountMinor)} spent via UPI")
            .setContentText("What was this for? Tap for more categories.")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        quickCategories.take(MAX_QUICK_CATEGORIES).forEach { category ->
            builder.addAction(
                NotificationCompat.Action(
                    R.drawable.ic_stat_notification,
                    category.name,
                    categorizeIntent(transactionId, category.id.toString())
                )
            )
        }
        builder.addAction(
            NotificationCompat.Action(
                R.drawable.ic_stat_notification,
                "Don't record",
                dontRecordIntent(transactionId)
            )
        )

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    fun cancel(transactionId: String) {
        NotificationManagerCompat.from(context).cancel(notificationIdFor(transactionId))
    }

    private fun categorizeIntent(transactionId: String, categoryId: String): PendingIntent {
        val intent = Intent(context, CategorizationActionReceiver::class.java).apply {
            action = ACTION_CATEGORIZE
            putExtra(EXTRA_TRANSACTION_ID, transactionId)
            putExtra(EXTRA_CATEGORY_ID, categoryId)
        }
        return PendingIntent.getBroadcast(
            context,
            (transactionId + categoryId).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun dontRecordIntent(transactionId: String): PendingIntent {
        val intent = Intent(context, CategorizationActionReceiver::class.java).apply {
            action = ACTION_DONT_RECORD
            putExtra(EXTRA_TRANSACTION_ID, transactionId)
        }
        return PendingIntent.getBroadcast(
            context,
            transactionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
