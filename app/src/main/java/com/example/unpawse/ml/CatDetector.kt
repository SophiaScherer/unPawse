package com.example.unpawse.ml

import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device cat identification wrapping ML Kit's image labeler. The whole ML dependency lives behind
 * this one class — swapping in a custom TensorFlow Lite model later would only touch this file.
 *
 * The labeler runs with a low internal confidence floor so the "Cat" label surfaces even when it's
 * uncertain; the gating decision (is it a cat?) is applied separately against [minConfidence] so the
 * app's threshold is independent of the model's recall floor.
 *
 * @param minConfidence the confidence a "Cat" label must reach to count as a cat. Defaults to
 * [DEFAULT_MIN_CONFIDENCE]; a future settings-backed value can be injected here (see plan piece 8).
 */
class CatDetector(
    private val minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
    private val labeler: ImageLabeler = defaultLabeler(),
) {
    /** Labels [image] and returns whether it's a cat plus the raw confidence. */
    suspend fun analyze(image: InputImage): DetectionResult {
        val catConfidence = labeler.process(image).await()
            .firstOrNull { it.text.equals(CAT_LABEL, ignoreCase = true) }
            ?.confidence ?: 0f
        return classify(catConfidence, minConfidence)
    }

    /** Releases the underlying detector. Call from the owner's onCleared/dispose. */
    fun close() = labeler.close()

    companion object {
        /** Fallback gate until the Settings sensitivity/confidence data layer exists. */
        const val DEFAULT_MIN_CONFIDENCE = 0.7f

        private const val CAT_LABEL = "Cat"

        // Kept low so a "Cat" label is returned even below the app's gate, letting us read its
        // confidence and decide ourselves rather than having ML Kit drop it.
        private const val LABELER_MIN_CONFIDENCE = 0.1f

        private fun defaultLabeler(): ImageLabeler = ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(LABELER_MIN_CONFIDENCE)
                .build(),
        )
    }
}

/**
 * Pure threshold decision, extracted from ML Kit so it's unit-testable without a device: a frame is
 * a cat iff the "Cat" confidence reaches [minConfidence].
 */
internal fun classify(catConfidence: Float, minConfidence: Float): DetectionResult =
    DetectionResult(isCat = catConfidence >= minConfidence, confidence = catConfidence)

/** Bridges a Play-services [Task] to a coroutine without pulling in kotlinx-coroutines-play-services. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
