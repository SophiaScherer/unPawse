package com.example.unpawse.data.time

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class DayClockTest {

    private val zone = ZoneId.of("America/New_York")

    private fun millisAt(dateTime: LocalDateTime, zone: ZoneId = this.zone): Long =
        ZonedDateTime.of(dateTime, zone).toInstant().toEpochMilli()

    private val hour = 3_600_000L

    @Test
    fun `an ordinary day ends at the next midnight`() {
        val now = millisAt(LocalDateTime.of(2026, 7, 16, 23, 59, 30))
        assertEquals(30_000L, millisUntilNextMidnight(now, zone))
    }

    @Test
    fun `exactly midnight waits for the whole of the new day`() {
        val now = millisAt(LocalDate.of(2026, 7, 16).atStartOfDay())
        assertEquals(24 * hour, millisUntilNextMidnight(now, zone))
    }

    @Test
    fun `the spring-forward day is an hour short`() {
        // 2026-03-08 skips 02:00-03:00 in New York.
        val now = millisAt(LocalDateTime.of(2026, 3, 8, 0, 30))
        assertEquals(22 * hour + 30 * 60_000L, millisUntilNextMidnight(now, zone))
    }

    @Test
    fun `the fall-back day is an hour long`() {
        // 2026-11-01 repeats 01:00-02:00 in New York.
        val now = millisAt(LocalDateTime.of(2026, 11, 1, 0, 30))
        assertEquals(24 * hour + 30 * 60_000L, millisUntilNextMidnight(now, zone))
    }

    @Test
    fun `a skipped midnight resolves to the first instant of the new day`() {
        // Chile springs forward at midnight: 2026-09-06 begins at 01:00, an hour after 23:00.
        val santiago = ZoneId.of("America/Santiago")
        val now = millisAt(LocalDateTime.of(2026, 9, 5, 23, 0), santiago)
        assertEquals(hour, millisUntilNextMidnight(now, santiago))
    }

    /** A clock on the test scheduler's virtual time, starting at [start]; [offset] models a clock change. */
    private class FakeWallClock(scope: TestScope, start: LocalDateTime, zone: ZoneId) {
        var offset = 0L
        private val base = ZonedDateTime.of(start, zone).toInstant().toEpochMilli()
        val millis: () -> Long = { base + scope.testScheduler.currentTime + offset }
    }

    @Test
    fun `ticks emit the new date at midnight, not a minute late`() = runTest {
        val wall = FakeWallClock(this, LocalDateTime.of(2026, 7, 16, 23, 59, 30), zone)
        val clock = DayClock(currentMillis = wall.millis, currentZone = { zone })
        val dates = mutableListOf<LocalDate>()
        backgroundScope.launch { clock.ticks().dates().collect { dates += it } }

        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 7, 16)), dates)

        advanceTimeBy(29_999L)
        runCurrent()
        assertEquals(1, dates.size)

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 7, 16), LocalDate.of(2026, 7, 17)), dates)
    }

    @Test
    fun `ticks re-read the wall clock at least once a minute`() = runTest {
        val wall = FakeWallClock(this, LocalDateTime.of(2026, 7, 16, 10, 0), zone)
        val clock = DayClock(currentMillis = wall.millis, currentZone = { zone })
        val ticks = mutableListOf<ZonedDateTime>()
        backgroundScope.launch { clock.ticks().collect { ticks += it } }

        runCurrent()
        // The monotonic delay knows nothing of a jump in the wall clock; the next tick still sees it.
        wall.offset = 3 * hour
        advanceTimeBy(DayClock.MAX_WAIT_MILLIS)
        runCurrent()

        assertEquals(LocalDateTime.of(2026, 7, 16, 13, 1), ticks.last().toLocalDateTime())
    }

    @Test
    fun `a clock change re-emits at once`() = runTest {
        val wall = FakeWallClock(this, LocalDateTime.of(2026, 7, 16, 10, 0), zone)
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val clock = DayClock(currentMillis = wall.millis, currentZone = { zone }, changes = changes)
        val dates = mutableListOf<LocalDate>()
        backgroundScope.launch { clock.ticks().dates().collect { dates += it } }
        runCurrent()

        wall.offset = -2 * 24 * hour
        changes.tryEmit(Unit)
        runCurrent()

        assertEquals(listOf(LocalDate.of(2026, 7, 16), LocalDate.of(2026, 7, 14)), dates)
    }

    @Test
    fun `a zone change moves the date with it`() = runTest {
        var currentZone = zone
        val wall = FakeWallClock(this, LocalDateTime.of(2026, 7, 16, 22, 0), zone)
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val clock = DayClock(currentMillis = wall.millis, currentZone = { currentZone }, changes = changes)
        val dates = mutableListOf<LocalDate>()
        backgroundScope.launch { clock.ticks().dates().collect { dates += it } }
        runCurrent()

        // 22:00 in New York is already the next morning in Tokyo.
        currentZone = ZoneId.of("Asia/Tokyo")
        changes.tryEmit(Unit)
        runCurrent()

        assertEquals(LocalDate.of(2026, 7, 17), dates.last())
        assertEquals(LocalDate.of(2026, 7, 17), clock.today())
    }

    @Test
    fun `a zone change on the same date is a new day for grouping, not for date queries`() = runTest {
        var currentZone = zone
        val wall = FakeWallClock(this, LocalDateTime.of(2026, 7, 16, 10, 0), zone)
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val clock = DayClock(currentMillis = wall.millis, currentZone = { currentZone }, changes = changes)
        val days = mutableListOf<LocalDay>()
        val dates = mutableListOf<LocalDate>()
        backgroundScope.launch { clock.ticks().days().collect { days += it } }
        backgroundScope.launch { clock.ticks().dates().collect { dates += it } }
        runCurrent()

        // 10:00 in New York is 07:00 in Los Angeles: same date, different zone.
        currentZone = ZoneId.of("America/Los_Angeles")
        changes.tryEmit(Unit)
        runCurrent()

        assertEquals(listOf(LocalDay(LocalDate.of(2026, 7, 16), zone), LocalDay(LocalDate.of(2026, 7, 16), currentZone)), days)
        assertEquals(listOf(LocalDate.of(2026, 7, 16)), dates)
    }
}
