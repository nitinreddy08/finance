package com.budgetpace.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.budgetpace.app.domain.ingestion.IngestionChannel
import com.budgetpace.app.domain.ingestion.IngestionOutcome
import com.budgetpace.app.domain.ingestion.SmsBodyAssembler
import com.budgetpace.app.domain.parser.MessageSources
import com.budgetpace.app.domain.parser.NotificationInput
import com.budgetpace.app.ingestion.DetectionDiagnostics
import com.budgetpace.app.ingestion.TransactionIngestor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import javax.inject.Inject

/**
 * The primary capture path (plan section 1.1). Android 15 replaces notification text with
 * "Sensitive notification content hidden" for every third-party listener as soon as it spots an
 * OTP-like number, and a 12-digit UPI reference is one — so on the owner's phone the listener can
 * never read a bank SMS. Reading the SMS itself is unaffected, and SMS_RECEIVED is on the
 * implicit-broadcast exemption list, so this fires even with the process dead.
 *
 * Nothing in here may throw. An uncaught exception from a manifest receiver kills the process, and
 * on this OEM that costs the owner every message until they next open the app.
 *
 * Note: `super.onReceive` is deliberately absent. `BroadcastReceiver.onReceive` is abstract, so a
 * Kotlin super call does not compile; Hilt's Gradle plugin rewrites the superclass and inserts the
 * `super.onReceive` that performs field injection at the very start of this method.
 */
@AndroidEntryPoint
class BankSmsReceiver : BroadcastReceiver() {

    @Inject lateinit var ingestor: TransactionIngestor
    @Inject lateinit var diagnostics: DetectionDiagnostics

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        var started = false
        try {
            if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

            // getMessagesFromIntent returns null when the "pdus" extra is missing or malformed, and
            // any element is null when SmsMessage.createFromPdu could not decode that part. An
            // unguarded dereference here is an NPE on the main thread of a background process.
            val messages: List<SmsMessage> = Telephony.Sms.Intents
                .getMessagesFromIntent(intent)
                ?.filterNotNull()
                .orEmpty()
            if (messages.isEmpty()) {
                diagnostics.recordEvent(IngestionChannel.SMS, sender = null)
                diagnostics.recordOutcome(IngestionChannel.SMS, IngestionOutcome.NO_TEXT)
                return
            }

            // Multipart bodies must be joined in delivery order; the platform hands the parts over
            // in order and each one carries only its own slice of the text.
            val body = SmsBodyAssembler.join(messages.map { it.messageBody })
            val sender = messages.firstNotNullOfOrNull { it.originatingAddress }

            val input = NotificationInput(
                packageName = MessageSources.sms(sender),
                title = sender,
                text = body,
                receivedAt = Instant.now(),
            )

            val job = ingestor.submit(input, IngestionChannel.SMS)
            started = true
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    // Join, never cancel: the ingestion owns an application-lifetime scope, and
                    // abandoning it mid-write would leave a row with no prompt. The timeout only
                    // bounds how long this receiver is held open.
                    withTimeoutOrNull(INGESTION_TIMEOUT_MILLIS) { job.join() }
                } finally {
                    pendingResult.finish()
                }
            }
        } catch (error: Throwable) {
            Log.e(TAG, "SMS receiver failed", error)
            runCatching {
                diagnostics.recordOutcome(
                    IngestionChannel.SMS,
                    IngestionOutcome.ERROR,
                    error.javaClass.simpleName,
                )
            }
        } finally {
            // The waiter above owns finish() once ingestion has actually been started.
            if (!started) {
                runCatching { pendingResult.finish() }
            }
        }
    }

    private companion object {
        const val TAG = "SmsIngest"
        const val INGESTION_TIMEOUT_MILLIS = 8_000L
    }
}
