package com.example.unpawse.ui.navigation

/**
 * Where the graph starts. A launch that deep-links somewhere (the block overlay's "Open Camera")
 * wins over an unfinished tour: the user is mid-block with a five-minute reward window running, and
 * the tour simply waits for the next plain launch. That case is reachable — Delete all data clears
 * the tour flag but keeps the special permissions, and backing out of the tour lands on Home.
 */
fun startDestinationFor(onboardingComplete: Boolean, deepLink: String?): String =
    if (onboardingComplete || deepLink != null) Routes.HOME else Routes.ONBOARDING
