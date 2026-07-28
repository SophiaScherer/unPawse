package com.example.unpawse.ui.photos

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.unpawse.appContainer
import com.example.unpawse.data.capture.CaptureRepository
import com.example.unpawse.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the Photo storage screen; wires the capture stream, its measured size, and the setting. */
class PhotoStorageViewModel(
    private val captures: CaptureRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<PhotoStorageUiState> = combine(
        captures.observeCaptures(),
        settings.retentionDays,
        captures.observeStorageBytes(),
    ) { captureList, retentionDays, bytes ->
        PhotoStorageUiState(
            photoCount = captureList.size,
            favoriteCount = captureList.count { it.isFavorite },
            storageBytes = bytes,
            retentionDays = retentionDays,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = PhotoStorageUiState(),
    )

    fun setRetentionDays(days: Int) = viewModelScope.launch { settings.setRetentionDays(days) }

    /** Deletes every photo, favorites included. The caller is expected to have confirmed first. */
    fun deleteAllPhotos() = viewModelScope.launch { captures.deleteAllCaptures() }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = context.appContainer()
                PhotoStorageViewModel(
                    captures = container.captureRepository,
                    settings = container.settingsRepository,
                )
            }
        }
    }
}
