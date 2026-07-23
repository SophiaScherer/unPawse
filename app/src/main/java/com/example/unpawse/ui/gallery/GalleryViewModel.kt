package com.example.unpawse.ui.gallery

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.unpawse.appContainer
import com.example.unpawse.data.capture.CaptureRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Observes stored captures and shapes them into [GalleryUiState] for the (stateless) GalleryScreen,
 * applying the user's selected filter chip and search query. Filter/search are held as their own
 * flows and combined with the capture stream so a change re-derives the sections without a re-query.
 */
class GalleryViewModel(private val repository: CaptureRepository) : ViewModel() {

    private val selectedFilter = MutableStateFlow(GalleryFilter.ALL)
    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<GalleryUiState> =
        combine(
            repository.observeCaptures(),
            selectedFilter,
            searchQuery,
        ) { captures, filter, query ->
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val sections = captures
                .matchingFilter(filter, System.currentTimeMillis())
                .matchingSearch(query, today, zone)
                .toGallerySections(today, zone)
            GalleryUiState(
                searchQuery = query,
                searchPlaceholder = GalleryUiState.SEARCH_PLACEHOLDER,
                selectedFilter = filter,
                sections = sections,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = GalleryUiState.empty(),
        )

    fun onFilterSelected(filter: GalleryFilter) {
        selectedFilter.value = filter
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    /** Stars/unstars a capture. Favorites are exempt from the retention purge. */
    fun toggleFavorite(id: String, favorite: Boolean) {
        viewModelScope.launch { repository.setFavorite(id, favorite) }
    }

    /** Permanently deletes a capture (row + JPEG). The observed stream refreshes the grid. */
    fun delete(id: String) {
        viewModelScope.launch { repository.deleteCaptureById(id) }
    }

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
