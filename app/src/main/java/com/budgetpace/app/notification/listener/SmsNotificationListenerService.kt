package com.budgetpace.app.notification.listener

import android.app.Notification
import android.content.ComponentName
import android.os.Build
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.budgetpace.app.domain.ingestion.IngestionChannel
import com.budgetpace.app.domain.parser.MessageSources
import com.budgetpace.app.domain.parser.NotificationInput
import com.budgetpace.app.ingestion.DetectionDiagnostics
import com.budgetpace.app.ingestion.TransactionIngestor
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject

/**
 * Fallback capture path (plan section 1.6). [BankSmsReceiver][com.budgetpace.app.sms.BankSmsReceiver]
 * is primary; this exists for the messages it misses, and because Android 15 may redact the very
 * text this service would otherwise read. Everything past "which text did the notification carry"
 * is shared with the SMS path through [TransactionIngestor].
 */
@AndroidEntryPoint
class SmsNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var ingestor: TransactionIngestor
    @Inject lateinit var diagnostics: DetectionDiagnostics

    override fun onListenerConnected() {
        super.onListenerConnected()
        diagnostics.recordListenerConnected()
        // A message posted while the listener was disconnected (rebinding after a crash or an
        // update) is otherwise never seen at all, since onNotificationPosted only fires for what
        // arrives after connection.
        runCatching {
            activeNotifications
                ?.filter { isSupportedSource(it.packageName) }
                ?.forEach { handle(it) }
        }.onFailure { Log.e(TAG, "Backfill from active notifications failed", it) }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        diagnostics.recordListenerDisconnected()
        requestRebind(ComponentName(this, SmsNotificationListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (!isSupportedSource(sbn.packageName)) return
        handle(sbn)
    }

    private fun handle(sbn: StatusBarNotification) {
        try {
            val text = extractText(sbn.notification)
            val input = NotificationInput(
                packageName = sbn.packageName,
                title = sbn.notification.extras.getString(Notification.EXTRA_TITLE),
                text = text,
                receivedAt = Instant.ofEpochMilli(sbn.postTime),
            )
            // Fire-and-forget into the ingestor's own application-lifetime scope: both
            // onNotificationPosted and onListenerConnected run on the main thread, and this
            // service can be torn down by the system at any moment, so nothing here may block
            // waiting for the DB write to finish.
            ingestor.submit(input, IngestionChannel.LISTENER)
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to process notification from ${sbn.packageName}", error)
        }
    }

    private fun isSupportedSource(packageName: String): Boolean =
        packageName == MessageSources.GOOGLE_MESSAGES || packageName == defaultSmsPackage()

    private fun defaultSmsPackage(): String? =
        runCatching { Telephony.Sms.getDefaultSmsPackage(this) }.getOrNull()

    /**
     * Order matters: a messaging app's own MessagingStyle carries the truest per-message text;
     * `EXTRA_TEXT_LINES` is the multi-line summary some apps post instead; the single-line extras
     * are the last resort. Android 15's redaction (see [com.budgetpace.app.domain.ingestion.
     * IngestionPolicy.REDACTION_MARKER]) can replace any of these, which the shared ingestion
     * policy detects afterwards — extraction does not need to know about it.
     */
    private fun extractText(notification: Notification): String? {
        val extras = notification.extras

        val messagingText = runCatching {
            @Suppress("DEPRECATION")
            val parcelables = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            Notification.MessagingStyle.Message.getMessagesFromBundleArray(parcelables)
        }.getOrNull()?.lastOrNull()?.text?.toString()
        if (!messagingText.isNullOrBlank()) return messagingText

        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        val lastLine = lines?.lastOrNull()?.toString()
        if (!lastLine.isNullOrBlank()) return lastLine

        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        if (!bigText.isNullOrBlank()) return bigText

        return extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    }

    private companion object {
        const val TAG = "SmsListener"
    }
}
