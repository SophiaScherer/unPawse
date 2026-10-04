package com.example.unpawse.ui.block

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hero is the one element on the overlay that gives up room so the copy above the buttons fits. */
class BlockHeroSizeTest {

    @Test
    fun `a tall phone at default font keeps the full-size hero`() {
        // A 411x914dp phone once the system bars and the outer padding are taken off.
        assertEquals(MAX_HERO_SIZE, blockHeroSize(800.dp, fontScale = 1f))
    }

    @Test
    fun `a short screen shrinks the hero to its floor rather than to nothing`() {
        // A 360x640dp phone, likewise.
        assertEquals(MIN_HERO_SIZE, blockHeroSize(530.dp, fontScale = 1f))
        assertEquals(MIN_HERO_SIZE, blockHeroSize(0.dp, fontScale = 1f))
    }

    @Test
    fun `a larger font shrinks the hero on the same screen`() {
        val normal = blockHeroSize(720.dp, fontScale = 1f)
        val large = blockHeroSize(720.dp, fontScale = 1.5f)
        assertTrue("$large should be smaller than $normal", large < normal)
        assertEquals(MIN_HERO_SIZE, blockHeroSize(800.dp, fontScale = 2f))
    }

    @Test
    fun `the hero never grows past its ceiling`() {
        assertEquals(MAX_HERO_SIZE, blockHeroSize(2000.dp, fontScale = 2f))
    }
}
