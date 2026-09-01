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
)

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
): UsageSeries {
    val todayKey = today.toString()
    return UsageSeries(
        secondsByDate = recentUsage.groupBy { it.date }
            .mapValues { (_, rows) -> rows.sumOf { it.usedSeconds } },
        todaySecondsByPackage = recentUsage.filter { it.date == todayKey }
            .associate { it.packageName to it.usedSeconds },
        categories = monitoredApps.filter { it.enabled }.associate { it.packageName to it.category },
    )
}

/**
 * What the platform measured: every app on the phone, from
 * [com.example.unpawse.data.apps.DeviceUsageProvider.dailySecondsByDate].
 *
 * A date the platform could not report is **absent** rather than zero — its daily buckets only go
 * back about a week — so the chart draws no mark for it and the trend finds no baseline, instead of
 * claiming a day spent off the phone.
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
