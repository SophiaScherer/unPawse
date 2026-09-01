package com.example.unpawse.data.usage

import org.junit.Assert.assertEquals
import org.junit.Test

/** The stored-value parser, which every read of the setting goes through. */
class UsageScopeTest {

    @Test
    fun `a stored name reads back as itself`() {
        assertEquals(UsageScope.TRACKED, usageScopeNamed("TRACKED"))
        assertEquals(UsageScope.ALL, usageScopeNamed("ALL"))
    }

    @Test
    fun `an unset preference reads as tracked`() {
        // The first launch after this feature ships: no key yet, and tracked is what every earlier
        // build showed.
        assertEquals(UsageScope.TRACKED, usageScopeNamed(null))
    }

    @Test
    fun `an unrecognised name reads as tracked rather than throwing`() {
        // A downgrade from a build with more scopes, or a hand-edited value.
        assertEquals(UsageScope.TRACKED, usageScopeNamed("WORK_APPS"))
        assertEquals(UsageScope.TRACKED, usageScopeNamed(""))
    }

    @Test
    fun `every scope has a chip label`() {
        assertEquals(UsageScope.entries.size, UsageScope.entries.map { it.label }.distinct().size)
        assertEquals(emptyList<UsageScope>(), UsageScope.entries.filter { it.label.isBlank() })
    }
}
