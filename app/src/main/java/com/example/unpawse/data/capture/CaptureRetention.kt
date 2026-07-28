package com.example.unpawse.data.capture

import java.util.concurrent.TimeUnit

/**
 * Single source of truth for the photo-retention window. Shared so the purge worker
 * ([com.example.unpawse.service.CaptureRetentionWorker]) and the Gallery display cutoff can never
 * drift apart: what gets auto-deleted is exactly what the default view already hides.
 *
 * The window used to be a fixed 30 days; it is now a user setting
 * ([com.example.unpawse.data.settings.SettingsRepository.retentionDays]) and 30 is its default.
 */
object CaptureRetention {
    const val DEFAULT_WINDOW_DAYS = 30

    /** Sentinel window meaning "never auto-delete"; [cutoff] then admits every capture. */
    const val KEEP_FOREVER = 0

    /** The choices offered in Photo storage, newest-first in the picker. */
    val WINDOW_CHOICES = listOf(7, 30, 90, KEEP_FOREVER)

    /**
     * Epoch-millis boundary: captures with `capturedAt < cutoff(now, days)` are expired (favorites
     * aside). With [KEEP_FOREVER] the boundary is [Long.MIN_VALUE], so nothing is ever older than it
     * — the purge finds nothing and the Gallery hides nothing.
     */
    fun cutoff(nowMillis: Long, windowDays: Int): Long =
        if (windowDays <= KEEP_FOREVER) {
            Long.MIN_VALUE
        } else {
            nowMillis - TimeUnit.DAYS.toMillis(windowDays.toLong())
        }

    /** Picker label for a window, e.g. "30 days" / "Keep forever". */
    fun label(windowDays: Int): String =
        if (windowDays <= KEEP_FOREVER) "Keep forever" else "$windowDays days"
}
