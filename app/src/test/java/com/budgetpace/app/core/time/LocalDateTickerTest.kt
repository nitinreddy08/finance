package com.budgetpace.app.core.time

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LocalDateTickerTest {

    private val kolkata = ZoneId.of("Asia/Kolkata")

    @Test
    fun emitsTodayThenRollsOverAtLocalMidnight() = runTest {
        // 2026-09-08T18:29:30Z is 2026-09-08T23:59:30 in Kolkata: half a minute short of midnight.
        // The injected clock is driven by the test scheduler, so the ticker's own delay advances it
        // — a fixed instant would make the flow re-emit the same date forever.
        val start = Instant.parse("2026-09-08T18:29:30Z")
        val ticker = LocalDateTicker(
            zone = { kolkata },
            now = { start.plusMillis(testScheduler.currentTime) },
        )

        val dates = ticker.dates().take(2).toList()

        assertEquals(listOf(LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 9)), dates)
    }

    @Test
    fun repeatedTicksNeverRepeatADate() = runTest {
        val start = Instant.parse("2026-09-08T18:29:30Z")
        val ticker = LocalDateTicker(
            zone = { kolkata },
            now = { start.plusMillis(testScheduler.currentTime) },
        )

        val dates = ticker.dates().take(3).toList()

        assertEquals(
            listOf(LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10)),
            dates
        )
    }

    @Test
    fun aChangedTimeZoneMovesTheNextRolloverToThatZonesMidnight() = runTest {
        // Flying west just before Kolkata's midnight must not roll the date over on Kolkata's
        // schedule. The date it settles on is the same either way, so what proves the zone was
        // honoured is WHEN the tick fires: Los Angeles is still mid-morning on the 8th here, so
        // the next rollover is ~12.5 hours away rather than 31 seconds.
        val start = Instant.parse("2026-09-08T18:29:30Z")
        var zone = kolkata
        val ticker = LocalDateTicker(
            zone = { zone },
            now = { start.plusMillis(testScheduler.currentTime) },
        )

        val dates = mutableListOf<LocalDate>()
        ticker.dates().take(2).collect { date ->
            dates += date
            zone = ZoneId.of("America/Los_Angeles")
        }

        assertEquals(listOf(LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 9)), dates)
        assertTrue(
            "rolled over after ${testScheduler.currentTime} ms, so it used the old zone",
            testScheduler.currentTime > Duration.ofHours(12).toMillis()
        )
    }
}
