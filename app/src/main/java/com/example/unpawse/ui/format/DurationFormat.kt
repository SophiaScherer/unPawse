package com.example.unpawse.ui.format

/**
 * Compact duration copy shared by every screen that shows time: "45m", "2h", "2h 15m". Matches the
 * mockup's phrasing. Pure, so it's unit-tested without a device.
 */
fun formatMinutes(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    val hours = safe / 60
    val remainder = safe % 60
    return when {
        hours == 0 -> "${remainder}m"
        remainder == 0 -> "${hours}h"
        else -> "${hours}h ${remainder}m"
    }
}

/** Same shape, but from seconds — the unit `daily_usage` stores. */
fun formatSeconds(seconds: Long): String = formatMinutes((seconds / 60).toInt())
