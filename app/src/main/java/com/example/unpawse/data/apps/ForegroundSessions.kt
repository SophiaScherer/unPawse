package com.example.unpawse.data.apps

/**
 * One usage event, reduced to what foreground accounting needs. Pure, so the session rules below
 * are unit-tested without `UsageStatsManager`.
 */
internal data class ForegroundEvent(
    val packageName: String,
    val className: String?,
    val timeMillis: Long,
    val kind: Kind,
) {
    enum class Kind {
        /** An activity came to the front. */
        RESUMED,

        /** An activity left the front: a pause or a stop, whichever the platform reports first. */
        LEFT,

        /** The device shut down or started up; nothing can still be in front across it. */
        DEVICE_BOUNDARY,

        /** The screen went off. The app stays on top, but nobody is using it. */
        SCREEN_OFF,

        /** The screen came back on; whatever is on top is being used again. */
        SCREEN_ON,
    }
}

/** A span one package spent in the foreground, half-open in epoch millis. */
internal data class ForegroundInterval(val packageName: String, val beginMillis: Long, val endMillis: Long)

/**
 * Turns [events] (oldest first) into per-package foreground spans within [beginMillis, endMillis],
 * crediting **one app at a time** — whatever is on top — so a day never holds more time than it lasts.
 *
 * Built from events rather than `queryAndAggregateUsageStats`, which returns every whole platform
 * bucket the window touches; the buckets aren't aligned to local midnight, so a one-day query came
 * back as one or two whole buckets — or a whole week once daily buckets had aged out.
 *
 * - The on-screen activities are a stack, as in the foreground monitor: a resume goes on top, a pause
 *   or stop removes that activity, and whatever was beneath resurfaces. Pairing each activity's own
 *   resume and pause instead double-counted anything that stays resumed underneath — an emulator's
 *   secondary-display launcher added a whole day to every day that way.
 * - A departure with no arrival credits nothing: the arrival is older than the platform's history,
 *   which usually starts days after [beginMillis], so crediting from there invented days of usage.
 * - Whatever is on top at the end is counted up to [endMillis].
 * - A device boundary empties the stack at the last event seen before it: nothing survives a reboot,
 *   and the shutdown itself may not have been logged.
 * - Screen-off time credits nobody, but the stack is kept: waking resumes nothing, so the app left
 *   on top really is still there. Same rule as the tracker's `isInteractive` gate.
 */
internal fun foregroundIntervals(
    events: List<ForegroundEvent>,
    beginMillis: Long,
    endMillis: Long,
): List<ForegroundInterval> {
    var stack = emptyList<Pair<String, String?>>()
    val spans = mutableListOf<ForegroundInterval>()
    var since = beginMillis
    var lastTime = beginMillis
    var screenOn = true

    fun creditTop(until: Long) {
        val top = stack.lastOrNull()
        if (screenOn && top != null && until > since) spans += ForegroundInterval(top.first, since, until)
        // Never step back: an out-of-order event would otherwise credit the same time twice.
        since = maxOf(since, until)
    }

    for (event in events) {
        val time = event.timeMillis.coerceIn(beginMillis, endMillis)
        val key = event.packageName to event.className
        when (event.kind) {
            ForegroundEvent.Kind.RESUMED -> {
                creditTop(time)
                stack = ((stack - key) + key).takeLast(MAX_STACK)
            }
            ForegroundEvent.Kind.LEFT -> {
                creditTop(time)
                stack = stack - key
            }
            ForegroundEvent.Kind.DEVICE_BOUNDARY -> {
                creditTop(lastTime)
                stack = emptyList()
                since = time
                screenOn = true
            }
            ForegroundEvent.Kind.SCREEN_OFF -> {
                creditTop(time)
                screenOn = false
            }
            ForegroundEvent.Kind.SCREEN_ON -> {
                if (!screenOn) since = maxOf(since, time)
                screenOn = true
            }
        }
        lastTime = time
    }
    creditTop(endMillis)

    return spans
}

/** Deepest the stack may get, as in the monitor: a departure that never arrives must not leak. */
private const val MAX_STACK = 16

/** Total foreground millis per package across [intervals]. */
internal fun totalMillisByPackage(intervals: List<ForegroundInterval>): Map<String, Long> =
    intervals.groupBy { it.packageName }.mapValues { (_, spans) -> spans.sumOf { it.endMillis - it.beginMillis } }

/**
 * Foreground seconds per package for each of [windows], keyed by the window's date, each span
 * clipped to the day it falls in — so a session across midnight is split between the two days.
 *
 * A day appears **only if the platform still holds something for it**: an event inside it, or a span
 * overlapping it. A day it has aged out of is absent rather than an empty map, which is what lets the
 * Stats series tell "not measured" from "not used".
 */
internal fun secondsByDay(
    intervals: List<ForegroundInterval>,
    eventTimes: List<Long>,
    windows: List<DayWindow>,
): Map<String, Map<String, Long>> = windows.mapNotNull { window ->
    val clipped = intervals.mapNotNull { span ->
        val begin = maxOf(span.beginMillis, window.beginMillis)
        val end = minOf(span.endMillis, window.endMillis)
        if (end > begin) span.packageName to end - begin else null
    }
    val hasEvent = eventTimes.any { it >= window.beginMillis && it < window.endMillis }
    if (clipped.isEmpty() && !hasEvent) return@mapNotNull null
    window.date to clipped.groupBy({ it.first }, { it.second })
        .mapValues { (_, millis) -> millis.sum() / MILLIS_PER_SECOND }
}.toMap()

private const val MILLIS_PER_SECOND = 1000L
