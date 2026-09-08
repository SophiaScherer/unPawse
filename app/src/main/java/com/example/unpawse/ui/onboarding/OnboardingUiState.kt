package com.example.unpawse.ui.onboarding

import com.example.unpawse.data.settings.CatAvatar

/**
 * Immutable UI state for the first-run tour. Built field by field by [toOnboardingUiState]; the
 * [sample] below is `@Preview`-only, per the project rule that no mapper builds production state
 * from a mockup fixture.
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val answers: OnboardingAnswers = OnboardingAnswers(),
    val copy: OnboardingCopy = onboardingCopyFor(OnboardingStep.WELCOME),
    /** Whether the current step got what it asked for; drives the "Granted" confirmation. */
    val satisfied: Boolean = true,
    /** 1-based, for "Step 3 of 9". */
    val stepNumber: Int = 1,
    val stepCount: Int = ONBOARDING_STEP_COUNT,
    val progress: Float = stepProgress(OnboardingStep.WELCOME),
    /** False on the first step, where back belongs to whatever pushed onboarding. */
    val canGoBack: Boolean = false,
    /**
     * False once Android has stopped showing the camera dialog. The copy already routes the button
     * to app settings; the screen uses this to explain why.
     */
    val cameraCanAskSystem: Boolean = true,
    /** Permission steps still unsatisfied, listed on the closing step. */
    val missingPermissions: List<OnboardingStep> = PERMISSION_STEPS,
) {
    companion object {
        /** Preview-only fixture. Never build production state from this — see the class KDoc. */
        fun sample(step: OnboardingStep = OnboardingStep.WELCOME) = toOnboardingUiState(
            step = step,
            answers = OnboardingAnswers(
                userName = "Sophia",
                avatarId = CatAvatar.CALICO.id,
                grants = PermissionGrants(usageAccess = true, overlayAccess = true),
            ),
            cameraCanAskSystem = true,
        )
    }
}
