package com.example.unpawse.ui.photos

import com.example.unpawse.data.capture.CaptureRetention
import com.example.unpawse.ui.format.formatBytes

/**
 * Immutable UI state for the Photo storage screen. [storageBytes] is measured off disk rather than
 * derived from the row count, so it stays honest if a file and its row ever disagree.
 */
data class PhotoStorageUiState(
    val photoCount: Int = 0,
    val favoriteCount: Int = 0,
    val storageBytes: Long = 0L,
    val retentionDays: Int = CaptureRetention.DEFAULT_WINDOW_DAYS,
) {
    val storageLabel: String get() = formatBytes(storageBytes)

    val retentionLabel: String get() = CaptureRetention.label(retentionDays)

    val hasPhotos: Boolean get() = photoCount > 0

    companion object {
        /** Preview-only fixture. */
        fun sample() = PhotoStorageUiState(
            photoCount = 142,
            favoriteCount = 12,
            storageBytes = 40_265_318L,
            retentionDays = 30,
        )
    }
}

/** The Settings row subtitle, e.g. "142 photos · 38.4 MB". */
fun photoStorageSummary(photoCount: Int, storageBytes: Long): String {
    if (photoCount == 0) return "No photos yet"
    val photos = if (photoCount == 1) "1 photo" else "$photoCount photos"
    return "$photos · ${formatBytes(storageBytes)}"
}
