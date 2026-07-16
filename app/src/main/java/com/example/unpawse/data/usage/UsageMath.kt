package com.example.unpawse.data.usage

/**
 * Pure limit arithmetic, extracted from [UsageRepository] so it's unit-testable without Room or a
 * device (same spirit as `classify` in the ML layer). "Remaining" folds in both the daily limit and
 * any bonus minutes earned back from cat captures.
 */
internal const val SECONDS_PER_MINUTE = 60L

/** Signed remaining seconds for today: `limit + earned − used`. Negative means over the limit. */
internal fun remainingSeconds(limitMinutes: Int, usedSeconds: Long, earnedSeconds: Long): Long =
    limitMinutes.toLong() * SECONDS_PER_MINUTE - usedSeconds + earnedSeconds

/** Remaining whole minutes for display — floored at zero (never shows negative time left). */
internal fun remainingMinutes(limitMinutes: Int, usedSeconds: Long, earnedSeconds: Long): Int =
    (remainingSeconds(limitMinutes, usedSeconds, earnedSeconds).coerceAtLeast(0) / SECONDS_PER_MINUTE).toInt()

/** Whether today's budget is exhausted (used ≥ limit + earned). Drives the block trigger. */
internal fun isLimitReached(limitMinutes: Int, usedSeconds: Long, earnedSeconds: Long): Boolean =
    remainingSeconds(limitMinutes, usedSeconds, earnedSeconds) <= 0
