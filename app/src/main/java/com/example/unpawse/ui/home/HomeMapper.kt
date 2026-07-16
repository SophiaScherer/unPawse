package com.example.unpawse.ui.home

import com.example.unpawse.data.capture.Capture
import com.example.unpawse.data.usage.DailyUsage
import com.example.unpawse.data.usage.MonitoredApp
import com.example.unpawse.ui.format.formatMinutes
import com.example.unpawse.ui.format.formatSeconds
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a")

/**
 * Builds [HomeUiState] from today's usage + the capture history. Pure and parameterised on
 * [today]/[zone] so it's unit-testable without a clock (same shape as `GalleryMapper`).
 *
 * Not everything on Home has data behind it yet — see [HomeUiState.sample] for the fields still
 * carrying placeholder copy (greeting/user, next-break countdown, banner). Those are copy rather
 * than metrics, so a placeholder is honest enough; the numbers are all real.
 */
internal fun toHomeUiState(
    monitoredApps: List<MonitoredApp>,
    todayUsage: List<DailyUsage>,
    captures: List<Capture>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): HomeUiState {
    val enabled = monitoredApps.filter { it.enabled }
    val usageByPackage = todayUsage.associateBy { it.packageName }

    val budgetSeconds = enabled.sumOf { it.dailyLimitMinutes.toLong() * 60 }
    val usedSeconds = enabled.sumOf { usageByPackage[it.packageName]?.usedSeconds ?: 0 }
    val earnedSeconds = enabled.sumOf { usageByPackage[it.packageName]?.earnedSeconds ?: 0 }
    val remainingSeconds = (budgetSeconds + earnedSeconds - usedSeconds).coerceAtLeast(0)

    val captureDates = captures.map { it.capturedAt.toLocalDate(zone) }.toSet()
    val capturesToday = captures.filter { it.capturedAt.toLocalDate(zone) == today }

    return HomeUiState.sample().copy(
        screenTimeUsedLabel = formatSeconds(usedSeconds),
        // Guard against a zero budget (nothing monitored) rather than dividing by zero.
        progressFraction = if (budgetSeconds == 0L) 0f else (usedSeconds.toFloat() / budgetSeconds).coerceIn(0f, 1f),
        remainingLabel = formatSeconds(remainingSeconds),
        streakDays = currentStreakDays(captureDates, today),
        catCount = capturesToday.size,
        pausedAppsCount = enabled.size,
        activities = buildActivities(enabled, usageByPackage, capturesToday, zone),
    )
}

/**
 * Recent activity, most urgent first: apps blocked *right now*, then today's verified cats.
 *
 * Block events aren't persisted (there's no event table), so a "blocked" row is derived from an
 * app currently being over budget — which is why its time reads "Now" rather than a timestamp.
 */
private fun buildActivities(
    enabledApps: List<MonitoredApp>,
    usageByPackage: Map<String, DailyUsage>,
    capturesToday: List<Capture>,
    zone: ZoneId,
): List<ActivityItem> {
    val blocked = enabledApps.mapNotNull { app ->
        val usage = usageByPackage[app.packageName] ?: return@mapNotNull null
        val remaining = app.dailyLimitMinutes.toLong() * 60 - usage.usedSeconds + usage.earnedSeconds
        if (remaining > 0) return@mapNotNull null
        ActivityItem(
            kind = ActivityKind.BLOCKED,
            title = "${app.appLabel} Blocked",
            subtitle = "Daily limit of ${formatMinutes(app.dailyLimitMinutes)} reached.",
            time = "Now",
        )
    }

    val verified = capturesToday.map { capture ->
        ActivityItem(
            kind = ActivityKind.VERIFIED,
            title = "Cat Verified",
            subtitle = "${(capture.confidence * 100).toInt()}% match. Time earned back.",
            time = Instant.ofEpochMilli(capture.capturedAt).atZone(zone).format(TIME_FORMAT),
        )
    }

    return blocked + verified
}

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

internal fun Long.toLocalDate(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
