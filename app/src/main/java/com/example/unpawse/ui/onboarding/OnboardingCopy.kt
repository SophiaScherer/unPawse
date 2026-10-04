package com.example.unpawse.ui.onboarding

/**
 * Everything a step says, as data. Pure so the wording is unit-testable and so
 * [OnboardingScreen] stays a layout with no `when (step)` branches buried in it.
 *
 * [secondaryLabel] is the skip affordance. It is null where there is nothing to skip — the two
 * explanatory steps, the closing one, and any step already answered — never because a step is
 * required. Nothing here is.
 */
data class OnboardingCopy(
    val title: String,
    val body: String,
    val primaryLabel: String,
    val secondaryLabel: String?,
)

/** The three beats of the loop, shown on [OnboardingStep.HOW_IT_WORKS]. */
data class LoopBeat(val title: String, val body: String)

/**
 * Concrete on purpose: "set a limit" means nothing until you've been told it's 30 minutes of
 * TikTok, and "earn time back" means nothing until you know it's a photo of an actual cat.
 */
val LOOP_BEATS: List<LoopBeat> = listOf(
    LoopBeat(
        title = "You set the limits",
        body = "Pick the apps that eat your day and give each one a daily budget — say 30 minutes " +
            "of TikTok. unPawse counts the minutes in the background.",
    ),
    LoopBeat(
        title = "We step in at the limit",
        body = "Open TikTok after those 30 minutes and a full-screen break slides over it. No " +
            "scrolling past it, no sneaking back in.",
    ),
    LoopBeat(
        title = "A cat buys you back in",
        body = "Point your camera at a real cat. unPawse checks it on your device — no photo ever " +
            "leaves your phone — and 15 minutes go back on the clock.",
    ),
)

/**
 * The copy for [step]. [satisfied] flips a permission step from its pitch to its confirmation, and
 * [canAskSystem] false means no dialog can help any more (the camera's or the notification one), so
 * the button has to offer a settings page instead of a request that would do nothing.
 */
fun onboardingCopyFor(
    step: OnboardingStep,
    satisfied: Boolean = false,
    canAskSystem: Boolean = true,
): OnboardingCopy = when (step) {
    OnboardingStep.WELCOME -> OnboardingCopy(
        title = "Welcome to unPawse",
        body = "unPawse is a screen-time app with a cat-shaped way out. You decide how long each " +
            "app gets; when the time is up we get in the way — until you show us a cat.",
        primaryLabel = "Let's go",
        secondaryLabel = null,
    )

    OnboardingStep.HOW_IT_WORKS -> OnboardingCopy(
        title = "How it works",
        body = "Three steps, and the third one is the fun part.",
        primaryLabel = "Got it",
        secondaryLabel = null,
    )

    OnboardingStep.NAME -> OnboardingCopy(
        title = "What should we call you?",
        body = "Only used to say hello on your Home screen. It stays on this device, and you can " +
            "change or clear it in Settings whenever you like.",
        primaryLabel = "Continue",
        // Skip means "leave my name as it was", which only differs from Continue while the field
        // is empty; once something is typed, offering both would make the user guess which keeps it.
        secondaryLabel = if (satisfied) null else "Skip for now",
    )

    OnboardingStep.AVATAR -> OnboardingCopy(
        title = "Pick a cat",
        body = "Your profile picture. These are drawn right here in the app — nothing is " +
            "downloaded, and no photo of you is involved.",
        primaryLabel = "Continue",
        // A cat is stored on the tap, so once one is picked there is nothing left to skip.
        secondaryLabel = if (satisfied) null else "Skip for now",
    )

    OnboardingStep.USAGE_ACCESS -> OnboardingCopy(
        title = "Let unPawse see your screen time",
        body = if (satisfied) {
            "Screen time access is on, so unPawse can see which app is open and count the minutes."
        } else {
            "This is the one unPawse can't work without: it's how we know which app is open " +
                "and how long you've had it open. Android keeps this behind a switch in Settings, " +
                "so we'll send you there — find unPawse in the list and turn it on."
        },
        primaryLabel = if (satisfied) "Continue" else "Open Settings",
        secondaryLabel = if (satisfied) null else "Not now",
    )

    OnboardingStep.OVERLAY_ACCESS -> OnboardingCopy(
        title = "Let unPawse draw the break",
        body = if (satisfied) {
            "Display over other apps is on, so when a limit runs out the break can appear right " +
                "over the app you're in."
        } else {
            "When you hit a limit, the break has to appear over the app you're in — otherwise " +
                "we can count your minutes but never actually interrupt you. This is another " +
                "Settings switch: \"Display over other apps\"."
        },
        primaryLabel = if (satisfied) "Continue" else "Open Settings",
        secondaryLabel = if (satisfied) null else "Not now",
    )

    OnboardingStep.CAMERA -> OnboardingCopy(
        title = "The cat camera",
        body = if (satisfied) {
            "Camera access is on. When a limit runs out, a photo of a cat earns your minutes back " +
                "— checked on your device, and the photo never leaves it."
        } else {
            "Photographing a cat is how you earn your minutes back. The check runs on your " +
                "device and the photo stays on it — nothing is uploaded, ever."
        },
        primaryLabel = when {
            satisfied -> "Continue"
            // Two denials and Android stops showing the dialog; asking again would be a dead button.
            !canAskSystem -> "Open app settings"
            else -> "Allow camera"
        },
        secondaryLabel = if (satisfied) null else "Not now",
    )

    OnboardingStep.NOTIFICATIONS -> OnboardingCopy(
        title = "A heads-up before the wall",
        body = if (satisfied) {
            "Notifications are on. You'll get a quiet heads-up a few minutes before an app runs " +
                "out, so a block is never a surprise."
        } else {
            "A quiet warning a few minutes before an app runs out, so a block is never a " +
                "surprise. Nice to have, not load-bearing — skip it and everything else still works."
        },
        primaryLabel = when {
            satisfied -> "Continue"
            !canAskSystem -> "Open notification settings"
            else -> "Allow notifications"
        },
        secondaryLabel = if (satisfied) null else "Not now",
    )

    OnboardingStep.DONE -> OnboardingCopy(
        title = "You're all set",
        body = "Next stop: pick the apps you want limits on. That's under Settings → Individual " +
            "app limits, and Home will nudge you there too.",
        primaryLabel = "Start using unPawse",
        secondaryLabel = null,
    )
}

/** One line per still-missing permission for the closing summary. */
fun missingPermissionLabel(step: OnboardingStep): String = when (step) {
    OnboardingStep.USAGE_ACCESS -> "Screen time access — without it nothing is measured"
    OnboardingStep.OVERLAY_ACCESS -> "Display over other apps — without it nothing is blocked"
    OnboardingStep.CAMERA -> "Camera — without it a cat can't buy time back"
    OnboardingStep.NOTIFICATIONS -> "Notifications — without them there's no warning"
    else -> ""
}
