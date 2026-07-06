package com.example.unpawse.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Result of a single shutter press: the JPEG to persist and the ML Kit [InputImage] to classify.
 * Not a `data class` — it wraps a byte array, so structural equality would be misleading.
 */
class CapturedImage(
    val jpegBytes: ByteArray,
    val inputImage: InputImage,
)

/**
 * Takes a photo and returns it upright and ready to use. Rotation is baked into the pixels once, so
 * the same bytes are correct both for storage (Coil) and for ML Kit — no reliance on EXIF handling.
 * The heavy decode/rotate/encode runs on [Dispatchers.IO]; the ImageProxy is read briefly and closed.
 */
suspend fun LifecycleCameraController.captureImage(context: Context): CapturedImage =
    withContext(Dispatchers.IO) {
        val (raw, rotationDegrees) = awaitRawCapture(context)

        var bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)
        if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        val jpeg = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        // Rotation is already applied, so hand ML Kit an upright bitmap (0°).
        CapturedImage(jpegBytes = jpeg, inputImage = InputImage.fromBitmap(bitmap, 0))
    }

/** Fires the shutter and hands back the raw JPEG bytes + rotation, releasing the proxy immediately. */
private suspend fun LifecycleCameraController.awaitRawCapture(context: Context): Pair<ByteArray, Int> =
    suspendCancellableCoroutine { cont ->
        takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        cont.resume(image.planes[0].buffer.toByteArray() to image.imageInfo.rotationDegrees)
                    } catch (t: Throwable) {
                        cont.resumeWithException(t)
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(exception)
                }
            },
        )
    }

private fun ByteBuffer.toByteArray(): ByteArray {
    rewind()
    return ByteArray(remaining()).also { get(it) }
}

private const val JPEG_QUALITY = 90
