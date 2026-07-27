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

    @Test
    fun `hasPhotos tracks the count`() {
        assertFalse(PhotoStorageUiState(photoCount = 0).hasPhotos)
        assertTrue(PhotoStorageUiState(photoCount = 1).hasPhotos)
    }

    /** "Delete all photos" must not be tappable with nothing at all to delete. */
    @Test
    fun `the destructive row is dead only when there is truly nothing to remove`() {
        assertFalse(PhotoStorageUiState(photoCount = 0, storageBytes = 0).canDeleteAll)
        assertTrue(PhotoStorageUiState(photoCount = 1, storageBytes = 1_024).canDeleteAll)
    }

    /**
     * Files can outlive their rows. Gating purely on the count would leave a user staring at
     * "0 photos · 93 KB" with no way to reclaim it.
     */
    @Test
    fun `leftover files with no rows can still be cleared`() {
        assertTrue(PhotoStorageUiState(photoCount = 0, storageBytes = 95_130).canDeleteAll)
    }
}
