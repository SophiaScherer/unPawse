package com.example.unpawse.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `an absent override means follow the system`() {
        assertEquals(ThemeMode.SYSTEM, themeModeFrom(null))
    }

    @Test
    fun `an explicit override reads as the mode the user picked`() {
        assertEquals(ThemeMode.DARK, themeModeFrom(true))
        assertEquals(ThemeMode.LIGHT, themeModeFrom(false))
    }

    /**
     * The bug this phase fixes: the persisted override had a "follow system" state that no UI could
     * write, so choosing Light or Dark once was permanent. Pins that SYSTEM persists as a clear.
     */
    @Test
    fun `follow system persists as a cleared override`() {
        assertNull(overrideFor(ThemeMode.SYSTEM))
        assertEquals(false, overrideFor(ThemeMode.LIGHT))
        assertEquals(true, overrideFor(ThemeMode.DARK))
    }

    @Test
    fun `every mode round-trips through the persisted form`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, themeModeFrom(overrideFor(mode)))
        }
    }

    @Test
    fun `system follows the device either way`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemInDarkTheme = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemInDarkTheme = false))
    }

    @Test
    fun `an explicit mode ignores the device`() {
        assertTrue(ThemeMode.DARK.isDark(systemInDarkTheme = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemInDarkTheme = true))
    }
}
