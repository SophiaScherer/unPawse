package com.example.unpawse.ui.onboarding

/**
 * Builds [OnboardingUiState] from the step the user is on and everything collected so far. Pure —
 * no `Context`, no clock — so the whole tour's behaviour (what a step says, when it reads as
 * satisfied, what the closing summary lists) is unit-testable off a device.
 */
internal fun toOnboardingUiState(
    step: OnboardingStep,
    answers: OnboardingAnswers,
    cameraCanAskSystem: Boolean,
    notificationsCanAskSystem: Boolean = true,
): OnboardingUiState {
    val satisfied = isSatisfied(step, answers)
    return OnboardingUiState(
        step = step,
        answers = answers,
        copy = onboardingCopyFor(
            step,
            satisfied = satisfied,
            canAskSystem = when (step) {
                OnboardingStep.NOTIFICATIONS -> notificationsCanAskSystem
                else -> cameraCanAskSystem
            },
        ),
        satisfied = satisfied,
        stepNumber = stepNumber(step),
        stepCount = ONBOARDING_STEP_COUNT,
        progress = stepProgress(step),
        canGoBack = previousStep(step) != null,
        cameraCanAskSystem = cameraCanAskSystem,
        notificationsCanAskSystem = notificationsCanAskSystem,
        missingPermissions = missingPermissionSteps(answers.grants),
    )
}
