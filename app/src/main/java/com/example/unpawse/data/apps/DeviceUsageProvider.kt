package com.example.unpawse.data.apps

import android.app.usage.UsageStatsManager
import android.content.Context
import com.example.unpawse.service.UsageAccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * How much each installed app has actually been used lately, so the picker can rank apps by what
 * they cost the user rather than by their first letter.
 *
 * An interface for the same reason as [InstalledAppsProvider]: ViewModels stay JVM-testable and
 * never touch [UsageStatsManager] directly.
 *
 * This deliberately reads the **platform's** figures rather than unPawse's own `daily_usage`. That
 * table only ever holds *monitored* apps, so building this on it would answer "how much have you
 * used the apps you already limit?" — the exact opposite of what a screen for finding the apps you
 * haven't limited needs to know.
 */
interface DeviceUsageProvider {
    /**
     * Average foreground seconds per day over the last [days], keyed by package.
     *
     * `null` means **no figures exist** — usage access isn't granted, so nothing can be measured.
     * An empty map (or a package missing from a non-null map) is a real measurement of zero. The
     * two must stay distinguishable: one is "we don't know", the other is "we know, and it's none".
     */
    suspend fun dailyAverageSeconds(days: Int = RECENT_DAYS): Map<String, Long>?
}

/** How far back "recently" looks. Short enough to track a habit the user is currently trying to change. */
const val RECENT_DAYS = 7

/**
 * [UsageStatsManager]-backed implementation. One `queryAndAggregateUsageStats` call per read — the
 * platform does the per-package folding, so this is a single binder round trip rather than a sweep
 * over daily buckets.
 *
 * Needs the same `PACKAGE_USAGE_STATS` app-op the enforcement service already requires, so it costs
 * no new permission; without it there is nothing to report and this answers `null`.
 */
class UsageStatsDeviceUsageProvider(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) : DeviceUsageProvider {

    private val appContext = context.applicationContext
    private val usageStatsManager = appContext.getSystemService(UsageStatsManager::class.java)

    override suspend fun dailyAverageSeconds(days: Int): Map<String, Long>? = withContext(ioDispatcher) {
        // Checked rather than assumed: the picker is reachable from Settings long before anyone has
        // visited the usage-access screen.
        if (!UsageAccess.isGranted(appContext)) return@withContext null
        val manager = usageStatsManager ?: return@withContext null

        val end = now()
        val begin = end - days.coerceAtLeast(1) * MILLIS_PER_DAY
        // totalTimeInForeground, not totalTimeVisible — the latter is API 29 against minSdk 26.
        val totals = manager.queryAndAggregateUsageStats(begin, end)
            .mapValues { (_, stats) -> stats.totalTimeInForeground }

        averageSecondsPerDay(totals, days)
    }
}

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
private const val MILLIS_PER_SECOND = 1000L

/**
 * Spreads each package's total across the window. Pure, so the arithmetic is unit-tested without a
 * device — the same split [InstalledAppsProvider] uses for its list shaping.
 *
 * Truncates rather than rounds, matching `formatMinutes`/`formatSeconds` everywhere else, and
 * clamps negatives: the platform's totals are occasionally nonsense on a device whose clock has
 * moved, and a negative average would sort an app above ones that really were unused.
 */
internal fun averageSecondsPerDay(totalMillisByPackage: Map<String, Long>, days: Int): Map<String, Long> {
    val divisor = MILLIS_PER_SECOND * days.coerceAtLeast(1)
    return totalMillisByPackage.mapValues { (_, millis) -> millis.coerceAtLeast(0L) / divisor }
}
