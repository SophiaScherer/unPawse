package com.example.unpawse.ml

/**
 * Outcome of running a captured frame through [CatDetector].
 *
 * @param isCat whether the "Cat" confidence cleared the detector's threshold.
 * @param confidence the raw ML Kit "Cat" label confidence (0f..1f), regardless of [isCat] — stored
 * with the capture and shown as the Gallery "% AI" badge.
 */
data class DetectionResult(
    val isCat: Boolean,
    val confidence: Float,
)
