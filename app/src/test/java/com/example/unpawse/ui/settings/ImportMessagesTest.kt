package com.example.unpawse.ui.settings

import com.example.unpawse.data.export.ImportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportMessagesTest {

    @Test
    fun `a full restore reports the photo count`() {
        assertEquals(
            "Data restored with 3 photos",
            importMessage(ImportResult.Restored(captures = 3, skippedCaptures = 0)),
        )
        assertEquals(
            "Data restored with 1 photo",
            importMessage(ImportResult.Restored(captures = 1, skippedCaptures = 0)),
        )
    }

    @Test
    fun `an export with no captures at all just says restored`() {
        assertEquals(
            "Data restored",
            importMessage(ImportResult.Restored(captures = 0, skippedCaptures = 0)),
        )
    }

    /** A legacy export carries no photos, and saying "restored" alone would overstate it. */
    @Test
    fun `skipped photos are reported rather than passed over`() {
        assertEquals(
            "Data restored — 2 photos couldn't be recovered",
            importMessage(ImportResult.Restored(captures = 0, skippedCaptures = 2)),
        )
        assertEquals(
            "Data restored — 1 photo couldn't be recovered",
            importMessage(ImportResult.Restored(captures = 4, skippedCaptures = 1)),
        )
    }

    /** Both refusals must say nothing was changed, or they read as a wipe that lost the data. */
    @Test
    fun `refusals say the device was left alone`() {
        assertTrue(importMessage(ImportResult.Unreadable).contains("nothing was changed"))
        assertTrue(importMessage(ImportResult.TooNew(99)).contains("nothing was changed"))
    }

    @Test
    fun `a part-way failure does not claim the device was left alone`() {
        assertEquals("Couldn't finish the import", importMessage(ImportResult.Failed))
    }
}
