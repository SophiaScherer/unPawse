package com.example.unpawse.ui.schedules

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Stateful wrapper around [SchedulesScreen]: owns the [SchedulesViewModel] and streams the stored
 * windows in. Mirrors `AppPickerRoute`.
 */
@Composable
fun SchedulesRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SchedulesViewModel = viewModel(factory = SchedulesViewModel.factory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SchedulesScreen(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onToggleEnabled = viewModel::setEnabled,
    )
}
