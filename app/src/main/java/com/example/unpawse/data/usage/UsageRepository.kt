package com.example.unpawse.data.usage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import kotlin.time.Duration

/**
 * The source of truth for what unPawse monitors and how much has been used today. Orchestrates
 * [UsageDao] behind minute-oriented calls so callers never touch Room (mirrors [CaptureRepository]).
 *
 * @param today supplies the current local date, injected so tests can pin "today" and exercise the
 * daily rollover without a real clock. Each call reads it fresh, so a long-lived process crossing
 * midnight naturally starts writing to the new day's row.
 */
class UsageRepository(
    private val dao: UsageDao,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    private fun todayKey(): String = today().toString()

    /** All monitored apps, alphabetical. */
    fun observeMonitoredApps(): Flow<List<MonitoredApp>> =
        dao.observeMonitoredApps().map { rows -> rows.map(MonitoredAppEntity::toDomain) }

    /** Today's usage rows (one per app that has been used today). */
    fun observeTodayUsage(): Flow<List<DailyUsage>> =
        dao.observeUsageForDate(todayKey()).map { rows -> rows.map(DailyUsageEntity::toDomain) }

    /** Adds (or updates) a monitored app and its daily limit. */
    suspend fun setLimit(
        packageName: String,
        appLabel: String,
        dailyLimitMinutes: Int,
        enabled: Boolean = true,
    ) = dao.upsertMonitoredApp(
        MonitoredAppEntity(packageName, appLabel, dailyLimitMinutes, enabled),
    )

    suspend fun setEnabled(packageName: String, enabled: Boolean) =
        dao.setEnabled(packageName, enabled)

    suspend fun removeMonitoredApp(packageName: String) =
        dao.removeMonitoredApp(packageName)

    /** Accrues foreground time against today's budget (called by the foreground monitor). */
    suspend fun addUsage(packageName: String, duration: Duration) =
        dao.addUsage(packageName, todayKey(), duration.inWholeSeconds)

    /** Credits bonus minutes back (called when a cat capture is verified). */
    suspend fun addEarnedMinutes(packageName: String, minutes: Int) =
        dao.addEarned(packageName, todayKey(), minutes.toLong() * SECONDS_PER_MINUTE)

    /**
     * Remaining minutes for [packageName] today (floored at 0), or `null` if it isn't a monitored,
     * enabled app. For the precise limit-reached check use [isLimitReached].
     */
    suspend fun remainingMinutes(packageName: String): Int? {
        val app = enabledApp(packageName) ?: return null
        val usage = dao.usageFor(packageName, todayKey())
        return remainingMinutes(app.dailyLimitMinutes, usage.used, usage.earned)
    }

    /** Whether [packageName] is being watched right now — the monitor's per-tick gate. */
    suspend fun isMonitoredAndEnabled(packageName: String): Boolean =
        enabledApp(packageName) != null

    /** The stored display name for a monitored app, or null if it isn't one. */
    suspend fun appLabel(packageName: String): String? =
        dao.monitoredApp(packageName)?.appLabel

    /** True when a monitored, enabled app has spent its budget for today. */
    suspend fun isLimitReached(packageName: String): Boolean {
        val app = enabledApp(packageName) ?: return false
        val usage = dao.usageFor(packageName, todayKey())
        return isLimitReached(app.dailyLimitMinutes, usage.used, usage.earned)
    }

    private suspend fun enabledApp(packageName: String): MonitoredAppEntity? =
        dao.monitoredApp(packageName)?.takeIf { it.enabled }
}

/** Null-usage-safe accessors so callers read 0 when there's no row for today yet. */
private val DailyUsageEntity?.used: Long get() = this?.usedSeconds ?: 0
private val DailyUsageEntity?.earned: Long get() = this?.earnedSeconds ?: 0
