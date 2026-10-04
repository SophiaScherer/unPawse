package com.example.unpawse.ui.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Stateful wrapper around [CaptureViewerScreen].
 *
 * It deliberately does **not** own a ViewModel of its own: [galleryOwner] is the Gallery's own
 * back-stack entry, so `viewModel()` here resolves to the *same* [GalleryViewModel] the grid is
 * using. That is what makes the viewer page over what the grid was showing rather than over the
 * whole library — the selected chip and the search query live in that instance, and a change to
 * either (or a delete, or the retention purge) re-emits through the same flow while the viewer is
 * open. Passing the filter and query as route arguments was the alternative and would have frozen
 * copies of them behind the viewer.
 *
 * [galleryOwner] is nullable because `getBackStackEntry` throws once Gallery leaves the stack; on
 * that path the viewer falls back to its own instance of the ViewModel, which shows the default
 * unfiltered list rather than crashing.
 */
@Composable
fun CaptureViewerRoute(
    captureId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    galleryOwner: ViewModelStoreOwner? = null,
) {
    val context = LocalContext.current
    val owner = galleryOwner ?: checkNotNull(LocalViewModelStoreOwner.current)
    val viewModel: GalleryViewModel =
        viewModel(viewModelStoreOwner = owner, factory = GalleryViewModel.factory(context))
    val galleryState by viewModel.uiState.collectAsStateWithLifecycle()

    val state = remember(galleryState.sections, captureId) {
        CaptureViewerUiState(
            captures = galleryState.sections.toViewerCaptures(),
            openAtId = captureId,
        )
    }

    CaptureViewerScreen(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onToggleFavorite = { item -> viewModel.toggleFavorite(item.id, !item.isFavorite) },
        onShare = { item -> item.imagePath?.let { shareCapture(context, it) } },
        onDelete = { item -> viewModel.delete(item.id) },
    )
}
