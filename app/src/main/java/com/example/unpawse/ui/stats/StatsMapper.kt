package com.example.unpawse.ui.stats

import com.example.unpawse.data.capture.Capture
import com.example.unpawse.data.usage.AppCategory
import com.example.unpawse.data.usage.DailyUsage
import com.example.unpawse.data.usage.MonitoredApp
import com.example.unpawse.ui.format.formatSeconds
import com.example.unpawse.ui.home.longestStreakDays
import com.example.unpawse.ui.home.toLocalDate
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
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
 * Two fields still have **no data behind them** and are deliberately blanked rather than left showing
 * `sample()`'s invented figures — a fabricated "24/day" next to real numbers reads as fact and would
 * quietly ship as a lie. They need real features first:
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

    // Calendar weeks, deliberately the *same* Mon–Sun weeks the chart draws. These used to be
    // rolling 7-day windows, so on a Monday the trend counted days that the chart didn't show at
    // all — "usage up 0.6h this week" sat next to a chart that was flat all week.
    val thisWeekSeconds = week.sumOf(::usedOn)
    val lastWeekSeconds = (1..DAYS_IN_WEEK).sumOf { usedOn(monday.minusDays(it.toLong())) }
    val trendDeltaSeconds = thisWeekSeconds - lastWeekSeconds

    // Blocks over the same Mon–Sun week the chart draws and the trend compares — the card says
    // "THIS WEEK" on its face, and all three must agree on which week that is.
    val weekKeys = week.mapTo(mutableSetOf()) { it.toString() }
    val preventedThisWeek = recentUsage.filter { it.date in weekKeys }.sumOf { it.blockedCount }

    val enabled = monitoredApps.filter { it.enabled }
    val captureDates = captures.map { it.capturedAt.toLocalDate(zone) }.toSet()

    return StatsUiState.sample().copy(
        dailyTotal = formatSeconds(todaySeconds),
        deltaText = deltaText(todaySeconds, yesterdaySeconds),
        // "Positive" means usage went *up* — the screen renders it as the unwelcome direction.
        deltaIsPositive = todaySeconds > yesterdaySeconds,
        deltaHasBaseline = yesterdaySeconds > 0L,
        weeklyPoints = week.map { usedOn(it) / SECONDS_PER_HOUR },
        highlightDayIndex = today.dayOfWeek.value - 1,
        trendLabel = trendLabel(trendDeltaSeconds),
        // Usage going *up* is the unwelcome direction, same convention as deltaIsPositive.
        trendIsUp = trendDeltaSeconds > 0,
        trendBars = trendBars { day -> usedOn(today.minusDays(day)) },
        productivePercent = budgetLeftPercent(enabled, recentUsage, today),
        breakdown = categoryBreakdown(enabled, recentUsage, today),
        longestStreak = dayCountLabel(longestStreakDays(captureDates)),
        capturedPhotos = "${captures.size} Photos",
        preventedCount = preventedThisWeek,
        // Blanked until there's data behind them — see the KDoc above.
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

/** "3 Days", but "1 Day" — the count used to be pluralised unconditionally. */
internal fun dayCountLabel(days: Int): String = if (days == 1) "1 Day" else "$days Days"

/**
 * Week-over-week change, signed. Zero is written without a sign: `-0.0h` was reachable whenever
 * the two weeks matched exactly, which reads as a decrease that didn't happen.
 */
internal fun trendLabel(deltaSeconds: Long): String {
    val hours = deltaSeconds / SECONDS_PER_HOUR
    val rounded = String.format(Locale.US, "%.1f", abs(hours))
    val sign = when {
        rounded == "0.0" -> ""
        hours > 0 -> "+"
        else -> "-"
    }
    return "$sign${rounded}h"
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
 * The donut/legend: today's screen time grouped into [AppCategory] buckets, as the mockup shows.
 *
 * Emitted in **declaration order, not by size**. Colour is semantic here — Social is the primary
 * plum whether it is the biggest slice or the smallest — so sorting would make the same category
 * change colour from one day to the next. Buckets with no time today are dropped entirely rather
 * than drawn as a zero-width arc with a "0m" legend row.
 */
private fun categoryBreakdown(
    enabledApps: List<MonitoredApp>,
    recentUsage: List<DailyUsage>,
    today: LocalDate,
): List<UsageCategory> {
    val todayByPackage = recentUsage.filter { it.date == today.toString() }.associateBy { it.packageName }

    val secondsByCategory = enabledApps
        .groupBy { it.category }
        .mapValues { (_, apps) -> apps.sumOf { todayByPackage[it.packageName]?.usedSeconds ?: 0L } }

    return AppCategory.entries.mapNotNull { category ->
        val seconds = secondsByCategory[category] ?: 0L
        if (seconds <= 0L) return@mapNotNull null
        UsageCategory(
            label = category.label,
            duration = formatSeconds(seconds),
            seconds = seconds,
            color = category.toUsageColor(),
        )
    }
}

private fun AppCategory.toUsageColor(): UsageColor = when (this) {
    AppCategory.SOCIAL -> UsageColor.SOCIAL
    AppCategory.PRODUCTIVITY -> UsageColor.PRODUCTIVITY
    AppCategory.ENTERTAINMENT -> UsageColor.ENTERTAINMENT
    AppCategory.OTHER -> UsageColor.OTHER
}
