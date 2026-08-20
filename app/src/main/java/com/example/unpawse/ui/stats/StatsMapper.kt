package com.example.unpawse.ui.stats

import com.example.unpawse.data.capture.Capture
import com.example.unpawse.data.unlocks.DailyUnlocks
import com.example.unpawse.data.usage.AppCategory
import com.example.unpawse.data.usage.DailyUsage
import com.example.unpawse.data.usage.MonitoredApp
import com.example.unpawse.data.usage.dailyBudget
import com.example.unpawse.ui.format.avatarInitialFor
import com.example.unpawse.ui.format.NO_DATA
import com.example.unpawse.ui.format.formatSeconds
import com.example.unpawse.data.capture.longestStreakDays
import com.example.unpawse.data.capture.toLocalDate
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Two weeks of history: this week for the chart, last week for the trend comparison. */
const val STATS_HISTORY_DAYS = 14L

private const val DAYS_IN_WEEK = 7
private const val SECONDS_PER_HOUR = 3600f

/** The chart's fixed axis. Monday-first, matching the Mon–Sun week the chart and trend both use. */
internal val WEEKDAY_LABELS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

/** The trend's period, on the card's face — the rule the "THIS WEEK" line on Prevented follows. */
private const val TREND_CAPTION = "VS LAST WEEK, SAME DAYS"
private const val TREND_NO_BASELINE_CAPTION = "NO DATA FOR LAST WEEK"

/** Not [NO_DATA]: a library with nothing in it is a known fact, not a missing measurement. */
private const val NO_PHOTOS_LABEL = "No photos yet"

/**
 * Builds [StatsUiState] from usage history + captures. Pure and parameterised on [today]/[zone] so
 * it's unit-testable without a clock.
 *
 * [recentUsage] must cover the last [STATS_HISTORY_DAYS] days. Days with no usage have no row, so
 * everything here fills gaps with zero rather than assuming a dense series.
 *
 * Every metric on the screen is now backed by real data. [allUsage] is the *whole* usage history and
 * exists only for the achievement rules: a badge's earned-on date derived from the 14-day chart
 * window would silently un-earn itself as the window slid past it. It defaults to [recentUsage] so
 * callers that only care about the charts don't have to supply it.
 */
internal fun toStatsUiState(
    monitoredApps: List<MonitoredApp>,
    recentUsage: List<DailyUsage>,
    captures: List<Capture>,
    unlocks: List<DailyUnlocks> = emptyList(),
    allUsage: List<DailyUsage> = recentUsage,
    /** Blank is the stored "not set" state, so the header falls back like everywhere else. */
    userName: String = "",
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

    // Calendar weeks, deliberately the *same* Mon–Sun week the chart draws. These used to be
    // rolling 7-day windows, so on a Monday the trend counted days that the chart didn't show at
    // all — "usage up 0.6h this week" sat next to a chart that was flat all week.
    //
    // Both sides stop at the same weekday. Last week used to be summed whole against a
    // Monday-to-today this week, so on a Wednesday it was 3 days measured against 7 — hugely
    // negative every Monday and drifting upward all week whatever the user actually did.
    val elapsedThisWeek = week.take(today.dayOfWeek.value)
    val thisWeekSeconds = elapsedThisWeek.sumOf(::usedOn)
    val lastWeekSeconds = elapsedThisWeek.sumOf { usedOn(it.minusDays(DAYS_IN_WEEK.toLong())) }
    val trendDeltaSeconds = thisWeekSeconds - lastWeekSeconds
    // No last week means nothing to compare against, so neither a figure nor an arrow is drawn —
    // the same rule deltaHasBaseline carries one card over.
    val trendHasBaseline = lastWeekSeconds > 0L

    // Blocks over the same Mon–Sun week the chart draws and the trend compares — the card says
    // "THIS WEEK" on its face, and all three must agree on which week that is.
    val weekKeys = week.mapTo(mutableSetOf()) { it.toString() }
    val preventedThisWeek = recentUsage.filter { it.date in weekKeys }.sumOf { it.blockedCount }

    val enabled = monitoredApps.filter { it.enabled }
    // Built once: the donut and the budget figure must agree on what today contained.
    val todayByPackage = recentUsage.filter { it.date == today.toString() }.associateBy { it.packageName }
    // One entry per capture — the achievement rules count them as well as date them, so this is a
    // list; the streak helpers below take the de-duplicated set.
    val captureDayList = captures.map { it.capturedAt.toLocalDate(zone) }
    val captureDates = captureDayList.toSet()

    // The donut's centre is summed from its own slices, so the two cannot drift apart. Deliberately
    // not `todaySeconds`, which counts monitored-but-disabled apps the breakdown leaves out.
    val breakdown = categoryBreakdown(enabled, todayByPackage)

    // Constructed field by field, deliberately **not** `StatsUiState.sample().copy(...)`. Every
    // value on this screen is computed now, so the only things `sample()` was still supplying were
    // two constants — and inheriting from it left a standing route for mockup data to reach the
    // screen the moment someone added a field and forgot to set it here. Same move already made in
    // `SettingsMapper`; `sample()` is now @Preview-only.
    return StatsUiState(
        avatarInitial = avatarInitialFor(userName),
        dailyTotal = formatSeconds(todaySeconds),
        deltaText = deltaText(todaySeconds, yesterdaySeconds),
        // "Positive" means usage went *up* — the screen renders it as the unwelcome direction.
        deltaIsPositive = todaySeconds > yesterdaySeconds,
        deltaHasBaseline = yesterdaySeconds > 0L,
        // Null after today: the chart draws no mark for a day that hasn't happened. Plotting it as
        // zero put Thu–Sun on the floor, and the smoothed curve dived off a cliff after today —
        // four days of abstinence, drawn from four days that don't exist yet.
        weeklyPoints = week.map { if (it.isAfter(today)) null else usedOn(it) / SECONDS_PER_HOUR },
        weekdayLabels = WEEKDAY_LABELS,
        highlightDayIndex = today.dayOfWeek.value - 1,
        trendLabel = if (trendHasBaseline) trendLabel(trendDeltaSeconds) else NO_DATA,
        // Usage going *up* is the unwelcome direction, same convention as deltaIsPositive.
        trendIsUp = trendDeltaSeconds > 0,
        trendHasBaseline = trendHasBaseline,
        trendCaption = if (trendHasBaseline) TREND_CAPTION else TREND_NO_BASELINE_CAPTION,
        trendBars = weekBars(week, today, ::usedOn),
        breakdownTotal = formatSeconds(breakdown.sumOf { it.seconds }),
        breakdown = breakdown,
        budgetLeftLabel = budgetLeftLabel(enabled, todayByPackage, today),
        longestStreak = dayCountLabel(longestStreakDays(captureDates)),
        // "0 Photos" under a party popper celebrates nothing; the card goes neutral and asks
        // instead. The count is the flag, so the screen never has to parse the label back.
        capturedPhotos = if (captures.isEmpty()) NO_PHOTOS_LABEL else "${captures.size} Photos",
        hasCapturedPhotos = captures.isNotEmpty(),
        preventedCount = preventedThisWeek,
        unlocks = unlocksLabel(unlocks, today),
        achievements = toAchievements(
            evaluateAchievements(
                AchievementInput(
                    captureDates = captureDayList,
                    usage = allUsage,
                    monitoredApps = monitoredApps,
                    today = today,
                ),
            ),
        ),
    )
}

/**
 * Budget headroom as a percentage, or [NO_DATA] when nothing monitored has a cap. A day made
 * entirely of uncapped apps has no headroom to express, and "0%" would read as "none left".
 */
private fun budgetLeftLabel(
    enabledApps: List<MonitoredApp>,
    todayByPackage: Map<String, DailyUsage>,
    today: LocalDate,
): String {
    val percent = dailyBudget(enabledApps, todayByPackage, today.dayOfWeek)?.leftPercent ?: return NO_DATA
    return "$percent%"
}

/**
 * Today's unlock count, or [NO_DATA] when the store has never seen a single unlock — which is the
 * honest reading of "the monitor service may never have run on this device".
 *
 * Deliberately **today's count, not the mockup's "24/day" average**. Unlocks are only observed while
 * the service is alive, so an average would divide a partial tally by a number of days it wasn't
 * really measuring — precisely the plausible-looking fabrication the blanking rule exists to stop.
 * A day with rows but none today is a real zero, not missing data.
 */
private fun unlocksLabel(unlocks: List<DailyUnlocks>, today: LocalDate): String {
    if (unlocks.isEmpty()) return NO_DATA
    return unlocks.filter { it.date == today.toString() }.sumOf { it.unlockCount }.toString()
}

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

/**
 * The Trend card's sparkline, normalised against the busiest day of the week.
 *
 * Drawn over the **same Mon–Sun week the headline compares**. It used to be a rolling five days,
 * so one small card held two different windows with nothing to tell them apart.
 *
 * A day still to come is `null`, not `0f`: it has no value to draw, and a zero would claim a day
 * spent off the phone. Same distinction [StatsUiState.weeklyPoints] makes.
 */
private fun weekBars(
    week: List<LocalDate>,
    today: LocalDate,
    usedOn: (LocalDate) -> Long,
): List<Float?> {
    val elapsed = week.filterNot { it.isAfter(today) }
    val peak = elapsed.maxOfOrNull(usedOn) ?: 0L
    return week.map { day ->
        when {
            day.isAfter(today) -> null
            peak == 0L -> 0f
            else -> usedOn(day).toFloat() / peak
        }
    }
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
    todayByPackage: Map<String, DailyUsage>,
): List<UsageCategory> {
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
