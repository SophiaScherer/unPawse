package com.example.unpawse.data.capture

/**
 * Domain model for a stored cat capture. Deliberately separate from [CaptureEntity] so the Room
 * schema can evolve without leaking `@Entity` annotations into the UI/ViewModel layers.
 *
 * @param filePath absolute path to the JPEG in app-internal storage (see [PhotoStorage]).
 * @param capturedAt epoch millis of when the shot was taken.
 * @param confidence ML Kit "Cat" label confidence, 0f..1f.
 * @param isBonus reserved for streak/bonus captures (no AI badge in the Gallery).
 */
data class Capture(
    val id: String,
    val filePath: String,
    val capturedAt: Long,
    val confidence: Float,
    val isBonus: Boolean = false,
)
