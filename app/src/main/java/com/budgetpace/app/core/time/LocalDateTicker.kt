package com.budgetpace.app.core.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Emits today's date, then again just after each local midnight.
 *
 * The month summary is computed against a date, so without this the app can sit open across
 * midnight still highlighting yesterday's period and offering yesterday's safe-to-spend.
 *
 * [zone] and [now] are injectable so the behaviour is testable against a virtual clock.
 */
class LocalDateTicker(
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val now: () -> Instant = Instant::now,
) {
    fun dates(): Flow<LocalDate> = flow {
        while (true) {
            // Re-read the clock every iteration rather than counting days: a delay can wake late
            // (the device slept), and a time zone can change under us.
            val currentZone = zone()
            val instant = now()
            val today = instant.atZone(currentZone).toLocalDate()
            emit(today)

            val nextMidnight = today.plusDays(1).atStartOfDay(currentZone).toInstant()
            val untilMidnight = Duration.between(instant, nextMidnight).toMillis()
            delay(untilMidnight.coerceAtLeast(0L) + SETTLE_MARGIN_MILLIS)
        }
    }.distinctUntilChanged()

    private companion object {
        /** Wake just past midnight so the recomputed date is never still yesterday. */
        const val SETTLE_MARGIN_MILLIS = 1_000L
    }
}
