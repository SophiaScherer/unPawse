package com.example.unpawse.data.usage

/**
 * One app's usage for one day: how much screen time was spent and how many bonus seconds were
 * earned back (via cat captures). Stored in **seconds** for precision — the foreground monitor
 * accrues sub-minute ticks — with minute conveniences for the UI.
 */
data class DailyUsage(
    val packageName: String,
    /** ISO-8601 local date, e.g. "2026-07-15"; the daily-reset key. */
    val date: String,
    val usedSeconds: Long,
    val earnedSeconds: Long,
) {
    val usedMinutes: Int get() = (usedSeconds / 60).toInt()
    val earnedMinutes: Int get() = (earnedSeconds / 60).toInt()
}
