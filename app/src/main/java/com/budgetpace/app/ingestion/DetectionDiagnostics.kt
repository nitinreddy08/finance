package com.budgetpace.app.ingestion

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.budgetpace.app.domain.ingestion.ChannelDiagnostics
import com.budgetpace.app.domain.ingestion.DetectionSnapshot
import com.budgetpace.app.domain.ingestion.IngestionChannel
import com.budgetpace.app.domain.ingestion.IngestionOutcome
import com.budgetpace.app.domain.ingestion.IngestionPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the two capture channels did, so the owner can answer "did my bank SMS arrive?" from inside
 * the app instead of over adb (plan section 1.7).
 *
 * Everything here survives process death, because the interesting case is exactly the one where the
 * app was never open: the SMS receiver runs in a process that is started for the broadcast and
 * killed again seconds later. Nothing but the fields of [DetectionSnapshot] is stored — no message
 * text, amounts, references or account numbers (spec section 8) — and the SMS sender only ever
 * reaches disk through [IngestionPolicy.sanitizeSender].
 */
@Singleton
class DetectionDiagnostics @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val state = MutableStateFlow(readSnapshot())

    val snapshot: StateFlow<DetectionSnapshot> = state.asStateFlow()

    /**
     * A message reached a channel, before anything is known about it. Recorded separately from the
     * outcome so "the receiver never fires at all" and "it fires but nothing parses" look different.
     */
    @Synchronized
    fun recordEvent(channel: IngestionChannel, sender: String?, at: Instant = Instant.now()) {
        val current = state.value
        val updated = current
            .withChannel(channel) { it.copy(lastEventAt = at) }
            .let { snapshot ->
                if (channel == IngestionChannel.SMS) {
                    snapshot.copy(lastSmsSender = IngestionPolicy.sanitizeSender(sender) ?: snapshot.lastSmsSender)
                } else {
                    snapshot
                }
            }
        persist(updated)
    }

    @Synchronized
    fun recordOutcome(
        channel: IngestionChannel,
        outcome: IngestionOutcome,
        errorClass: String? = null,
        at: Instant = Instant.now(),
    ) {
        var updated = state.value.withChannel(channel) {
            it.copy(
                lastOutcome = outcome,
                lastOutcomeAt = at,
                counts = it.counts + (outcome to ((it.counts[outcome] ?: 0) + 1)),
            )
        }
        if (outcome == IngestionOutcome.RECORDED) {
            // A fresh recording clears the "we recorded it but could not tell you" warning: the
            // owner has since either turned notifications back on or not, and the next suppressed
            // prompt sets it again.
            updated = updated.copy(
                lastRecordedAt = at,
                lastRecordedChannel = channel,
                promptSuppressed = false,
            )
        }
        if (outcome == IngestionOutcome.ERROR) {
            updated = updated.copy(lastErrorClass = errorClass)
        }
        persist(updated)
    }

    /** The expense was written but no prompt could be posted, so Home is the only place it shows. */
    @Synchronized
    fun recordPromptSuppressed() {
        persist(state.value.copy(promptSuppressed = true))
    }

    @Synchronized
    fun recordListenerConnected(at: Instant = Instant.now()) {
        persist(state.value.copy(lastListenerConnectedAt = at))
    }

    @Synchronized
    fun recordListenerDisconnected(at: Instant = Instant.now()) {
        persist(state.value.copy(lastListenerDisconnectedAt = at))
    }

    private fun persist(updated: DetectionSnapshot) {
        state.value = updated
        prefs.edit {
            writeChannel(SMS_PREFIX, updated.sms)
            writeChannel(LISTENER_PREFIX, updated.listener)
            putStringOrRemove(KEY_LAST_SMS_SENDER, updated.lastSmsSender)
            putInstantOrRemove(KEY_LISTENER_CONNECTED_AT, updated.lastListenerConnectedAt)
            putInstantOrRemove(KEY_LISTENER_DISCONNECTED_AT, updated.lastListenerDisconnectedAt)
            putInstantOrRemove(KEY_LAST_RECORDED_AT, updated.lastRecordedAt)
            putStringOrRemove(KEY_LAST_RECORDED_CHANNEL, updated.lastRecordedChannel?.name)
            putBoolean(KEY_PROMPT_SUPPRESSED, updated.promptSuppressed)
            putStringOrRemove(KEY_LAST_ERROR_CLASS, updated.lastErrorClass)
        }
    }

    private fun readSnapshot(): DetectionSnapshot = DetectionSnapshot(
        sms = readChannel(SMS_PREFIX),
        listener = readChannel(LISTENER_PREFIX),
        lastSmsSender = prefs.getString(KEY_LAST_SMS_SENDER, null),
        lastListenerConnectedAt = readInstant(KEY_LISTENER_CONNECTED_AT),
        lastListenerDisconnectedAt = readInstant(KEY_LISTENER_DISCONNECTED_AT),
        lastRecordedAt = readInstant(KEY_LAST_RECORDED_AT),
        lastRecordedChannel = readEnum(KEY_LAST_RECORDED_CHANNEL) { IngestionChannel.valueOf(it) },
        promptSuppressed = prefs.getBoolean(KEY_PROMPT_SUPPRESSED, false),
        lastErrorClass = prefs.getString(KEY_LAST_ERROR_CLASS, null),
    )

    private fun readChannel(prefix: String): ChannelDiagnostics = ChannelDiagnostics(
        lastEventAt = readInstant(prefix + SUFFIX_LAST_EVENT_AT),
        lastOutcome = readEnum(prefix + SUFFIX_LAST_OUTCOME) { IngestionOutcome.valueOf(it) },
        lastOutcomeAt = readInstant(prefix + SUFFIX_LAST_OUTCOME_AT),
        counts = IngestionOutcome.entries.mapNotNull { outcome ->
            val count = prefs.getInt(prefix + SUFFIX_COUNT + outcome.name, 0)
            if (count > 0) outcome to count else null
        }.toMap(),
    )

    private fun SharedPreferences.Editor.writeChannel(prefix: String, channel: ChannelDiagnostics) {
        putInstantOrRemove(prefix + SUFFIX_LAST_EVENT_AT, channel.lastEventAt)
        putStringOrRemove(prefix + SUFFIX_LAST_OUTCOME, channel.lastOutcome?.name)
        putInstantOrRemove(prefix + SUFFIX_LAST_OUTCOME_AT, channel.lastOutcomeAt)
        IngestionOutcome.entries.forEach { outcome ->
            val count = channel.counts[outcome] ?: 0
            if (count > 0) {
                putInt(prefix + SUFFIX_COUNT + outcome.name, count)
            } else {
                remove(prefix + SUFFIX_COUNT + outcome.name)
            }
        }
    }

    private fun SharedPreferences.Editor.putInstantOrRemove(key: String, value: Instant?) {
        if (value == null) remove(key) else putLong(key, value.toEpochMilli())
    }

    private fun SharedPreferences.Editor.putStringOrRemove(key: String, value: String?) {
        if (value == null) remove(key) else putString(key, value)
    }

    private fun readInstant(key: String): Instant? {
        val millis = prefs.getLong(key, ABSENT)
        return if (millis == ABSENT) null else Instant.ofEpochMilli(millis)
    }

    /**
     * A stored name that a later build no longer has must not crash the app on launch, so an
     * unreadable enum reads as "nothing recorded" rather than throwing.
     */
    private fun <T : Enum<T>> readEnum(key: String, parse: (String) -> T): T? {
        val raw = prefs.getString(key, null) ?: return null
        return runCatching { parse(raw) }.getOrNull()
    }

    private fun DetectionSnapshot.withChannel(
        channel: IngestionChannel,
        transform: (ChannelDiagnostics) -> ChannelDiagnostics,
    ): DetectionSnapshot = when (channel) {
        IngestionChannel.SMS -> copy(sms = transform(sms))
        IngestionChannel.LISTENER -> copy(listener = transform(listener))
    }

    private companion object {
        const val PREFS_NAME = "detection_diagnostics"
        const val ABSENT = Long.MIN_VALUE

        const val SMS_PREFIX = "sms."
        const val LISTENER_PREFIX = "listener."

        const val SUFFIX_LAST_EVENT_AT = "lastEventAt"
        const val SUFFIX_LAST_OUTCOME = "lastOutcome"
        const val SUFFIX_LAST_OUTCOME_AT = "lastOutcomeAt"
        const val SUFFIX_COUNT = "count."

        const val KEY_LAST_SMS_SENDER = "lastSmsSender"
        const val KEY_LISTENER_CONNECTED_AT = "listenerConnectedAt"
        const val KEY_LISTENER_DISCONNECTED_AT = "listenerDisconnectedAt"
        const val KEY_LAST_RECORDED_AT = "lastRecordedAt"
        const val KEY_LAST_RECORDED_CHANNEL = "lastRecordedChannel"
        const val KEY_PROMPT_SUPPRESSED = "promptSuppressed"
        const val KEY_LAST_ERROR_CLASS = "lastErrorClass"
    }
}
