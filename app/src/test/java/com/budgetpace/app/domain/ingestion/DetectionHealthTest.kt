package com.budgetpace.app.domain.ingestion

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class DetectionHealthTest {

    private val now: Instant = Instant.parse("2026-09-04T10:00:00Z")

    private fun status(
        smsPermissionGranted: Boolean = true,
        notificationsEnabled: Boolean = true,
        listenerEnabled: Boolean = true,
        batteryUnrestricted: Boolean = true,
    ) = DetectionStatus(
        smsPermissionGranted = smsPermissionGranted,
        notificationsEnabled = notificationsEnabled,
        listenerEnabled = listenerEnabled,
        batteryUnrestricted = batteryUnrestricted,
    )

    private fun snapshotWith(
        outcome: IngestionOutcome,
        at: Instant,
        promptSuppressed: Boolean = false,
        channel: IngestionChannel = IngestionChannel.SMS,
    ): DetectionSnapshot {
        val diagnostics = ChannelDiagnostics(
            lastEventAt = at,
            lastOutcome = outcome,
            lastOutcomeAt = at,
            counts = mapOf(outcome to 1),
        )
        return if (channel == IngestionChannel.SMS) {
            DetectionSnapshot.empty().copy(sms = diagnostics, promptSuppressed = promptSuppressed)
        } else {
            DetectionSnapshot.empty().copy(listener = diagnostics, promptSuppressed = promptSuppressed)
        }
    }

    @Test
    fun testEmptySnapshotHasNoHistory() {
        val empty = DetectionSnapshot.empty()

        assertEquals(ChannelDiagnostics(), empty.sms)
        assertEquals(ChannelDiagnostics(), empty.listener)
        assertEquals("Nothing detected yet", DetectionHealth.summarize(empty, status(), now))
    }

    // ─── permission branches ──────────────────────────────────────────────────

    @Test
    fun testSmsOffWithListenerOn() {
        assertEquals(
            "SMS access off - relying on notification access",
            DetectionHealth.summarize(
                DetectionSnapshot.empty(),
                status(smsPermissionGranted = false, listenerEnabled = true),
                now,
            ),
        )
    }

    @Test
    fun testSmsOffAndListenerOff() {
        assertEquals(
            "SMS access off and notification access is off too",
            DetectionHealth.summarize(
                DetectionSnapshot.empty(),
                status(smsPermissionGranted = false, listenerEnabled = false),
                now,
            ),
        )
    }

    /** Permission state outranks history — a stale success must not read as "all fine". */
    @Test
    fun testSmsOffOutranksAPreviousRecordedOutcome() {
        val snapshot = snapshotWith(IngestionOutcome.RECORDED, now.minusSeconds(120))

        assertEquals(
            "SMS access off - relying on notification access",
            DetectionHealth.summarize(snapshot, status(smsPermissionGranted = false), now),
        )
    }

    // ─── outcome branches ─────────────────────────────────────────────────────

    @Test
    fun testRecorded() {
        val snapshot = snapshotWith(IngestionOutcome.RECORDED, now.minusSeconds(120))

        assertEquals(
            "Last detected 2 min ago - recorded",
            DetectionHealth.summarize(snapshot, status(), now),
        )
    }

    @Test
    fun testRecordedWithPromptSuppressed() {
        val snapshot = snapshotWith(
            IngestionOutcome.RECORDED,
            now.minusSeconds(120),
            promptSuppressed = true,
        )

        assertEquals(
            "Recorded - alert not shown (notifications off)",
            DetectionHealth.summarize(snapshot, status(notificationsEnabled = false), now),
        )
    }

    @Test
    fun testRedactedDoesNotQuoteATime() {
        val snapshot = snapshotWith(IngestionOutcome.REDACTED, now.minusSeconds(30))

        assertEquals(
            "Last message was hidden by Android",
            DetectionHealth.summarize(snapshot, status(), now),
        )
    }

    @Test
    fun testNoMatch() {
        val snapshot = snapshotWith(IngestionOutcome.NO_MATCH, now.minusSeconds(3_600))

        assertEquals(
            "Last message 1 h ago - not a bank transaction",
            DetectionHealth.summarize(snapshot, status(), now),
        )
    }

    @Test
    fun testDuplicate() {
        val snapshot = snapshotWith(IngestionOutcome.DUPLICATE, now.minusSeconds(10))

        assertEquals(
            "Last message just now - already recorded",
            DetectionHealth.summarize(snapshot, status(), now),
        )
    }

    @Test
    fun testLowConfidence() {
        val snapshot = snapshotWith(IngestionOutcome.LOW_CONFIDENCE, now.minusSeconds(600))

        assertEquals(
            "Last message 10 min ago - could not read the details",
            DetectionHealth.summarize(snapshot, status(), now),
        )
    }

    @Test
    fun testNoText() {
        val snapshot = snapshotWith(IngestionOutcome.NO_TEXT, now.minusSeconds(600))

        assertEquals(
            "Last message 10 min ago - arrived with no text",
            DetectionHealth.summarize(snapshot, status(), now),
        )
    }

    @Test
    fun testError() {
        val snapshot = snapshotWith(IngestionOutcome.ERROR, now.minusSeconds(172_800))
            .copy(lastErrorClass = "IllegalStateException")

        assertEquals(
            "Last message 2 d ago - something went wrong",
            DetectionHealth.summarize(snapshot, status(), now),
        )
    }

    /** Both channels can see the same message; the more recent outcome is the one worth showing. */
    @Test
    fun testMostRecentChannelWins() {
        val snapshot = DetectionSnapshot.empty().copy(
            sms = ChannelDiagnostics(
                lastEventAt = now.minusSeconds(3_600),
                lastOutcome = IngestionOutcome.NO_MATCH,
                lastOutcomeAt = now.minusSeconds(3_600),
            ),
            listener = ChannelDiagnostics(
                lastEventAt = now.minusSeconds(120),
                lastOutcome = IngestionOutcome.RECORDED,
                lastOutcomeAt = now.minusSeconds(120),
            ),
        )

        assertEquals(
            "Last detected 2 min ago - recorded",
            DetectionHealth.summarize(snapshot, status(), now),
        )
    }

    // ─── relative time ────────────────────────────────────────────────────────

    @Test
    fun testRelativeTimeBuckets() {
        assertEquals("just now", DetectionHealth.relativeTime(now.minusSeconds(0), now))
        assertEquals("just now", DetectionHealth.relativeTime(now.minusSeconds(59), now))
        assertEquals("1 min ago", DetectionHealth.relativeTime(now.minusSeconds(60), now))
        assertEquals("59 min ago", DetectionHealth.relativeTime(now.minusSeconds(3_599), now))
        assertEquals("1 h ago", DetectionHealth.relativeTime(now.minusSeconds(3_600), now))
        assertEquals("23 h ago", DetectionHealth.relativeTime(now.minusSeconds(86_399), now))
        assertEquals("1 d ago", DetectionHealth.relativeTime(now.minusSeconds(86_400), now))
        assertEquals("9 d ago", DetectionHealth.relativeTime(now.minusSeconds(9 * 86_400L), now))
    }

    /** A device clock moved backwards must not render "-3 min ago". */
    @Test
    fun testFutureTimestampReadsAsJustNow() {
        assertEquals("just now", DetectionHealth.relativeTime(now.plusSeconds(180), now))
    }
}
