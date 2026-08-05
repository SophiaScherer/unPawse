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
import com.example.unpawse.data.usage.RewardDecision
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

/**
 * What a saved cat actually did for the user. Every case but [Earned] means the photo is in the
 * gallery but bought no time, and each says something different about *why* — a refusal the user
 * can't explain reads as the app being broken.
 */
sealed interface RewardOutcome {

    /** A casual capture: no block was armed, or its redemption window had already closed. */
    data object NoActiveBlock : RewardOutcome

    /** Bonus time bought back by a verified cat while an app was blocked. */
    data class Earned(val appLabel: String, val minutes: Int) : RewardOutcome

    /** The app has earned everything it can today; nothing until tomorrow. */
    data class DailyCapReached(val appLabel: String, val capMinutes: Int) : RewardOutcome

    /** Too soon after the last grant. The block stays armed, so a retry later still counts. */
    data class CoolingDown(val appLabel: String, val retrySeconds: Long) : RewardOutcome
}

/** One-shot outcomes of a capture, consumed by [CameraRoute] (state changes go through [uiState]). */
sealed interface CameraEvent {
    /** A cat was confirmed and saved to the gallery; [outcome] says what it was worth. */
    data class Saved(val outcome: RewardOutcome) : CameraEvent

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
    /** Supplies the current reward grant; a lambda so the VM reads it fresh, like [CatDetector]'s gate. */
    private val earnedMinutesPerCat: () -> Int = { BONUS_MINUTES_PER_CAT },
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
                    val outcome = creditBlockedApp()
                    _uiState.update { it.copy(hintText = savedHint(outcome)) }
                    _events.send(CameraEvent.Saved(outcome))
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
     * Pays off the blocked app, if the user came here from a live block. Returns
     * [RewardOutcome.NoActiveBlock] for a casual capture — or for one taken after the block's
     * redemption window closed, which to the user reads the same as never having been blocked.
     *
     * Crediting raises the budget above what's been used, so the tracker stops reporting the app as
     * over-limit and won't re-block when the user goes back to it. There is no explicit unblock.
     */
    private suspend fun creditBlockedApp(): RewardOutcome {
        val packageName = blockSession.current()?.packageName ?: return RewardOutcome.NoActiveBlock
        // Read at credit time, so changing the Settings stepper applies to the very next capture.
        val decision = usageRepository.tryEarnMinutes(packageName, earnedMinutesPerCat())
        val label = usageRepository.appLabel(packageName) ?: packageName
        return when (decision) {
            is RewardDecision.Granted -> {
                blockSession.clear()
                RewardOutcome.Earned(appLabel = label, minutes = decision.minutes)
            }
            // Nothing left to redeem today; keeping the debt armed would only let a later photo
            // look like it mattered.
            is RewardDecision.Capped -> {
                blockSession.clear()
                RewardOutcome.DailyCapReached(appLabel = label, capMinutes = decision.dailyCapMinutes)
            }
            // Left armed on purpose: the wait is temporary, so a retry once it's up should still
            // pay out against this same block.
            is RewardDecision.CoolingDown ->
                RewardOutcome.CoolingDown(appLabel = label, retrySeconds = decision.retrySeconds)
        }
    }

    override fun onCleared() {
        detector.close()
    }

    companion object {
        private const val TAG = "CameraViewModel"

        private const val ANALYZING_HINT = "Checking for a cat..."
        private const val NOT_CAT_HINT = "Hmm, that's not a cat — try again."
        private const val ERROR_HINT = "Couldn't take that shot — try again."

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
                    earnedMinutesPerCat = { container.earnedMinutesPerCat.value },
                )
            }
        }
    }
}
