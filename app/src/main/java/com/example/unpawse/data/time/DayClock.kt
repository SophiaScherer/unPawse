package com.example.unpawse.data.time

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The app's single answer to "what time is it, and which day is today?". Every date-keyed screen and
 * store reads it, so enforcement, Home, Stats and the Gallery cannot disagree about the day.
 *
 * @param changes fires when the wall clock or zone moves under us (the time-change broadcasts), so a
 * manual change is picked up at once rather than on the next scheduled tick.
 */
class DayClock(
    private val currentMillis: () -> Long = System::currentTimeMillis,
    private val currentZone: () -> ZoneId = ZoneId::systemDefault,
    private val changes: Flow<Unit> = emptyFlow(),
    private val maxWaitMillis: Long = MAX_WAIT_MILLIS,
) {
    fun nowMillis(): Long = currentMillis()

    fun zone(): ZoneId = currentZone()

    fun now(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis()), zone())

    fun today(): LocalDate = now().toLocalDate()

    /**
     * Emits the local time now, then again at each local midnight and at least every [maxWaitMillis].
     *
     * The cap is not just for the greeting: coroutine delays run on a monotonic clock that stops while
     * the device sleeps, so a single delay aimed at midnight would land hours late after a night in a
     * drawer. Re-reading the wall clock each minute bounds that error to one tick.
     */
    fun ticks(): Flow<LocalDateTime> = channelFlow {
        val wake = Channel<Unit>(Channel.CONFLATED)
        launch { changes.collect { wake.trySend(Unit) } }
        while (true) {
            val millis = nowMillis()
            val zone = zone()
            send(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone))
            val wait = minOf(millisUntilNextMidnight(millis, zone), maxWaitMillis)
            withTimeoutOrNull(wait) { wake.receive() }
        }
    }

    companion object {
        const val MAX_WAIT_MILLIS = 60_000L
    }
}

/** The local date of each tick, emitted only when it changes. */
fun Flow<LocalDateTime>.dates(): Flow<LocalDate> = map { it.toLocalDate() }.distinctUntilChanged()

/**
 * Milliseconds from [nowMillis] to the start of the next local day in [zone]. Always at least 1.
 *
 * Goes through `atStartOfDay(zone)` rather than adding 24 hours, so DST days come out 23 or 25 hours
 * long and a zone whose midnight is skipped resolves to the first instant that does exist.
 */
fun millisUntilNextMidnight(nowMillis: Long, zone: ZoneId): Long {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val next = now.toLocalDate().plusDays(1).atStartOfDay(zone)
    return Duration.between(now, next).toMillis().coerceAtLeast(1L)
}
