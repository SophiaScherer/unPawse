package com.example.unpawse.ui.photos

import com.example.unpawse.data.capture.CaptureRetention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoStorageUiStateTest {

    @Test
    fun `an empty library reads as a prompt rather than zero bytes`() {
        assertEquals("No photos yet", photoStorageSummary(photoCount = 0, storageBytes = 0))
    }

    @Test
    fun `a single photo is counted in the singular`() {
        assertEquals("1 photo · 512 B", photoStorageSummary(photoCount = 1, storageBytes = 512))
    }

    @Test
    fun `the summary pairs the count with the size on disk`() {
        val summary = photoStorageSummary(photoCount = 142, storageBytes = 40_265_318L)

        assertEquals("142 photos · 38 MB", summary)
    }

    @Test
    fun `the retention label follows the chosen window`() {
        assertEquals("30 days", PhotoStorageUiState(retentionDays = 30).retentionLabel)
        assertEquals(
            "Keep forever",
            PhotoStorageUiState(retentionDays = CaptureRetention.KEEP_FOREVER).retentionLabel,
        )
    }

    /** "Delete all photos" must not be tappable with nothing to delete. */
    @Test
    fun `hasPhotos gates the destructive row`() {
        assertFalse(PhotoStorageUiState(photoCount = 0).hasPhotos)
        assertTrue(PhotoStorageUiState(photoCount = 1).hasPhotos)
    }
}
