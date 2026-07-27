package com.example.unpawse.service

import com.example.unpawse.ui.format.formatMinutes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One limited app's share of the day, already resolved to a display label. */
data class AppUsageSummary(val label: String, val minutes: Int)

/** Fixed heading for the recap notification. */
const val DAILY_SUMMARY_TITLE = "Your day in review"

/**
 * Body copy for the daily recap.
 *
 * Reports only what was measured. A day with nothing on it says so rather than dressing up a zero —
 * the same rule the Stats screen follows by blanking figures it cannot compute. Pure, so every
 * branch is unit-tested.
 */
fun buildDailySummary(apps: List<AppUsageSummary>, captureCount: Int): String {
    val used = apps.filter { it.minutes > 0 }.sortedByDescending { it.minutes }
    val totalMinutes = used.sumOf { it.minutes }

    val screenTime = when {
        used.isEmpty() -> "No time on your limited apps today."
        used.size == 1 -> "${formatMinutes(totalMinutes)} in ${used.first().label}."
        else -> {
            val top = used.first()
            "${formatMinutes(totalMinutes)} across ${used.size} apps, " +
                "most of it in ${top.label} (${formatMinutes(top.minutes)})."
        }
    }

    val cats = when (captureCount) {
        0 -> null
        1 -> "You photographed 1 cat."
        else -> "You photographed $captureCount cats."
    }

    return listOfNotNull(screenTime, cats).joinToString(" ")
}

/**
 * Milliseconds until the next [hour] o'clock in [zone] — used as the recap job's initial delay so
 * it lands at the end of the day rather than whenever the toggle happened to be switched on.
 * Exactly on the hour counts as already past, so the job never fires twice for one day.
 */
fun millisUntilNextHour(nowMillis: Long, hour: Int, zone: ZoneId): Long {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val todayAt = now.truncatedTo(ChronoUnit.DAYS).withHour(hour)
    val next = if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
    return next.toInstant().toEpochMilli() - nowMillis
}

/** Epoch-millis start of [date] in [zone]; the boundary for "captured today". */
fun startOfDayMillis(date: LocalDate, zone: ZoneId): Long =
    date.atStartOfDay(zone).toInstant().toEpochMilli()
