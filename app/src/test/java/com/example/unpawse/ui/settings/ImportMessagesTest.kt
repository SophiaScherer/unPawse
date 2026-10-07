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

    /** Every refusal must say nothing was changed, or they read as a wipe that lost the data. */
    @Test
    fun `refusals say the device was left alone`() {
        assertTrue(importMessage(ImportResult.Unreadable).contains("nothing was changed"))
        assertTrue(importMessage(ImportResult.Damaged).contains("nothing was changed"))
        assertTrue(importMessage(ImportResult.TooNew(99)).contains("nothing was changed"))
    }

    /** Only true because the wipe and the restore commit together; see `ImportRepositoryTest`. */
    @Test
    fun `a failed restore says it was rolled back`() {
        assertTrue(importMessage(ImportResult.Failed).contains("nothing was changed"))
    }

    /** By then the data has committed, so this one must not claim nothing changed. */
    @Test
    fun `settings that failed after the commit are called out`() {
        val message = importMessage(
            ImportResult.Restored(captures = 2, skippedCaptures = 0, settingsRestored = false),
        )
        assertTrue(message.contains("settings couldn't be"))
        assertTrue(!message.contains("nothing was changed"))
    }
}
