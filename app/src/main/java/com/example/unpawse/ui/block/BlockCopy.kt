package com.example.unpawse.ui.block

import com.example.unpawse.data.usage.RewardDecision
import com.example.unpawse.ui.camera.retryText
import com.example.unpawse.ui.format.formatMinutes

/**
 * What the block overlay says about the reward before the user goes looking for a cat.
 *
 * Pure top-level functions for the same reason as `CameraHints.kt` and `warningText`: these bounds
 * were implemented, tested and completely invisible until the user hit one and got refused, so the
 * wording that finally states them is worth unit-testing.
 */

/**
 * The reward terms as the overlay draws them: two labelled figures, plus a note when a cooldown is
 * actually running. Pre-formatted like the rest of [BlockUiState] — the overlay renders copy that
 * was decided elsewhere.
 */
data class RewardTerms(
    val grantValue: String,
    val grantLabel: String,
    val allowanceValue: String,
    val allowanceLabel: String,
    /** Non-null only while a wait is genuinely in progress. */
    val cooldownNote: String?,
)

/**
 * Builds the terms from the same [RewardDecision] the credit path produces.
 *
 * Returns **null exactly when there is nothing left to earn today**, which is the one case the
 * overlay answers with [BlockUiState.forAppOutOfRewards] instead — so a null here is also what tells
 * the service not to arm a session. A cooldown is not that case: the wait is stated, the camera
 * stays, and the photo is still saved and still counts toward the streak.
 *
 * [grantMinutes] is the user's configured grant and [earnableMinutes] what is left of the daily cap;
 * the pill reports the smaller, because a grant trimmed by the cap is what this cat will really pay.
 */
internal fun rewardTerms(
    appName: String,
    decision: RewardDecision,
    grantMinutes: Int,
    earnableMinutes: Int,
): RewardTerms? {
    if (decision is RewardDecision.Capped) return null

    val grant = minOf(grantMinutes, earnableMinutes).coerceAtLeast(0)
    return RewardTerms(
        grantValue = "+${formatMinutes(grant)}",
        grantLabel = "Per cat",
        allowanceValue = formatMinutes(earnableMinutes),
        allowanceLabel = "Bonus left today",
        // Echoes the camera's own refusal copy, via the same rounds-up helper, so the wait the
        // overlay promises and the wait the shutter reports can't disagree.
        cooldownNote = (decision as? RewardDecision.CoolingDown)?.let {
            "$appName can earn again in ${retryText(it.retrySeconds)}."
        },
    )
}
