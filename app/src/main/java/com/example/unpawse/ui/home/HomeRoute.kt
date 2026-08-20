package com.example.unpawse.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Stateful wrapper around [HomeScreen]: owns the [HomeViewModel] and streams real usage/captures in.
 * This is what the NavHost renders; [HomeUiState.sample] survives for `@Preview` only.
 */
@Composable
fun HomeRoute(
    onEditLimits: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focus by viewModel.focus.collectAsStateWithLifecycle()

    // Both special permissions change only while the user is away in system Settings, so re-read them
    // on the way back. Starting the monitor is not this screen's job — `UnPawseApp` does it app-wide
    // on every resume; this only refreshes what Home reports about it.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose { }
    }

    HomeScreen(
        state = state,
        focus = focus,
        modifier = modifier,
        onEditLimits = onEditLimits,
        onStartFocus = viewModel::startFocus,
        onStopFocus = viewModel::stopFocus,
    )
}
