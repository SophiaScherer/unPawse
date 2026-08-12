package com.example.unpawse.data.usage

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppCategoryTest {

    // --- Platform declarations we do map ---------------------------------------------------------

    @Test
    fun `social and productivity map straight across`() {
        assertEquals(AppCategory.SOCIAL, categoryFromPlatform(ApplicationInfo.CATEGORY_SOCIAL))
        assertEquals(AppCategory.PRODUCTIVITY, categoryFromPlatform(ApplicationInfo.CATEGORY_PRODUCTIVITY))
    }

    @Test
    fun `the four media categories all read as entertainment`() {
        assertEquals(AppCategory.ENTERTAINMENT, categoryFromPlatform(ApplicationInfo.CATEGORY_GAME))
        assertEquals(AppCategory.ENTERTAINMENT, categoryFromPlatform(ApplicationInfo.CATEGORY_AUDIO))
        assertEquals(AppCategory.ENTERTAINMENT, categoryFromPlatform(ApplicationInfo.CATEGORY_VIDEO))
        assertEquals(AppCategory.ENTERTAINMENT, categoryFromPlatform(ApplicationInfo.CATEGORY_IMAGE))
    }

    // --- Platform declarations we deliberately don't map -----------------------------------------

    @Test
    fun `an undeclared category is not guessed at`() {
        assertNull(categoryFromPlatform(ApplicationInfo.CATEGORY_UNDEFINED))
    }

    /**
     * News and Maps are left unmapped on purpose. Neither lands unambiguously in one of our three
     * buckets, and forcing one would be the app asserting something it doesn't know.
     */
    @Test
    fun `news and maps are left for the user to place`() {
        assertNull(categoryFromPlatform(ApplicationInfo.CATEGORY_NEWS))
        assertNull(categoryFromPlatform(ApplicationInfo.CATEGORY_MAPS))
    }

    @Test
    fun `a category from a newer platform than we know reads as unclassified`() {
        // e.g. CATEGORY_ACCESSIBILITY (API 33), which we deliberately don't reference.
        assertNull(categoryFromPlatform(8))
        assertNull(categoryFromPlatform(999))
    }

    // --- Reading the stored column ---------------------------------------------------------------

    @Test
    fun `every category round-trips through its stored name`() {
        AppCategory.entries.forEach { category ->
            assertEquals(category, appCategoryFrom(category.name))
        }
    }

    @Test
    fun `an unclassified or unrecognisable column reads as Other rather than throwing`() {
        assertEquals(AppCategory.OTHER, appCategoryFrom(null))
        assertEquals(AppCategory.OTHER, appCategoryFrom("BOGUS"))
        assertEquals(AppCategory.OTHER, appCategoryFrom(""))
        // Case matters — the column only ever holds an exact `name`.
        assertEquals(AppCategory.OTHER, appCategoryFrom("social"))
    }
}
