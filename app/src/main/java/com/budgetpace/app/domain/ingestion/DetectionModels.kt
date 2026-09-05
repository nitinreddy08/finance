package com.budgetpace.app.domain.ingestion

import java.time.Instant

/**
 * Detection diagnostics data, kept in a pure file so [DetectionHealth] can be tested off-device.
 * The Android `DetectionDiagnostics` singleton only produces these values; nothing here may hold
 * message text, amounts, references or account numbers (spec section 8).
 */

enum class IngestionChannel { SMS, LISTENER }

enum class IngestionOutcome {
    RECORDED,
    DUPLICATE,
    NO_MATCH,
    LOW_CONFIDENCE,
    REDACTED,
    NO_TEXT,
    ERROR,
}

data class ChannelDiagnostics(
    val lastEventAt: Instant? = null,
    val lastOutcome: IngestionOutcome? = null,
    val lastOutcomeAt: Instant? = null,
    val counts: Map<IngestionOutcome, Int> = emptyMap(),
)

data class DetectionSnapshot(
    val sms: ChannelDiagnostics = ChannelDiagnostics(),
    val listener: ChannelDiagnostics = ChannelDiagnostics(),
    /** Only ever a DLT header or the literal "number" — see `IngestionPolicy.sanitizeSender`. */
    val lastSmsSender: String? = null,
    val lastListenerConnectedAt: Instant? = null,
    val lastListenerDisconnectedAt: Instant? = null,
    val lastRecordedAt: Instant? = null,
    val lastRecordedChannel: IngestionChannel? = null,
    /** A transaction was recorded but no categorization prompt could be posted. */
    val promptSuppressed: Boolean = false,
    val lastErrorClass: String? = null,
) {
    companion object {
        fun empty(): DetectionSnapshot = DetectionSnapshot()
    }
}

data class DetectionStatus(
    val smsPermissionGranted: Boolean,
    val notificationsEnabled: Boolean,
    val listenerEnabled: Boolean,
    val batteryUnrestricted: Boolean,
)
