package com.example.unpawse.data.usage

import java.time.DayOfWeek

/**
 * Pure limit arithmetic, extracted from [UsageRepository] so it's unit-testable without Room or a
 * device (same spirit as `classify` in the ML layer). "Remaining" folds in both the daily limit and
 * any bonus minutes earned back from cat captures.
 */
internal const val SECONDS_PER_MINUTE = 60L

/**
 * "No cap today" — the value a weekend override carries when the user wants an app left alone at
 * weekends.
 *
 * Deliberately negative rather than zero: a limit of **0 already means "over budget from the first
 * second"** everywhere in the existing enforcement path, and several tracker tests rely on exactly
 * that. Overloading zero would have flipped those from "blocked instantly" to "never blocked".
 */
const val UNLIMITED_MINUTES = -1

/**
 * The budget that applies on [day]: the weekend override when there is one and the day is a
 * weekend, otherwise the everyday figure. A null override means "same as the rest of the week",
 * which is the default — most apps want one number.
 */
internal fun effectiveLimitMinutes(
    dailyLimitMinutes: Int,
    weekendLimitMinutes: Int?,
    day: DayOfWeek,
): Int = if (weekendLimitMinutes != null && isWeekend(day)) {
    weekendLimitMinutes
} else {
    dailyLimitMinutes
}

internal fun isWeekend(day: DayOfWeek): Boolean =
    day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY

/** Signed remaining seconds for today: `limit + earned − used`. Negative means over the limit. */
internal fun remainingSeconds(limitMinutes: Int, usedSeconds: Long, earnedSeconds: Long): Long =
    limitMinutes.toLong() * SECONDS_PER_MINUTE - usedSeconds + earnedSeconds

/**
 * Remaining whole minutes for display — floored at zero (never shows negative time left), or `null`
 * when the day is uncapped. Null rather than a huge number so callers must decide what "no limit"
 * looks like instead of rendering "8h 0m left" and implying one.
 */
internal fun remainingMinutes(limitMinutes: Int, usedSeconds: Long, earnedSeconds: Long): Int? {
    if (isUnlimited(limitMinutes)) return null
    return (remainingSeconds(limitMinutes, usedSeconds, earnedSeconds).coerceAtLeast(0) / SECONDS_PER_MINUTE).toInt()
}

/** Whether today's budget is exhausted (used ≥ limit + earned). Drives the block trigger. */
internal fun isLimitReached(limitMinutes: Int, usedSeconds: Long, earnedSeconds: Long): Boolean {
    if (isUnlimited(limitMinutes)) return false
    return remainingSeconds(limitMinutes, usedSeconds, earnedSeconds) <= 0
}

/** Any negative budget reads as uncapped, so a stray −2 can't be mistaken for a real limit. */
internal fun isUnlimited(limitMinutes: Int): Boolean = limitMinutes < 0

/**
 * Today's budget picture across an app set, for the screens that report headroom rather than
 * enforce it. Summed over the **capped** apps only — see [dailyBudget].
 */
internal data class DailyBudget(
    val budgetSeconds: Long,
    val usedSeconds: Long,
    val earnedSeconds: Long,
) {
    /** Floored at zero: the screens report time left, never a negative overdraft. */
    val remainingSeconds: Long
        get() = (budgetSeconds + earnedSeconds - usedSeconds).coerceAtLeast(0)

    /** Headroom as a percentage of the day's allowance, earned time included in the denominator. */
    val leftPercent: Int
        get() {
            val allowance = budgetSeconds + earnedSeconds
            if (allowance <= 0L) return 0
            return ((remainingSeconds * 100) / allowance).toInt().coerceIn(0, 100)
        }

    /** Share of the budget burned, for Home's ring. A zero budget is spent the moment it's used. */
    val usedFraction: Float
        get() = when {
            budgetSeconds > 0L -> (usedSeconds.toFloat() / budgetSeconds).coerceIn(0f, 1f)
            usedSeconds > 0L -> 1f
            else -> 0f
        }
}

/**
 * Sums [enabledApps] into one budget for [day], or `null` when none of them has a cap to report on.
 *
 * Each app's budget comes from [effectiveLimitMinutes], the same function the enforcement path uses,
 * so a weekend override moves the reported headroom and the blocker together.
 *
 * Uncapped apps are dropped from the budget **and** from the used/earned sums. An app with no cap has
 * no headroom to be a percentage of, and counting its time against everyone else's budget was what
 * made a single unlimited app report "0% left" for the whole day. Null rather than a zeroed
 * [DailyBudget] so callers blank the figure instead of publishing a 0% that means "no data".
 */
internal fun dailyBudget(
    enabledApps: List<MonitoredApp>,
    usageByPackage: Map<String, DailyUsage>,
    day: DayOfWeek,
): DailyBudget? {
    val capped = enabledApps
        .map { app -> app to effectiveLimitMinutes(app.dailyLimitMinutes, app.weekendLimitMinutes, day) }
        .filterNot { (_, limit) -> isUnlimited(limit) }
    if (capped.isEmpty()) return null

    return DailyBudget(
        budgetSeconds = capped.sumOf { (_, limit) -> limit.toLong() * SECONDS_PER_MINUTE },
        usedSeconds = capped.sumOf { (app, _) -> usageByPackage[app.packageName]?.usedSeconds ?: 0L },
        earnedSeconds = capped.sumOf { (app, _) -> usageByPackage[app.packageName]?.earnedSeconds ?: 0L },
    )
}
