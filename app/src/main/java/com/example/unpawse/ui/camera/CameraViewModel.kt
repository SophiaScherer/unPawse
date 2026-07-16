package com.example.unpawse.ui.camera

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.unpawse.appContainer
import com.example.unpawse.data.capture.CaptureRepository
import com.example.unpawse.data.usage.UsageRepository
import com.example.unpawse.ml.CatDetector
import com.example.unpawse.service.BONUS_MINUTES_PER_CAT
import com.example.unpawse.service.BlockSession
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Bonus time bought back by a verified cat while an app was blocked. */
data class EarnedTime(val appLabel: String, val minutes: Int)

/** One-shot outcomes of a capture, consumed by [CameraRoute] (state changes go through [uiState]). */
sealed interface CameraEvent {
    /**
     * A cat was confirmed and saved to the gallery. [earned] is non-null only when the capture also
     * paid off a blocked app — i.e. the user got here from the block overlay.
     */
    data class Saved(val earned: EarnedTime?) : CameraEvent

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
    private val usageRepository: UsageRepository,
    private val blockSession: BlockSession,
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
                    // isBonus stays false: that flag marks a *streak* bonus in the Gallery (no AI
                    // badge, "Daily streak bonus!"). An unblock capture is an ordinary verified cat.
                    repository.saveCapture(captured.jpegBytes, result.confidence)
                    val earned = creditBlockedApp()
                    _uiState.update { it.copy(hintText = savedHint(earned)) }
                    _events.send(CameraEvent.Saved(earned))
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

    /**
     * Pays off the blocked app, if the user came here from a block: credits bonus minutes and
     * settles the session so a second photo can't be spent on the same debt. Returns what was
     * earned, or null when this was just a casual capture.
     *
     * Crediting raises the budget above what's been used, so the tracker stops reporting the app as
     * over-limit and won't re-block when the user goes back to it.
     */
    private suspend fun creditBlockedApp(): EarnedTime? {
        val packageName = blockSession.blockedPackage.value ?: return null
        usageRepository.addEarnedMinutes(packageName, BONUS_MINUTES_PER_CAT)
        val label = usageRepository.appLabel(packageName) ?: packageName
        blockSession.clear()
        return EarnedTime(appLabel = label, minutes = BONUS_MINUTES_PER_CAT)
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

        /** Tells the user what their cat just bought them, when it bought anything. */
        private fun savedHint(earned: EarnedTime?): String =
            if (earned == null) {
                SAVED_HINT
            } else {
                "Purrfect! +${earned.minutes} min of ${earned.appLabel}."
            }

        /**
         * Manual-DI factory: pulls shared dependencies from the [AppContainer]. The VM owns its
         * detector, gated by the app-wide (settings-backed) min-confidence flow.
         */
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = context.appContainer()
                val detector = CatDetector(minConfidence = { container.catDetectorMinConfidence.value })
                CameraViewModel(
                    repository = container.captureRepository,
                    detector = detector,
                    usageRepository = container.usageRepository,
                    blockSession = container.blockSession,
                )
            }
        }
    }
}
