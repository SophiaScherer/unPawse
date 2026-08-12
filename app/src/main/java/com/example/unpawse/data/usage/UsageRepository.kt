package com.example.unpawse.data.usage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
 * @param now supplies epoch millis for the reward cooldown, injected for the same reason (and in
 * the same shape as `UsageTracker`/`FocusSession`).
 */
class UsageRepository(
    private val dao: UsageDao,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private fun todayKey(): String = today().toString()

    /** All monitored apps, alphabetical. */
    fun observeMonitoredApps(): Flow<List<MonitoredApp>> =
        dao.observeMonitoredApps().map { rows -> rows.map(MonitoredAppEntity::toDomain) }

    /** Today's usage rows (one per app that has been used today). */
    fun observeTodayUsage(): Flow<List<DailyUsage>> =
        dao.observeUsageForDate(todayKey()).map { rows -> rows.map(DailyUsageEntity::toDomain) }

    /**
     * Usage over the last [days] days, today inclusive — the history behind the Stats charts.
     * Days with no usage simply have no row; callers fill the gaps with zero.
     */
    fun observeRecentUsage(days: Long): Flow<List<DailyUsage>> {
        val end = today()
        val start = end.minusDays(days - 1)
        return dao.observeUsageBetween(start.toString(), end.toString())
            .map { rows -> rows.map(DailyUsageEntity::toDomain) }
    }

    /** The complete usage history, oldest first. Used by the data export, not by any screen. */
    suspend fun allUsage(): List<DailyUsage> = dao.allUsage().map(DailyUsageEntity::toDomain)

    /** The monitored apps as a one-shot list, for callers that aren't observing. */
    suspend fun monitoredApps(): List<MonitoredApp> =
        observeMonitoredApps().first()

    /**
     * Adds (or updates) a monitored app and its daily limit.
     *
     * Any weekend override *and category* already on the row are carried across rather than reset,
     * so the everyday callers (the monitor switch, the limit stepper) can't silently drop a setting
     * made elsewhere. Use [setWeekendLimit] / [setCategory] to change them.
     *
     * @param defaultCategory seeds the category when there is no row yet — the platform's guess at
     * first enable. It never overwrites a stored value, so a stepper adjustment can't undo the
     * user's choice by passing a stale default.
     */
    suspend fun setLimit(
        packageName: String,
        appLabel: String,
        dailyLimitMinutes: Int,
        enabled: Boolean = true,
        defaultCategory: AppCategory? = null,
    ) {
        val existing = dao.monitoredApp(packageName)
        dao.upsertMonitoredApp(
            MonitoredAppEntity(
                packageName = packageName,
                appLabel = appLabel,
                dailyLimitMinutes = dailyLimitMinutes,
                enabled = enabled,
                weekendLimitMinutes = existing?.weekendLimitMinutes,
                category = existing?.category ?: defaultCategory?.name,
            ),
        )
    }

    /** Sets (or clears, with `null`) the Saturday/Sunday override for an already-monitored app. */
    suspend fun setWeekendLimit(packageName: String, weekendLimitMinutes: Int?) =
        dao.setWeekendLimit(packageName, weekendLimitMinutes)

    /** Reassigns which Stats bucket an app's time counts toward. */
    suspend fun setCategory(packageName: String, category: AppCategory) =
        dao.setCategory(packageName, category.name)

    suspend fun setEnabled(packageName: String, enabled: Boolean) =
        dao.setEnabled(packageName, enabled)

    suspend fun removeMonitoredApp(packageName: String) =
        dao.removeMonitoredApp(packageName)

    /** Drops the entire screen-time history and every monitored app. Used only by the full reset. */
    suspend fun clearAll() {
        dao.clearUsage()
        dao.clearMonitoredApps()
    }

    /** Accrues foreground time against today's budget (called by the foreground monitor). */
    suspend fun addUsage(packageName: String, duration: Duration) =
        dao.addUsage(packageName, todayKey(), duration.inWholeSeconds)

    /**
     * Records that a block was raised over [packageName]. Called once per breach by `UsageTracker`,
     * whatever the reason — a limit, a focus session and a schedule window are all one interruption
     * prevented.
     */
    suspend fun recordBlock(packageName: String) =
        dao.addBlock(packageName, todayKey())

    /**
     * Credits bonus minutes back **unconditionally**. The raw primitive behind [tryEarnMinutes];
     * the reward loop must not call it directly or the daily cap is bypassed.
     */
    suspend fun addEarnedMinutes(packageName: String, minutes: Int) =
        dao.addEarned(packageName, todayKey(), minutes.toLong() * SECONDS_PER_MINUTE, now())

    /**
     * The reward loop's only credit path: applies the per-app daily cap and the cooldown, then
     * credits whatever survives them. Returns the decision so the camera can tell the user *why* a
     * cat paid nothing.
     *
     * Read-then-write rather than a single statement, which is safe here because a grant only ever
     * happens on a shutter press — there is no concurrent writer to race with, unlike the
     * once-a-second usage accrual.
     */
    suspend fun tryEarnMinutes(packageName: String, minutes: Int): RewardDecision {
        val row = dao.usageFor(packageName, todayKey())
        val decision = decideReward(
            requestedMinutes = minutes,
            earnedSecondsToday = row.earned,
            lastEarnedAtMillis = row.lastEarnedAt,
            nowMillis = now(),
        )
        if (decision is RewardDecision.Granted) addEarnedMinutes(packageName, decision.minutes)
        return decision
    }

    /** Bonus minutes [packageName] can still earn today; 0 once its cap is spent. */
    suspend fun earnableMinutes(packageName: String): Int =
        earnableMinutes(dao.usageFor(packageName, todayKey()).earned)

    /**
     * Remaining minutes for [packageName] today (floored at 0), or `null` if it isn't a monitored,
     * enabled app — or if today is uncapped. Both nulls mean the same thing to every caller ("no
     * countdown to show, nothing to warn about"), so they deliberately aren't distinguished. For
     * the precise limit-reached check use [isLimitReached].
     */
    suspend fun remainingMinutes(packageName: String): Int? {
        val app = enabledApp(packageName) ?: return null
        val usage = dao.usageFor(packageName, todayKey())
        return remainingMinutes(app.limitForToday(), usage.used, usage.earned)
    }

    /** Minutes spent in [packageName] today, ignoring earned time. Drives the reminder copy. */
    suspend fun usedMinutes(packageName: String): Int =
        (dao.usageFor(packageName, todayKey()).used / SECONDS_PER_MINUTE).toInt()

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
        return isLimitReached(app.limitForToday(), usage.used, usage.earned)
    }

    /** Today's budget for [packageName] in minutes, or `null` if it isn't monitored and enabled. */
    suspend fun limitMinutesToday(packageName: String): Int? =
        enabledApp(packageName)?.limitForToday()

    private suspend fun enabledApp(packageName: String): MonitoredAppEntity? =
        dao.monitoredApp(packageName)?.takeIf { it.enabled }

    /**
     * Resolves the weekday/weekend split against the same `today()` the usage row is keyed by, so a
     * process that crosses into Saturday starts applying the weekend budget on the very next tick.
     */
    private fun MonitoredAppEntity.limitForToday(): Int =
        effectiveLimitMinutes(dailyLimitMinutes, weekendLimitMinutes, today().dayOfWeek)
}

/** Null-usage-safe accessors so callers read 0 when there's no row for today yet. */
private val DailyUsageEntity?.used: Long get() = this?.usedSeconds ?: 0
private val DailyUsageEntity?.earned: Long get() = this?.earnedSeconds ?: 0
private val DailyUsageEntity?.lastEarnedAt: Long get() = this?.lastEarnedAtMillis ?: 0
