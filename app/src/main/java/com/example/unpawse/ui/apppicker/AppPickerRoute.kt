package com.example.unpawse.ui.apppicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Stateful wrapper around [AppPickerScreen]: owns the [AppPickerViewModel] and streams the real
 * installed-app / monitored-app join in. Mirrors `GalleryRoute`.
 */
@Composable
fun AppPickerRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: AppPickerViewModel = viewModel(factory = AppPickerViewModel.factory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AppPickerScreen(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onSearchChange = viewModel::onSearchChange,
        onToggleMonitored = viewModel::onToggleMonitored,
        onLimitChange = viewModel::onLimitChange,
    )
}
