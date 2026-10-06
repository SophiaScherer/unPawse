package com.example.unpawse.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class StartDestinationTest {

    @Test
    fun `a fresh install starts on the tour`() {
        assertEquals(Routes.ONBOARDING, startDestinationFor(onboardingComplete = false, deepLink = null))
    }

    @Test
    fun `a finished tour starts on Home`() {
        assertEquals(Routes.HOME, startDestinationFor(onboardingComplete = true, deepLink = null))
    }

    /** "Open Camera" during a block must reach the camera, not a welcome screen. */
    @Test
    fun `a deep link wins over an unfinished tour`() {
        assertEquals(Routes.HOME, startDestinationFor(onboardingComplete = false, deepLink = Routes.CAMERA))
    }
}
