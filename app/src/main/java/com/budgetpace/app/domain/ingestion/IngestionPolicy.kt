package com.budgetpace.app.domain.ingestion

import com.budgetpace.app.core.model.ParseConfidence
import com.budgetpace.app.core.model.TransactionDirection
import com.budgetpace.app.domain.parser.ParsedTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

sealed interface PreInsertDecision {
    data class Proceed(val parsed: ParsedTransaction) : PreInsertDecision
    data class Stop(val outcome: IngestionOutcome) : PreInsertDecision
}

data class PostInsertDecision(
    val outcome: IngestionOutcome,
    val showPrompt: Boolean,
)

data class DateResolution(
    val date: LocalDate,
    /** The message claimed a date that had not happened yet; the arrival date was used instead. */
    val anomaly: Boolean,
)

sealed interface MonthPlan {
    /** Attribute to whatever month is active today — never create a month in the future. */
    data object CurrentMonth : MonthPlan
    data class ExistingMonth(val year: Int, val month: Int) : MonthPlan
    data class CreateArchivedMonth(val year: Int, val month: Int) : MonthPlan
}

/**
 * Every decision the SMS receiver and the notification listener share, kept pure so both channels
 * behave identically and the behaviour is testable without a device.
 */
object IngestionPolicy {

    /**
     * Android 15 replaces notification text with this literal for third-party listeners whenever it
     * detects an OTP-like number; a 12-digit UPI reference qualifies, so every bank SMS is hit.
     */
    const val REDACTION_MARKER: String = "Sensitive notification content hidden"

    /** Indian DLT alphanumeric sender header, e.g. "AX-KOTAKB-S". */
    private val DLT_HEADER = Regex("^[A-Za-z]{2}-[A-Za-z0-9]{3,9}(-[A-Za-z])?$")

    /** Shown instead of a sender that is not a DLT header, so diagnostics never leak a phone number. */
    const val OPAQUE_SENDER: String = "number"

    fun isRedacted(text: String?): Boolean =
        text != null && text.contains(REDACTION_MARKER, ignoreCase = true)

    /**
     * [parse] is a lambda so redacted text is never handed to a parser: the marker replaces the
     * whole body, and running regexes over it only wastes work and muddies the outcome.
     */
    fun preInsert(text: String?, parse: () -> ParsedTransaction?): PreInsertDecision {
        if (text.isNullOrBlank()) return PreInsertDecision.Stop(IngestionOutcome.NO_TEXT)
        if (isRedacted(text)) return PreInsertDecision.Stop(IngestionOutcome.REDACTED)
        val parsed = parse() ?: return PreInsertDecision.Stop(IngestionOutcome.NO_MATCH)
        if (parsed.confidence != ParseConfidence.HIGH) {
            return PreInsertDecision.Stop(IngestionOutcome.LOW_CONFIDENCE)
        }
        // A malformed amount must never become a zero-rupee prompt asking for a category.
        if (parsed.amountMinor <= 0L) return PreInsertDecision.Stop(IngestionOutcome.NO_MATCH)
        return PreInsertDecision.Proceed(parsed)
    }

    /**
     * The insert's return value is the only truth for whether a prompt is shown: Room's
     * `OnConflictStrategy.IGNORE` returns -1 when the duplicate key already exists, and the two
     * capture channels can deliver the same message milliseconds apart.
     */
    fun postInsert(insertResult: Long, direction: TransactionDirection): PostInsertDecision =
        if (insertResult < 0L) {
            PostInsertDecision(IngestionOutcome.DUPLICATE, showPrompt = false)
        } else {
            PostInsertDecision(
                IngestionOutcome.RECORDED,
                showPrompt = direction == TransactionDirection.DEBIT,
            )
        }

    /**
     * Spec section 17: the bank's date wins, so an SMS arriving 00:02 on 1 Sep dated 31 Aug stays in
     * August. A date *after* the arrival date is rejected outright with no tolerance window —
     * accepting one would let the app create a month that has not happened yet, which later collides
     * with the unique(year, month) index at rollover and crashes on every launch.
     */
    fun resolveTransactionDate(
        parsedDate: LocalDate?,
        receivedAt: Instant,
        zone: ZoneId,
    ): DateResolution {
        val arrival = receivedAt.atZone(zone).toLocalDate()
        return when {
            parsedDate == null -> DateResolution(arrival, anomaly = false)
            parsedDate.isAfter(arrival) -> DateResolution(arrival, anomaly = true)
            else -> DateResolution(parsedDate, anomaly = false)
        }
    }

    fun planMonth(
        txDate: LocalDate,
        today: LocalDate,
        monthExists: (year: Int, month: Int) -> Boolean,
    ): MonthPlan {
        val target = YearMonth.from(txDate)
        // Anything at or beyond the current month belongs to the active month; a future month row
        // would collide with the rollover insert.
        if (!target.isBefore(YearMonth.from(today))) return MonthPlan.CurrentMonth
        val year = target.year
        val month = target.monthValue
        return if (monthExists(year, month)) {
            MonthPlan.ExistingMonth(year, month)
        } else {
            MonthPlan.CreateArchivedMonth(year, month)
        }
    }

    /**
     * Diagnostics show "last SMS from ..."; without this it would routinely show a friend's phone
     * number, which spec section 8 forbids storing.
     */
    fun sanitizeSender(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return if (DLT_HEADER.matches(trimmed)) trimmed else OPAQUE_SENDER
    }
}
