package com.example.unpawse.ui.camera

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.unpawse.data.capture.CaptureDatabase
import com.example.unpawse.data.capture.CaptureRepository
import com.example.unpawse.data.capture.PhotoStorage
import com.example.unpawse.ml.CatDetector
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-shot outcomes of a capture, consumed by [CameraRoute] (state changes go through [uiState]). */
sealed interface CameraEvent {
    /** A cat was confirmed and saved to the gallery. */
    data object Saved : CameraEvent

    /** The shot wasn't a cat (or was below the threshold); nothing was stored. */
    data class NotACat(val confidence: Float) : CameraEvent

    /** Capture or classification failed. */
    data object Error : CameraEvent
}

/**
 * Owns the camera screen's state and the capture pipeline: take photo → classify → save-if-cat. The
 * ViewModel does not hold the CameraX controller (that stays lifecycle-bound in the composable);
 * instead [onShutter] receives a suspend capture lambda, so all state transitions live here.
 */
class CameraViewModel(
    private val repository: CaptureRepository,
    private val detector: CatDetector,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _events = Channel<CameraEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onToggleFlash() = _uiState.update { it.copy(flashOn = !it.flashOn) }

    fun onFlipCamera() = _uiState.update {
        it.copy(lensFacing = if (it.lensFacing == LensFacing.BACK) LensFacing.FRONT else LensFacing.BACK)
    }

    /**
     * Handles a shutter press. [capture] takes the photo (provided by the composable that owns the
     * controller). Debounced via [CameraUiState.isCapturing]. Only cat photos are persisted.
     */
    fun onShutter(capture: suspend () -> CapturedImage) {
        if (_uiState.value.isCapturing) return
        _uiState.update { it.copy(isCapturing = true, hintText = ANALYZING_HINT) }
        viewModelScope.launch {
            try {
                val captured = capture()
                val result = detector.analyze(captured.inputImage)
                if (result.isCat) {
                    repository.saveCapture(captured.jpegBytes, result.confidence)
                    _uiState.update { it.copy(hintText = SAVED_HINT) }
                    _events.send(CameraEvent.Saved)
                } else {
                    _uiState.update { it.copy(hintText = NOT_CAT_HINT) }
                    _events.send(CameraEvent.NotACat(result.confidence))
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Capture/analysis failed", t)
                _uiState.update { it.copy(hintText = ERROR_HINT) }
                _events.send(CameraEvent.Error)
            } finally {
                _uiState.update { it.copy(isCapturing = false) }
            }
        }
    }

    override fun onCleared() {
        detector.close()
    }

    companion object {
        private const val TAG = "CameraViewModel"

        private const val ANALYZING_HINT = "Checking for a cat..."
        private const val SAVED_HINT = "Purrfect! Saved to your gallery."
        private const val NOT_CAT_HINT = "Hmm, that's not a cat — try again."
        private const val ERROR_HINT = "Couldn't take that shot — try again."

        /** Manual-DI factory: builds the repository off the singleton DB; the VM owns its detector. */
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                val database = CaptureDatabase.getInstance(appContext)
                val repository = CaptureRepository(database.captureDao(), PhotoStorage(appContext))
                CameraViewModel(repository, CatDetector())
            }
        }
    }
}
