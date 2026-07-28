package com.example.unpawse.ui.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Stateful wrapper for [PhotoStorageScreen], mirroring `AppPickerRoute`. */
@Composable
fun PhotoStorageRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: PhotoStorageViewModel =
        viewModel(factory = PhotoStorageViewModel.factory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PhotoStorageScreen(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onRetentionChange = viewModel::setRetentionDays,
        onDeleteAll = viewModel::deleteAllPhotos,
    )
}
