package com.example.unpawse.ui.camera

/**
 * Viewfinder hint copy. Pure top-level functions rather than private helpers on the ViewModel, for
 * the same reason as `warningText` and the `SettingsMapper` labels: the wording of a refusal is the
 * only thing the user sees when a cat doesn't pay out, so it is worth unit-testing.
 */

/** Tells the user what their cat just bought them — or, when it bought nothing, why. */
internal fun savedHint(outcome: RewardOutcome): String = when (outcome) {
    RewardOutcome.NoActiveBlock -> "Purrfect! Saved to your gallery."
    is RewardOutcome.Earned -> "Purrfect! +${outcome.minutes} min of ${outcome.appLabel}."
    is RewardOutcome.DailyCapReached ->
        "Saved! But ${outcome.appLabel} has earned all ${outcome.capMinutes} bonus minutes it can today."
    is RewardOutcome.CoolingDown ->
        "Saved! ${outcome.appLabel} can earn again in ${retryText(outcome.retrySeconds)}."
}

/**
 * How long is left on a cooldown, in the coarsest honest unit. Rounds minutes *up* so the wait is
 * never under-promised — telling someone "1 minute" when it's really 90 seconds reads as a bug.
 */
internal fun retryText(retrySeconds: Long): String = when {
    retrySeconds <= 0 -> "a moment"
    retrySeconds < 60 -> "under a minute"
    else -> {
        val minutes = ((retrySeconds + 59) / 60).toInt()
        if (minutes == 1) "1 minute" else "$minutes minutes"
    }
}
