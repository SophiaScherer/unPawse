package com.example.unpawse.ui.gallery

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.unpawse.appContainer
import com.example.unpawse.data.capture.CaptureRepository
import com.example.unpawse.data.capture.CaptureRetention
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Observes stored captures and shapes them into [GalleryUiState] for the (stateless) GalleryScreen. */
class GalleryViewModel(repository: CaptureRepository) : ViewModel() {

    val uiState: StateFlow<GalleryUiState> =
        repository.observeCaptures()
            .map { captures ->
                // Default view: only the last month (favorites-regardless-of-age arrives in Phase 2).
                val cutoff = CaptureRetention.cutoff(System.currentTimeMillis())
                GalleryUiState(
                    searchPlaceholder = "Search captures...",
                    filters = GalleryUiState.defaultFilters,
                    sections = captures.retainedWithin(cutoff).toGallerySections(),
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = GalleryUiState.empty(),
            )

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Manual-DI factory mirroring CameraViewModel; both share the container's repository. */
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GalleryViewModel(context.appContainer().captureRepository)
            }
        }
    }
}
