package com.example.unpawse.ui.onboarding

import com.example.unpawse.data.settings.AVATAR_NONE

/**
 * The first-run tour, as an ordered list of steps. Onboarding is a single navigation destination
 * that walks this enum rather than nine destinations, so sequencing is plain data and stays
 * unit-testable without a NavController.
 *
 * Order is not arbitrary: the two special permissions come before the two runtime ones because
 * without usage access nothing is measured and without the overlay nothing can be blocked — the app
 * genuinely does not function. The camera only gates the reward, and notifications only gate the
 * nudges, so they can wait.
 */
enum class OnboardingStep {
    WELCOME,
    HOW_IT_WORKS,
    NAME,
    AVATAR,
    USAGE_ACCESS,
    OVERLAY_ACCESS,
    CAMERA,
    NOTIFICATIONS,
    DONE,
}

/** Total number of steps, for the progress indicator. */
val ONBOARDING_STEP_COUNT: Int = OnboardingStep.entries.size

/** The four permission steps, in the order they are primed. */
val PERMISSION_STEPS: List<OnboardingStep> = listOf(
    OnboardingStep.USAGE_ACCESS,
    OnboardingStep.OVERLAY_ACCESS,
    OnboardingStep.CAMERA,
    OnboardingStep.NOTIFICATIONS,
)

/** Whether [step] is one of the permission primers, which behave differently from the rest. */
fun isPermissionStep(step: OnboardingStep): Boolean = step in PERMISSION_STEPS

/** What the app has actually been granted. Read from the platform; never persisted. */
data class PermissionGrants(
    val usageAccess: Boolean = false,
    val overlayAccess: Boolean = false,
    val camera: Boolean = false,
    val notifications: Boolean = false,
)

/** Everything onboarding has collected so far, whether typed in or granted. */
data class OnboardingAnswers(
    /** Blank means "skipped" — [com.example.unpawse.ui.format.displayNameOf] supplies the fallback. */
    val userName: String = "",
    /** [AVATAR_NONE] means "skipped" — the initials avatar remains the fallback. */
    val avatarId: Int = AVATAR_NONE,
    val grants: PermissionGrants = PermissionGrants(),
)

/** The grant [step] asks for, or null when it isn't a permission step. */
fun grantFor(step: OnboardingStep, grants: PermissionGrants): Boolean? = when (step) {
    OnboardingStep.USAGE_ACCESS -> grants.usageAccess
    OnboardingStep.OVERLAY_ACCESS -> grants.overlayAccess
    OnboardingStep.CAMERA -> grants.camera
    OnboardingStep.NOTIFICATIONS -> grants.notifications
    else -> null
}

/**
 * Whether [step] has got what it asked for. Nothing here is mandatory — this only decides what the
 * step *says* and whether its button offers to ask again, never whether the user may move on.
 */
fun isSatisfied(step: OnboardingStep, answers: OnboardingAnswers): Boolean = when (step) {
    OnboardingStep.WELCOME, OnboardingStep.HOW_IT_WORKS, OnboardingStep.DONE -> true
    OnboardingStep.NAME -> answers.userName.isNotBlank()
    OnboardingStep.AVATAR -> answers.avatarId != AVATAR_NONE
    else -> grantFor(step, answers.grants) == true
}

/** The step after [step]; the last step is its own successor, so this can't run off the end. */
fun nextStep(step: OnboardingStep): OnboardingStep =
    OnboardingStep.entries.getOrElse(step.ordinal + 1) { step }

/** The step before [step], or null on the first one (where back belongs to whatever hosts us). */
fun previousStep(step: OnboardingStep): OnboardingStep? =
    OnboardingStep.entries.getOrNull(step.ordinal - 1)

/**
 * Where to be after a resume, given the grants read before leaving and after coming back.
 *
 * This is the whole answer to the problem that usage access and the overlay are granted in *system
 * Settings*, which never reports back: the user flips the switch, returns, and unless we re-read
 * and act on it the screen still reads "we need this" — as though the trip had done nothing. A step
 * whose permission arrived while we were away advances; everything else stays put, so a user who
 * came back without granting isn't shoved forward.
 */
fun stepAfterGrant(
    step: OnboardingStep,
    before: PermissionGrants,
    after: PermissionGrants,
): OnboardingStep =
    if (grantFor(step, before) == false && grantFor(step, after) == true) nextStep(step) else step

/**
 * The permission steps still unsatisfied, in tour order — what the closing step lists as "you can
 * turn these on any time in Settings".
 */
fun missingPermissionSteps(grants: PermissionGrants): List<OnboardingStep> =
    PERMISSION_STEPS.filter { grantFor(it, grants) == false }

/** 1-based position of [step], for "Step 3 of 9" and the progress bar. */
fun stepNumber(step: OnboardingStep): Int = step.ordinal + 1

/** How far along [step] is, in 0f..1f. */
fun stepProgress(step: OnboardingStep): Float =
    stepNumber(step).toFloat() / ONBOARDING_STEP_COUNT
