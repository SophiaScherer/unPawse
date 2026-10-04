package com.example.unpawse.data.usage

import java.time.LocalDate

/**
 * Screen time in the one shape the Stats screen reads, whichever [UsageScope] produced it.
 *
 * The two sources answer the same questions from completely different stores — `daily_usage` for
 * [UsageScope.TRACKED], the platform's own figures for [UsageScope.ALL] — so they are folded into
 * this before the mapper sees them. Without it every metric on the screen would need a branch, and
 * the two branches would drift the way the chart and the trend once did over what "this week" meant.
 */
data class UsageSeries(
    /** Total seconds per ISO date. A date with no entry has no measurement, not a zero. */
    val secondsByDate: Map<String, Long>,
    /** Today's seconds per package, for the breakdown. */
    val todaySecondsByPackage: Map<String, Long>,
    /**
     * Which bucket each package counts toward.
     *
     * A package **absent from this map is left out of the donut entirely**, which is how the donut's
     * membership rule travels as data rather than as a filter the mapper has to apply differently
     * per scope. Tracked scope populates it for enabled apps only, so a monitored-but-disabled app's
     * row drops out; all-apps scope populates it for everything it saw.
     */
    val categories: Map<String, AppCategory>,
    /**
     * The first day this source measured anything. Earlier days are unknown rather than idle: before
     * install for the tracked rows, past the platform's retention for all apps. A comparison that
     * reaches back before it has no baseline, and a chart draws no mark there.
     */
    val measuredSince: LocalDate,
)

/** The first day [usage] has a row for, or [today] when it has none — today is always being measured. */
fun firstMeasuredDay(usage: List<DailyUsage>, today: LocalDate): LocalDate =
    usage.minOfOrNull { it.date }?.let(LocalDate::parse)?.coerceAtMost(today) ?: today

/**
 * The whole history with [recent] laid over it, so the rows inside the live window are current even
 * though [all] was read once. A one-shot history read before midnight would otherwise judge
 * yesterday on a partial total.
 */
fun mergeUsageHistory(all: List<DailyUsage>, recent: List<DailyUsage>): List<DailyUsage> =
    (recent + all).distinctBy { it.packageName to it.date }

/**
 * What unPawse measured itself: the `daily_usage` rows, which only ever cover monitored apps.
 *
 * [secondsByDate] deliberately sums **every** row, including apps that are monitored but currently
 * disabled, while [UsageSeries.categories] covers only the enabled ones. That asymmetry is
 * pre-existing behaviour and is kept on purpose: filtering history by the *currently* enabled set
 * would let switching an app off today rewrite last Tuesday's chart.
 */
fun trackedUsageSeries(
    recentUsage: List<DailyUsage>,
    monitoredApps: List<MonitoredApp>,
    today: LocalDate,
    /** Pass the first day of the *whole* history; a window alone can't say when tracking began. */
    measuredSince: LocalDate = firstMeasuredDay(recentUsage, today),
): UsageSeries {
    val todayKey = today.toString()
    return UsageSeries(
        secondsByDate = recentUsage.groupBy { it.date }
            .mapValues { (_, rows) -> rows.sumOf { it.usedSeconds } },
        todaySecondsByPackage = recentUsage.filter { it.date == todayKey }
            .associate { it.packageName to it.usedSeconds },
        categories = monitoredApps.filter { it.enabled }.associate { it.packageName to it.category },
        measuredSince = measuredSince,
    )
}

/**
 * What the platform measured: every app on the phone, from
 * [com.example.unpawse.data.apps.DeviceUsageProvider.dailySecondsByDate].
 *
 * A date the platform could not report comes back empty — its daily buckets only go back about a
 * week — so [UsageSeries.measuredSince] starts after it: the chart draws no mark for it and the trend
 * finds no baseline, instead of claiming a day spent off the phone.
 *
 * [platformCategories] is each package's own declaration, `null` where it made none; see
 * [categoryFromPlatform] for why that map is deliberately sparse.
 */
fun deviceUsageSeries(
    secondsByDateAndPackage: Map<String, Map<String, Long>>,
    platformCategories: Map<String, AppCategory?>,
    monitoredApps: List<MonitoredApp>,
    today: LocalDate,
): UsageSeries {
    val todayByPackage = secondsByDateAndPackage[today.toString()].orEmpty()
    val resolved = resolveCategories(platformCategories, monitoredApps)
    return UsageSeries(
        secondsByDate = secondsByDateAndPackage.mapValues { (_, byPackage) -> byPackage.values.sum() },
        todaySecondsByPackage = todayByPackage,
        // Everything the platform reported gets a bucket, so nothing silently vanishes from the
        // donut — an app we can't classify is exactly what OTHER means.
        categories = todayByPackage.keys.associateWith { resolved[it] ?: AppCategory.OTHER },
        // A day with no app used at all is a day the platform no longer holds, not one spent off the
        // phone — something is always in the foreground while the screen is on.
        measuredSince = secondsByDateAndPackage.filterValues { it.isNotEmpty() }.keys
            .minOrNull()?.let(LocalDate::parse)?.coerceAtMost(today) ?: today,
    )
}

/**
 * The user's stored choice beats the platform's guess, for every app they have ever monitored.
 *
 * The same precedence the App Picker's rows already follow: [categoryFromPlatform] is only ever a
 * default for an app nobody has classified, and a monitored row records a decision. Disabled rows
 * count too — switching an app off stops it being limited, it doesn't retract what the user said it
 * was.
 */
fun resolveCategories(
    platformCategories: Map<String, AppCategory?>,
    monitoredApps: List<MonitoredApp>,
): Map<String, AppCategory> {
    val resolved = platformCategories.filterValues { it != null }
        .mapValues { (_, category) -> category!! }
        .toMutableMap()
    monitoredApps.forEach { resolved[it.packageName] = it.category }
    return resolved
}
