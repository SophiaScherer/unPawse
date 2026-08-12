package com.example.unpawse.data.unlocks

/**
 * Device unlocks for one day. Domain model kept decoupled from the Room entity, same pattern as
 * `DailyUsage`/`DailyUsageEntity`.
 *
 * Counted only while the monitor service is running — nothing observes `ACTION_USER_PRESENT`
 * otherwise — so the Stats card says so on its face rather than implying a complete tally.
 */
data class DailyUnlocks(
    /** ISO-8601 local date, e.g. "2026-07-15". */
    val date: String,
    val unlockCount: Int,
)
