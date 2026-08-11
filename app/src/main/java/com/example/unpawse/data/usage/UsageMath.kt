package com.example.unpawse.data.usage

import java.time.DayOfWeek

/**
 * Pure limit arithmetic, extracted from [UsageRepository] so it's unit-testable without Room or a
 * device (same spirit as `classify` in the ML layer). "Remaining" folds in both the daily limit and
 * any bonus minutes earned back from cat captures.
 */
internal const val SECONDS_PER_MINUTE = 60L

/**
 * "No cap today" — the value a weekend override carries when the user wants an app left alone at
 * weekends.
 *
 * Deliberately negative rather than zero: a limit of **0 already means "over budget from the first
 * second"** everywhere in the existing enforcement path, and several tracker tests rely on exactly
 * that. Overloading zero would have flipped those from "blocked instantly" to "never blocked".
 */
const val UNLIMITED_MINUTES = -1

/**
 * The budget that applies on [day]: the weekend override when there is one and the day is a
 * weekend, otherwise the everyday figure. A null override means "same as the rest of the week",
 * which is the default — most apps want one number.
 */
internal fun effectiveLimitMinutes(
    dailyLimitMinutes: Int,
    weekendLimitMinutes: Int?,
    day: DayOfWeek,
): Int = if (weekendLimitMinutes != null && isWeekend(day)) {
    weekendLimitMinutes
} else {
    dailyLimitMinutes
}

internal fun isWeekend(day: DayOfWeek): Boolean =
    day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY

/** Signed remaining seconds for today: `limit + earned − used`. Negative means over the limit. */
internal fun remainingSeconds(limitMinutes: Int, usedSeconds: Long, earnedSeconds: Long): Long =
    limitMinutes.toLong() * SECONDS_PER_MINUTE - usedSeconds + earnedSeconds

/**
 * Remaining whole minutes for display — floored at zero (never shows negative time left), or `null`
 * when the day is uncapped. Null rather than a huge number so callers must decide what "no limit"
 * looks like instead of rendering "8h 0m left" and implying one.
 */
internal fun remainingMinutes(limitMinutes: Int, usedSeconds: Long, earnedSeconds: Long): Int? {
    if (isUnlimited(limitMinutes)) return null
    return (remainingSeconds(limitMinutes, usedSeconds, earnedSeconds).coerceAtLeast(0) / SECONDS_PER_MINUTE).toInt()
}

/** Whether today's budget is exhausted (used ≥ limit + earned). Drives the block trigger. */
internal fun isLimitReached(limitMinutes: Int, usedSeconds: Long, earnedSeconds: Long): Boolean {
    if (isUnlimited(limitMinutes)) return false
    return remainingSeconds(limitMinutes, usedSeconds, earnedSeconds) <= 0
}

/** Any negative budget reads as uncapped, so a stray −2 can't be mistaken for a real limit. */
internal fun isUnlimited(limitMinutes: Int): Boolean = limitMinutes < 0
