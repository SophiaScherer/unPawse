package com.example.unpawse.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every header derives its avatar the same way now. Stats and Gallery used to fall back to a
 * hardcoded 'S' — the mockup's "Sophia" — which showed the wrong letter for every real user.
 */
class DisplayNameTest {

    @Test
    fun `a set name supplies its own initial`() {
        assertEquals('S', avatarInitialFor("Sophia"))
        assertEquals('I', avatarInitialFor("Imported"))
    }

    @Test
    fun `the initial is upper-cased regardless of how the name was typed`() {
        assertEquals('S', avatarInitialFor("sophia"))
    }

    @Test
    fun `an unset name falls back rather than crashing on an empty string`() {
        assertEquals(DEFAULT_DISPLAY_NAME, displayNameOf(""))
        assertEquals('F', avatarInitialFor(""))
        assertEquals(DEFAULT_AVATAR_INITIAL, avatarInitialFor(""))
    }

    /** Trimming happens on write, but a legacy stored value could still be whitespace-only. */
    @Test
    fun `a whitespace-only name counts as unset`() {
        assertEquals(DEFAULT_DISPLAY_NAME, displayNameOf("   "))
        assertEquals('F', avatarInitialFor("   "))
    }

    @Test
    fun `a name that is already set is returned unchanged`() {
        assertEquals("Sophia", displayNameOf("Sophia"))
    }
}
