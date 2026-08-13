package com.example.unpawse.data.capture

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Streak math over capture dates. Lives beside the domain model rather than in `ui/home` because
 * Home, Stats and the capture path all need it, and a ViewModel shouldn't reach into another
 * feature's mapper for it. Derived from timestamps, never stored — see `Achievements.kt`, whose
 * `firstStreakDate` answers a different question (*when* a run completed, not how long one is).
 */

/** A streak worth celebrating: the Home banner's threshold, and what makes a capture a bonus. */
internal const val STREAK_CELEBRATION_DAYS = 3

/**
 * Consecutive days up to today with at least one capture. Today not being photographed *yet*
 * doesn't break a streak — it's still in progress — so counting starts from yesterday in that case.
 */
internal fun currentStreakDays(captureDates: Set<LocalDate>, today: LocalDate): Int {
    var day = if (today in captureDates) today else today.minusDays(1)
    if (day !in captureDates) return 0

    var streak = 0
    while (day in captureDates) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}

/** The longest run of consecutive capture days ever recorded. */
internal fun longestStreakDays(captureDates: Set<LocalDate>): Int {
    if (captureDates.isEmpty()) return 0

    val sorted = captureDates.sorted()
    var longest = 1
    var run = 1
    for (i in 1 until sorted.size) {
        run = if (sorted[i] == sorted[i - 1].plusDays(1)) run + 1 else 1
        longest = maxOf(longest, run)
    }
    return longest
}

/**
 * Whether the capture about to be saved is a streak bonus: the first of [today], landing a streak of
 * at least [milestoneDays]. Every qualifying day counts, not only the milestone day itself.
 *
 * [existingCaptureDates] must be read *before* the insert — afterwards today is already a capture
 * day and this can only answer false.
 */
internal fun isStreakMilestone(
    existingCaptureDates: Set<LocalDate>,
    today: LocalDate,
    milestoneDays: Int = STREAK_CELEBRATION_DAYS,
): Boolean =
    today !in existingCaptureDates &&
        currentStreakDays(existingCaptureDates + today, today) >= milestoneDays

internal fun Long.toLocalDate(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
