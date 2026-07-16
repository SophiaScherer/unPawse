package com.example.unpawse.ui.stats

import com.example.unpawse.data.capture.Capture
import com.example.unpawse.data.usage.DailyUsage
import com.example.unpawse.data.usage.MonitoredApp
import com.example.unpawse.ui.format.formatSeconds
import com.example.unpawse.ui.home.longestStreakDays
import com.example.unpawse.ui.home.toLocalDate
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Two weeks of history: this week for the chart, last week for the trend comparison. */
const val STATS_HISTORY_DAYS = 14L

private const val DAYS_IN_WEEK = 7
private const val TREND_BAR_COUNT = 5
private const val SECONDS_PER_HOUR = 3600f

/**
 * Builds [StatsUiState] from usage history + captures. Pure and parameterised on [today]/[zone] so
 * it's unit-testable without a clock.
 *
 * [recentUsage] must cover the last [STATS_HISTORY_DAYS] days. Days with no usage have no row, so
 * everything here fills gaps with zero rather than assuming a dense series.
 *
 * Three fields have **no data behind them** and are deliberately blanked rather than left showing
 * `sample()`'s invented figures — a fabricated "42 interruptions prevented" next to real numbers
 * reads as fact and would quietly ship as a lie. They need real features first:
 *  - `preventedCount` — blocks aren't recorded anywhere; needs a block-events table.
 *  - `unlocks` — device unlocks aren't tracked at all.
 *  - `achievements` — there's no rules engine to award any.
 */
internal fun toStatsUiState(
    monitoredApps: List<MonitoredApp>,
    recentUsage: List<DailyUsage>,
    captures: List<Capture>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): StatsUiState {
    val usedByDate = recentUsage.groupBy({ it.date }, { it.usedSeconds })
        .mapValues { (_, seconds) -> seconds.sum() }

    fun usedOn(date: LocalDate): Long = usedByDate[date.toString()] ?: 0L

    // Monday-to-Sunday of the current week, matching the fixed MON..SUN axis labels.
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val week = (0 until DAYS_IN_WEEK).map { monday.plusDays(it.toLong()) }

    val todaySeconds = usedOn(today)
    val yesterdaySeconds = usedOn(today.minusDays(1))

    val thisWeekSeconds = (0 until DAYS_IN_WEEK).sumOf { usedOn(today.minusDays(it.toLong())) }
    val lastWeekSeconds = (DAYS_IN_WEEK until DAYS_IN_WEEK * 2)
        .sumOf { usedOn(today.minusDays(it.toLong())) }

    val enabled = monitoredApps.filter { it.enabled }
    val captureDates = captures.map { it.capturedAt.toLocalDate(zone) }.toSet()

    return StatsUiState.sample().copy(
        dailyTotal = formatSeconds(todaySeconds),
        deltaText = deltaText(todaySeconds, yesterdaySeconds),
        // "Positive" means usage went *up* — the screen renders it as the unwelcome direction.
        deltaIsPositive = todaySeconds > yesterdaySeconds,
        weeklyPoints = week.map { usedOn(it) / SECONDS_PER_HOUR },
        highlightDayIndex = today.dayOfWeek.value - 1,
        trendLabel = trendLabel(thisWeekSeconds - lastWeekSeconds),
        trendBars = trendBars { day -> usedOn(today.minusDays(day)) },
        productivePercent = budgetLeftPercent(enabled, recentUsage, today),
        breakdown = topAppsBreakdown(enabled, recentUsage, today),
        longestStreak = "${longestStreakDays(captureDates)} Days",
        capturedPhotos = "${captures.size} Photos",
        // Blanked until there's data behind them — see the KDoc above.
        preventedCount = 0,
        unlocks = NO_DATA,
        achievements = emptyList(),
    )
}

/** Shown where a metric has no backing data yet, rather than an invented number. */
private const val NO_DATA = "—"

private fun deltaText(todaySeconds: Long, yesterdaySeconds: Long): String = when {
    yesterdaySeconds == 0L -> "No data for yesterday"
    else -> {
        val percent = ((todaySeconds - yesterdaySeconds) * 100f / yesterdaySeconds).roundToInt()
        "${abs(percent)}% from yesterday"
    }
}

private fun trendLabel(deltaSeconds: Long): String {
    val hours = deltaSeconds / SECONDS_PER_HOUR
    val sign = if (hours > 0) "+" else "-"
    return "$sign${String.format("%.1f", abs(hours))}h"
}

/** Last [TREND_BAR_COUNT] days, oldest first, normalised against the busiest of them. */
private fun trendBars(usedOn: (Long) -> Long): List<Float> {
    val days = (TREND_BAR_COUNT - 1 downTo 0).map { usedOn(it.toLong()) }
    val peak = days.maxOrNull() ?: 0L
    return if (peak == 0L) List(TREND_BAR_COUNT) { 0f } else days.map { it.toFloat() / peak }
}

/** How much of today's total budget is still unspent, as a percentage. */
private fun budgetLeftPercent(
    enabledApps: List<MonitoredApp>,
    recentUsage: List<DailyUsage>,
    today: LocalDate,
): Int {
    val todayByPackage = recentUsage.filter { it.date == today.toString() }.associateBy { it.packageName }
    val budget = enabledApps.sumOf { it.dailyLimitMinutes.toLong() * 60 }
    if (budget == 0L) return 0

    val used = enabledApps.sumOf { todayByPackage[it.packageName]?.usedSeconds ?: 0 }
    val earned = enabledApps.sumOf { todayByPackage[it.packageName]?.earnedSeconds ?: 0 }
    val left = (budget + earned - used).coerceAtLeast(0)
    return ((left * 100) / (budget + earned)).toInt().coerceIn(0, 100)
}

/**
 * The donut/legend: today's most-used monitored apps. The mockup groups by category
 * (Social/Productivity/Entertainment) but nothing classifies apps, so this shows real per-app usage
 * and reuses [UsageColor] purely as a three-colour palette.
 */
private fun topAppsBreakdown(
    enabledApps: List<MonitoredApp>,
    recentUsage: List<DailyUsage>,
    today: LocalDate,
): List<UsageCategory> {
    val todayByPackage = recentUsage.filter { it.date == today.toString() }.associateBy { it.packageName }
    val palette = UsageColor.entries

    return enabledApps
        .map { it to (todayByPackage[it.packageName]?.usedSeconds ?: 0L) }
        .filter { (_, seconds) -> seconds > 0 }
        .sortedByDescending { (_, seconds) -> seconds }
        .take(palette.size)
        .mapIndexed { index, (app, seconds) ->
            UsageCategory(
                label = app.appLabel,
                duration = formatSeconds(seconds),
                color = palette[index % palette.size],
            )
        }
}
