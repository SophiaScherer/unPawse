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

/** What a verified cat bought, and when it bought nothing, why. */
sealed interface RewardDecision {

    /**
     * Minutes to credit. May be *less* than requested when only part of the cap is left — granting
     * the remainder beats refusing outright, and keeps `earnedSeconds` from ever passing the cap.
     */
    data class Granted(val minutes: Int) : RewardDecision

    /** Today's whole allowance for this app is spent; nothing until tomorrow. */
    data class Capped(val dailyCapMinutes: Int) : RewardDecision
}

/**
 * Decides what [requestedMinutes] is actually worth given how much this app has already earned
 * today.
 *
 * [earnedSecondsToday] can be read straight off the `daily_usage` row: `earnedSeconds` is only ever
 * incremented, so today's value *is* today's cumulative grant total, and the composite
 * `(packageName, date)` key resets it at midnight for free.
 */
internal fun decideReward(
    requestedMinutes: Int,
    earnedSecondsToday: Long,
    dailyCapMinutes: Int = DAILY_EARNED_CAP_MINUTES,
): RewardDecision {
    val remainingMinutes = earnableMinutes(earnedSecondsToday, dailyCapMinutes)
    // A non-positive request can't be a reward; treat it as nothing left rather than crediting 0.
    val grant = minOf(requestedMinutes, remainingMinutes)
    return if (grant > 0) RewardDecision.Granted(grant) else RewardDecision.Capped(dailyCapMinutes)
}

/** Bonus minutes still available today, floored at zero. Pure counterpart of the check above. */
internal fun earnableMinutes(
    earnedSecondsToday: Long,
    dailyCapMinutes: Int = DAILY_EARNED_CAP_MINUTES,
): Int {
    val capSeconds = dailyCapMinutes.toLong() * SECONDS_PER_MINUTE
    return ((capSeconds - earnedSecondsToday).coerceAtLeast(0) / SECONDS_PER_MINUTE).toInt()
}
