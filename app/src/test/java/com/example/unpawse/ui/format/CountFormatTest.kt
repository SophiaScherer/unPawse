package com.example.unpawse.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class CountFormatTest {

    @Test
    fun `one takes the singular`() {
        assertEquals("1 photo", countLabel(1, "photo"))
    }

    @Test
    fun `anything else takes the plural`() {
        assertEquals("5 photos", countLabel(5, "photo"))
    }

    /** Zero is plural in English, and several callers render it — "0 apps", not "0 app". */
    @Test
    fun `zero takes the plural`() {
        assertEquals("0 apps", countLabel(0, "app"))
    }

    @Test
    fun `the plural can be given outright`() {
        assertEquals("2 entries", countLabel(2, "entry", "entries"))
    }

    /** Casing belongs to the caller: Stats titles its counts, the Settings rows don't. */
    @Test
    fun `case is the caller's`() {
        assertEquals("1 Day", countLabel(1, "Day"))
        assertEquals("3 Days", countLabel(3, "Day"))
    }

    /**
     * Home's stat pill draws the figure above its noun, so it needs the noun alone. It had been
     * hardcoded "Cats" and read "1 Cats" for a library of one — the same defect `countLabel` was
     * introduced to stamp out, surviving in the one layout that couldn't use it.
     */
    @Test
    fun `the noun alone agrees with its count`() {
        assertEquals("Cat", pluralOf(1, "Cat"))
        assertEquals("Cats", pluralOf(2, "Cat"))
        assertEquals("Cats", pluralOf(0, "Cat"))
        assertEquals("entries", pluralOf(3, "entry", "entries"))
    }

    /** One definition of the rule, so the split form and the inline form can't disagree. */
    @Test
    fun `countLabel is the noun rule plus the number`() {
        listOf(0, 1, 2, 17).forEach { n ->
            assertEquals("$n ${pluralOf(n, "cat")}", countLabel(n, "cat"))
        }
    }
}
