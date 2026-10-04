package com.example.unpawse.ui.stats

import com.example.unpawse.data.capture.Capture
import com.example.unpawse.data.unlocks.DailyUnlocks
import com.example.unpawse.data.usage.AppCategory
import com.example.unpawse.data.usage.DailyUsage
import com.example.unpawse.data.usage.MonitoredApp
import com.example.unpawse.data.usage.UsageScope
import com.example.unpawse.data.usage.UsageSeries
import com.example.unpawse.data.usage.dailyBudget
import com.example.unpawse.data.usage.firstMeasuredDay
import com.example.unpawse.data.usage.trackedUsageSeries
import com.example.unpawse.ui.format.avatarInitialFor
import com.example.unpawse.ui.format.NO_DATA
import com.example.unpawse.ui.format.formatSeconds
import com.example.unpawse.ui.format.countLabel
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

/**
 * The trend's captions when there is nothing to compare: no complete day yet (every Monday), or no
 * measured last week. Short enough to stay on one line in the half-width card.
 */
private const val TREND_NO_FULL_DAY_CAPTION = "NO FULL DAY YET"
private const val TREND_NO_BASELINE_CAPTION = "NO LAST WEEK DATA"

/** Not [NO_DATA]: a library with nothing in it is a known fact, not a missing measurement. */
private const val NO_PHOTOS_LABEL = "No photos yet"

/**
 * Which apps the figures count, on the cards' faces. Same rule as the trend's period and Budget
 * Left's "ACROSS CAPPED APPS": a screen-time number whose scope isn't stated invites being read as
 * the other one.
 */
private val SCOPE_CAPTIONS = mapOf(
    UsageScope.TRACKED to "TRACKED APPS",
    UsageScope.ALL to "ALL APPS ON THIS PHONE",
)

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
    today: LocalDate,
    zone: ZoneId,
    scope: UsageScope = UsageScope.TRACKED,
    series: UsageSeries? = trackedUsageSeries(
        recentUsage,
        monitoredApps,
        today,
        measuredSince = firstMeasuredDay(allUsage + recentUsage, today),
    ),
    /** The scope's figures are still being read; blank, but not "unavailable". */
    scopeLoading: Boolean = false,
): StatsUiState {
    // Null is the scope having no figures at all, not a quiet phone: all-apps without usage access.
    // Every scoped metric blanks, and the tracked-only tiles below carry on reporting.
    val measured = series != null
    val usedByDate = series?.secondsByDate.orEmpty()

    fun usedOn(date: LocalDate): Long = usedByDate[date.toString()] ?: 0L

    // Before the series began, a day is unknown rather than zero: no mark on the chart, and no
    // comparison may lean on it.
    fun measuredOn(date: LocalDate): Boolean = series != null && !date.isBefore(series.measuredSince)

    // Monday-to-Sunday of the current week, matching the fixed MON..SUN axis labels.
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val week = (0 until DAYS_IN_WEEK).map { monday.plusDays(it.toLong()) }

    val todaySeconds = usedOn(today)
    val yesterdaySeconds = usedOn(today.minusDays(1))

    // Calendar weeks, deliberately the *same* Mon–Sun week the chart draws, and both sides cover
    // the same weekdays. Rolling windows and a whole last week against a partial this week each
    // produced a figure that moved with the calendar rather than with the user.
    //
    // Completed days only: today against a whole day last week read as a big drop every morning,
    // shrinking as the day went on — worst on a Monday, when today was the entire comparison.
    val completedThisWeek = week.take(today.dayOfWeek.value - 1)
    val thisWeekSeconds = completedThisWeek.sumOf(::usedOn)
    val lastWeekSeconds = completedThisWeek.sumOf { usedOn(it.minusDays(DAYS_IN_WEEK.toLong())) }
    val trendDeltaSeconds = thisWeekSeconds - lastWeekSeconds
    // No last week means nothing to compare against, so neither a figure nor an arrow is drawn —
    // the same rule deltaHasBaseline carries one card over. Every compared day must also have been
    // measured: in the first week after install, or past the platform's retention in all-apps
    // scope, last week is partly unknown, and summing what is left compared four days against two.
    val trendHasBaseline = measured &&
        completedThisWeek.isNotEmpty() &&
        measuredOn(completedThisWeek.first().minusDays(DAYS_IN_WEEK.toLong())) &&
        lastWeekSeconds > 0L

    // Blocks over the same Mon–Sun week the chart draws and the trend compares — the card says
    // "THIS WEEK" on its face, and all three must agree on which week that is.
    val weekKeys = week.mapTo(mutableSetOf()) { it.toString() }
    val preventedThisWeek = recentUsage.filter { it.date in weekKeys }.sumOf { it.blockedCount }

    val enabled = monitoredApps.filter { it.enabled }
    // Budget Left is a claim about limits, so it reads the tracked rows in either scope — an
    // all-apps total has no allowance to be a percentage of.
    val todayByPackage = recentUsage.filter { it.date == today.toString() }.associateBy { it.packageName }
    // One entry per capture — the achievement rules count them as well as date them, so this is a
    // list; the streak helpers below take the de-duplicated set.
    val captureDayList = captures.map { it.capturedAt.toLocalDate(zone) }
    val captureDates = captureDayList.toSet()

    // The donut's centre is summed from its own slices, so the two cannot drift apart. Deliberately
    // not `todaySeconds`, which counts monitored-but-disabled apps the breakdown leaves out.
    val breakdown = if (measured) categoryBreakdown(series!!) else emptyList()

    // Constructed field by field, deliberately **not** `StatsUiState.sample().copy(...)`. Every
    // value on this screen is computed now, so the only things `sample()` was still supplying were
    // two constants — and inheriting from it left a standing route for mockup data to reach the
    // screen the moment someone added a field and forgot to set it here. Same move already made in
    // `SettingsMapper`; `sample()` is now @Preview-only.
    return StatsUiState(
        avatarInitial = avatarInitialFor(userName),
        // Every scoped figure below goes blank when the chosen scope has nothing to measure, rather
        // than rendering the zeroes an empty series would otherwise produce. Same rule as
        // `deltaHasBaseline` and `ProtectionStatus.OFF`: one numeric slot cannot also say "unknown".
        dailyTotal = if (measured) formatSeconds(todaySeconds) else NO_DATA,
        deltaText = if (measured) deltaText(todaySeconds, yesterdaySeconds) else "",
        // "Positive" means usage went *up* — the screen renders it as the unwelcome direction. Only
        // a rise is a settled fact mid-day; being under yesterday so far is not yet an improvement.
        deltaIsPositive = yesterdaySeconds > 0L && percentChange(todaySeconds, yesterdaySeconds) >= 1,
        deltaHasBaseline = measured && yesterdaySeconds > 0L,
        // Null after today: the chart draws no mark for a day that hasn't happened. Plotting it as
        // zero put Thu–Sun on the floor, and the smoothed curve dived off a cliff after today —
        // four days of abstinence, drawn from four days that don't exist yet.
        weeklyPoints = if (measured) {
            week.map { if (it.isAfter(today) || !measuredOn(it)) null else usedOn(it) / SECONDS_PER_HOUR }
        } else {
            emptyList()
        },
        weekdayLabels = WEEKDAY_LABELS,
        highlightDayIndex = today.dayOfWeek.value - 1,
        trendLabel = if (trendHasBaseline) trendLabel(trendDeltaSeconds) else NO_DATA,
        // Usage going *up* is the unwelcome direction, same convention as deltaIsPositive.
        // A change that rounds to "0.0h" is level, as on vs-yesterday: an arrow beside it would
        // claim a direction the figure itself doesn't show.
        trendIsUp = trendDeltaSeconds > 0 && !trendIsLevel(trendDeltaSeconds),
        trendIsLevel = trendIsLevel(trendDeltaSeconds),
        trendHasBaseline = trendHasBaseline,
        trendCaption = when {
            !measured -> ""
            completedThisWeek.isEmpty() -> TREND_NO_FULL_DAY_CAPTION
            trendHasBaseline -> trendCaption(completedThisWeek)
            else -> TREND_NO_BASELINE_CAPTION
        },
        trendBars = if (measured) weekBars(week, today, ::usedOn, ::measuredOn) else emptyList(),
        breakdownTotal = if (measured) formatSeconds(breakdown.sumOf { it.seconds }) else NO_DATA,
        breakdown = breakdown,
        usageScope = scope,
        scopeCaption = SCOPE_CAPTIONS.getValue(scope),
        scopeUnavailable = !measured && !scopeLoading,
        scopeLoading = !measured && scopeLoading,
        budgetLeftLabel = budgetLeftLabel(enabled, todayByPackage, today),
        longestStreak = countLabel(longestStreakDays(captureDates), "Day"),
        // "0 Photos" under a party popper celebrates nothing; the card goes neutral and asks
        // instead. The count is the flag, so the screen never has to parse the label back.
        capturedPhotos = if (captures.isEmpty()) NO_PHOTOS_LABEL else countLabel(captures.size, "Photo"),
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

/**
 * Today so far against the whole of yesterday. A rise is a fact the moment it happens, but being
 * under yesterday is only true *so far*, so that case says so and claims no improvement.
 */
private fun deltaText(todaySeconds: Long, yesterdaySeconds: Long): String {
    if (yesterdaySeconds == 0L) return "No data for yesterday"
    val percent = percentChange(todaySeconds, yesterdaySeconds)
    return when {
        percent > 0 -> "$percent% more than yesterday"
        percent == 0 -> "Level with yesterday"
        else -> "${abs(percent)}% below yesterday so far"
    }
}

private fun percentChange(todaySeconds: Long, yesterdaySeconds: Long): Int =
    ((todaySeconds - yesterdaySeconds) * 100f / yesterdaySeconds).roundToInt()

/**
 * Names the compared days ("VS LAST MON–WED"), since week-to-date isn't guessable. Short enough to
 * stay on one line in the half-width card, where "MON–SAT VS LAST WEEK" broke after "LAST".
 */
private fun trendCaption(days: List<LocalDate>): String {
    val first = WEEKDAY_LABELS[days.first().dayOfWeek.value - 1]
    val last = WEEKDAY_LABELS[days.last().dayOfWeek.value - 1]
    val span = if (days.size == 1) first else "$first–$last"
    return "VS LAST $span"
}

/**
 * Week-over-week change, signed. Zero is written without a sign: `-0.0h` was reachable whenever
 * the two weeks matched exactly, which reads as a decrease that didn't happen.
 */
/** Whether a week-over-week change is too small to show as anything but "0.0h". */
internal fun trendIsLevel(deltaSeconds: Long): Boolean = trendLabel(deltaSeconds) == "0.0h"

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
 * Drawn over the **same completed days the headline compares**: Monday to yesterday. It used to be
 * a rolling five days, and then the whole week to date — on a Monday that was one full-height bar
 * for today beside "NO FULL DAY YET".
 *
 * Today and the days still to come are `null`, not `0f`: they have no complete value to draw, and a
 * zero would claim a day spent off the phone. Same distinction [StatsUiState.weeklyPoints] makes.
 */
private fun weekBars(
    week: List<LocalDate>,
    today: LocalDate,
    usedOn: (LocalDate) -> Long,
    measuredOn: (LocalDate) -> Boolean,
): List<Float?> {
    val completed = week.filter { it.isBefore(today) && measuredOn(it) }
    val peak = completed.maxOfOrNull(usedOn) ?: 0L
    return week.map { day ->
        when {
            !day.isBefore(today) || !measuredOn(day) -> null
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
 *
 * Scope-agnostic: it reads whatever [UsageSeries] it is handed, so the arcs always sum to the figure
 * in the middle of the donut whichever store the seconds came from.
 */
private fun categoryBreakdown(series: UsageSeries): List<UsageCategory> {
    // A package with no category is left out entirely — that is how each scope's membership rule
    // reaches here, rather than as two different filters this function would have to choose between.
    val secondsByCategory = series.todaySecondsByPackage.entries
        .groupBy { series.categories[it.key] }
        .mapValues { (_, entries) -> entries.sumOf { it.value } }

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
