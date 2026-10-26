package com.example.unpawse.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarVisibilityTest {

    @Test
    fun `the camera keeps the bar in portrait and drops it in landscape`() {
        assertTrue(showsBottomBar(Routes.CAMERA, landscape = false))
        assertFalse(showsBottomBar(Routes.CAMERA, landscape = true))
    }

    @Test
    fun `takeovers never show the bar`() {
        listOf(Routes.CAPTURE_VIEWER, Routes.ONBOARDING).forEach { route ->
            assertFalse(route, showsBottomBar(route, landscape = false))
            assertFalse(route, showsBottomBar(route, landscape = true))
        }
    }

    /** Only the viewfinder trades the bar for space; every other tab keeps it when rotated. */
    @Test
    fun `other tabs and sub-screens keep the bar in landscape`() {
        listOf(Routes.HOME, Routes.STATS, Routes.GALLERY, Routes.SETTINGS, Routes.APP_PICKER).forEach { route ->
            assertTrue(route, showsBottomBar(route, landscape = true))
        }
    }
}
