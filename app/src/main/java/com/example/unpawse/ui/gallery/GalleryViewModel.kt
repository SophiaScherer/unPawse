package com.example.unpawse.ui.gallery

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.unpawse.appContainer
import com.example.unpawse.data.capture.CaptureRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Observes stored captures and shapes them into [GalleryUiState] for the (stateless) GalleryScreen. */
class GalleryViewModel(repository: CaptureRepository) : ViewModel() {

    val uiState: StateFlow<GalleryUiState> =
        repository.observeCaptures()
            .map { captures ->
                GalleryUiState(
                    searchPlaceholder = "Search captures...",
                    filters = GalleryUiState.defaultFilters,
                    sections = captures.toGallerySections(),
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
