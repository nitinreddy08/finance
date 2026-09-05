package com.budgetpace.app.domain.ingestion

import java.time.Duration
import java.time.Instant

/**
 * The one-line detection status shown on the Settings row and at the top of Detection health.
 * Pure so the copy is testable; strings stay ASCII and never mention amounts or message text.
 */
object DetectionHealth {

    fun summarize(
        snapshot: DetectionSnapshot,
        status: DetectionStatus,
        now: Instant,
    ): String {
        // Permission state outranks history: with SMS off, "last detected 3 d ago" would read as
        // everything being fine while nothing new can arrive.
        if (!status.smsPermissionGranted) {
            return if (status.listenerEnabled) {
                "SMS access off - relying on notification access"
            } else {
                "SMS access off and notification access is off too"
            }
        }

        val last = latestOutcome(snapshot) ?: return "Nothing detected yet"
        val ago = relativeTime(last.second, now)
        return when (last.first) {
            IngestionOutcome.RECORDED ->
                if (snapshot.promptSuppressed) {
                    "Recorded - alert not shown (notifications off)"
                } else {
                    "Last detected $ago - recorded"
                }
            IngestionOutcome.DUPLICATE -> "Last message $ago - already recorded"
            IngestionOutcome.REDACTED -> "Last message was hidden by Android"
            IngestionOutcome.NO_MATCH -> "Last message $ago - not a bank transaction"
            IngestionOutcome.LOW_CONFIDENCE -> "Last message $ago - could not read the details"
            IngestionOutcome.NO_TEXT -> "Last message $ago - arrived with no text"
            IngestionOutcome.ERROR -> "Last message $ago - something went wrong"
        }
    }

    /** Coarse relative time; a clock skew that puts [then] in the future reads as "just now". */
    fun relativeTime(then: Instant, now: Instant): String {
        val seconds = Duration.between(then, now).seconds.coerceAtLeast(0L)
        return when {
            seconds < 60L -> "just now"
            seconds < 3_600L -> "${seconds / 60L} min ago"
            seconds < 86_400L -> "${seconds / 3_600L} h ago"
            else -> "${seconds / 86_400L} d ago"
        }
    }

    private fun latestOutcome(snapshot: DetectionSnapshot): Pair<IngestionOutcome, Instant>? {
        val candidates = listOfNotNull(
            snapshot.sms.toOutcomeAt(),
            snapshot.listener.toOutcomeAt(),
        )
        return candidates.maxByOrNull { it.second }
    }

    private fun ChannelDiagnostics.toOutcomeAt(): Pair<IngestionOutcome, Instant>? {
        val outcome = lastOutcome ?: return null
        val at = lastOutcomeAt ?: return null
        return outcome to at
    }
}
