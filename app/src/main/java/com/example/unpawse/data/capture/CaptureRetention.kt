package com.example.unpawse.data.capture

import java.util.concurrent.TimeUnit

/**
 * Single source of truth for the photo-retention window (rolling 30 days). Shared so the purge
 * worker ([com.example.unpawse.service.CaptureRetentionWorker]) and the Gallery display cutoff can
 * never drift apart: what gets auto-deleted is exactly what the default view already hides.
 */
object CaptureRetention {
    const val WINDOW_DAYS = 30L

    val windowMillis: Long = TimeUnit.DAYS.toMillis(WINDOW_DAYS)

    /** Epoch-millis boundary: captures with `capturedAt < cutoff(now)` are expired (favorites aside). */
    fun cutoff(nowMillis: Long): Long = nowMillis - windowMillis
}
