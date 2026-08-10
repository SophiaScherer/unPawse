package com.example.unpawse.data.usage

/**
 * Pure reward-policy arithmetic: how much (if anything) a verified cat is worth right now.
 * Extracted from [UsageRepository] for the same reason as [UsageMath] — it is the rule that decides
 * whether the app's core promise holds, so it must be testable without Room or a device.
 *
 * The rule exists because the escape hatch used to have no floor: a block re-arms the moment the
 * granted minutes are spent, so photo → 15 min → photo → 15 min could repeat forever and a static
 * cat photo on a second screen defeated the daily limit entirely.
 */

/**
 * Bonus minutes one app may earn in a single day.
 *
 * Four cats at the shipped 15-minute grant — generous enough to feel like a real escape hatch,
 * small enough that a 15-minute daily limit can't quietly become an unbounded one. At the Settings
 * stepper's 60-minute maximum it collapses to one cat per app per day, which is the honest
 * consequence of asking for a very large grant. Per app rather than global so a heavy Instagram day
 * doesn't silently spend the allowance that would have unblocked Chrome.
 */
const val DAILY_EARNED_CAP_MINUTES = 60

/**
 * Minimum wall-clock gap between two grants for the same app.
 *
 * A backstop rather than the primary defence: at the shipped 15-minute grant it never bites, since
 * the user can't burn 15 minutes in under 10. It bites exactly where the cap alone is weakest —
 * small grant settings, where a 5-minute reward could otherwise be re-earned every 5 minutes.
 */
const val REWARD_COOLDOWN_MINUTES = 10

internal const val MILLIS_PER_MINUTE = 60_000L

/** What a verified cat bought, and when it bought nothing, why. */
sealed interface RewardDecision {

    /**
     * Minutes to credit. May be *less* than requested when only part of the cap is left — granting
     * the remainder beats refusing outright, and keeps `earnedSeconds` from ever passing the cap.
     */
    data class Granted(val minutes: Int) : RewardDecision

    /** Today's whole allowance for this app is spent; nothing until tomorrow. */
    data class Capped(val dailyCapMinutes: Int) : RewardDecision

    /** Too soon after the last grant. [retrySeconds] is how much longer the user has to wait. */
    data class CoolingDown(val retrySeconds: Long) : RewardDecision
}

/**
 * Decides what [requestedMinutes] is actually worth given how much this app has already earned
 * today and when it last earned.
 *
 * [earnedSecondsToday] and [lastEarnedAtMillis] can be read straight off the `daily_usage` row:
 * `earnedSeconds` is only ever incremented, so today's value *is* today's cumulative grant total,
 * and the composite `(packageName, date)` key resets both at midnight for free.
 *
 * The cap is checked **before** the cooldown: "you're done for today" is both more final and more
 * useful than "wait four minutes" when the wait would end in a refusal anyway.
 */
internal fun decideReward(
    requestedMinutes: Int,
    earnedSecondsToday: Long,
    lastEarnedAtMillis: Long,
    nowMillis: Long,
    dailyCapMinutes: Int = DAILY_EARNED_CAP_MINUTES,
    cooldownMillis: Long = REWARD_COOLDOWN_MINUTES * MILLIS_PER_MINUTE,
): RewardDecision {
    val remainingMinutes = earnableMinutes(earnedSecondsToday, dailyCapMinutes)
    // A non-positive request can't be a reward; treat it as nothing left rather than crediting 0.
    val grant = minOf(requestedMinutes, remainingMinutes)
    if (grant <= 0) return RewardDecision.Capped(dailyCapMinutes)

    // 0 means "hasn't earned yet today", which is never a cooldown — and a clock that has moved
    // backwards (skew, a timezone change) reads as elapsed rather than locking the user out.
    val elapsed = nowMillis - lastEarnedAtMillis
    if (lastEarnedAtMillis > 0 && elapsed in 0 until cooldownMillis) {
        return RewardDecision.CoolingDown(
            retrySeconds = ceilToSeconds(cooldownMillis - elapsed),
        )
    }
    return RewardDecision.Granted(grant)
}

/** Rounds *up*, so a wait of 100ms reports "1 second" rather than "0" and reads as a real wait. */
private fun ceilToSeconds(millis: Long): Long = (millis + 999) / 1000

/** Bonus minutes still available today, floored at zero. Pure counterpart of the check above. */
internal fun earnableMinutes(
    earnedSecondsToday: Long,
    dailyCapMinutes: Int = DAILY_EARNED_CAP_MINUTES,
): Int {
    val capSeconds = dailyCapMinutes.toLong() * SECONDS_PER_MINUTE
    return ((capSeconds - earnedSecondsToday).coerceAtLeast(0) / SECONDS_PER_MINUTE).toInt()
}
